package handlers

import (
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha1"
	"crypto/x509"
	"encoding/base64"
	"encoding/pem"
	"net/http"
	"testing"
)

// makeSecureDropKeyPair is a real RSA-2048 key pair, the same shape a device's own crypto would
// hand the API: the public key as a PEM, and the *rsa.PrivateKey for the test itself to decrypt a
// challenge with (standing in for what the person's own device does).
func makeSecureDropKeyPair(t *testing.T) (publicKeyPEM string, private *rsa.PrivateKey) {
	t.Helper()
	key, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		t.Fatal(err)
	}
	der, err := x509.MarshalPKIXPublicKey(&key.PublicKey)
	if err != nil {
		t.Fatal(err)
	}
	publicPEM := pem.EncodeToMemory(&pem.Block{Type: "PUBLIC KEY", Bytes: der})
	return string(publicPEM), key
}

// decryptSecureDropChallenge is what the person's device does after POSTing /encryption-keys/challenge.
func decryptSecureDropChallenge(t *testing.T, private *rsa.PrivateKey, ciphertextBase64 string) string {
	t.Helper()
	ciphertext, err := base64.StdEncoding.DecodeString(ciphertextBase64)
	if err != nil {
		t.Fatal(err)
	}
	nonce, err := rsa.DecryptOAEP(sha1.New(), rand.Reader, private, ciphertext, nil)
	if err != nil {
		t.Fatal(err)
	}
	return base64.StdEncoding.EncodeToString(nonce)
}

// makeSecureDropEnvelope is a structurally valid "-----BEGIN CTOS ENCRYPTED PRIVATE KEY-----"
// envelope: real random bytes in the right shape, but not an actual encrypted key — good enough
// for anything that only checks the envelope is well-formed and refuses to decrypt.
func makeSecureDropEnvelope(t *testing.T) string {
	t.Helper()
	bytes := make([]byte, 1+16+4+12+48)
	if _, err := rand.Read(bytes); err != nil {
		t.Fatal(err)
	}
	bytes[0] = 1
	encoded := base64.StdEncoding.EncodeToString(bytes)

	var wrapped string
	for i := 0; i < len(encoded); i += 64 {
		end := i + 64
		if end > len(encoded) {
			end = len(encoded)
		}
		wrapped += encoded[i:end] + "\n"
	}
	return "-----BEGIN CTOS ENCRYPTED PRIVATE KEY-----\n" + wrapped + "-----END CTOS ENCRYPTED PRIVATE KEY-----"
}

func TestSettingUpAKeyPairProvesPossessionFirst(t *testing.T) {
	api := newTestAPI(t)
	_, email := api.user("keyowner", true)
	token := api.token(email)

	publicPEM, private := makeSecureDropKeyPair(t)

	status, body := api.do("POST", "/api/v1/encryption-keys/challenge", token, map[string]any{"public_key": publicPEM})
	if status != http.StatusOK {
		t.Fatalf("challenge gave %d: %v", status, body)
	}
	ciphertext := body["data"].(map[string]any)["ciphertext"].(string)
	proof := decryptSecureDropChallenge(t, private, ciphertext)
	envelope := makeSecureDropEnvelope(t)

	status, body = api.do("POST", "/api/v1/encryption-keys", token, map[string]any{"proof": proof, "encrypted_private_key": envelope})
	if status != http.StatusOK {
		t.Fatalf("store gave %d: %v", status, body)
	}

	status, body = api.do("GET", "/api/v1/encryption-keys", token, nil)
	if status != http.StatusOK {
		t.Fatalf("show gave %d: %v", status, body)
	}
	if body["data"].(map[string]any)["fingerprint"] == "" {
		t.Fatal("expected a fingerprint")
	}
}

func TestARandomProofIsRefused(t *testing.T) {
	api := newTestAPI(t)
	_, email := api.user("wrongproof", true)
	token := api.token(email)
	publicPEM, _ := makeSecureDropKeyPair(t)

	api.do("POST", "/api/v1/encryption-keys/challenge", token, map[string]any{"public_key": publicPEM})

	status, _ := api.do("POST", "/api/v1/encryption-keys", token, map[string]any{
		"proof": base64.StdEncoding.EncodeToString([]byte("not the nonce")), "encrypted_private_key": makeSecureDropEnvelope(t),
	})
	if status != http.StatusUnprocessableEntity {
		t.Fatalf("expected the wrong proof to be refused, got %d", status)
	}
}

func TestAPlainPrivateKeyIsRefusedEvenWithACorrectProof(t *testing.T) {
	api := newTestAPI(t)
	_, email := api.user("plainkey", true)
	token := api.token(email)
	publicPEM, private := makeSecureDropKeyPair(t)

	status, body := api.do("POST", "/api/v1/encryption-keys/challenge", token, map[string]any{"public_key": publicPEM})
	if status != http.StatusOK {
		t.Fatalf("challenge gave %d", status)
	}
	proof := decryptSecureDropChallenge(t, private, body["data"].(map[string]any)["ciphertext"].(string))

	der, _ := x509.MarshalPKCS8PrivateKey(private)
	plainPEM := pem.EncodeToMemory(&pem.Block{Type: "PRIVATE KEY", Bytes: der})

	status, _ = api.do("POST", "/api/v1/encryption-keys", token, map[string]any{"proof": proof, "encrypted_private_key": string(plainPEM)})
	if status != http.StatusUnprocessableEntity {
		t.Fatalf("expected the plain private key to be refused, got %d", status)
	}
}

func TestDownloadingAndRemovingAKey(t *testing.T) {
	api := newTestAPI(t)
	_, email := api.user("dl", true)
	token := api.token(email)
	publicPEM, private := makeSecureDropKeyPair(t)
	envelope := makeSecureDropEnvelope(t)

	_, body := api.do("POST", "/api/v1/encryption-keys/challenge", token, map[string]any{"public_key": publicPEM})
	proof := decryptSecureDropChallenge(t, private, body["data"].(map[string]any)["ciphertext"].(string))
	api.do("POST", "/api/v1/encryption-keys", token, map[string]any{"proof": proof, "encrypted_private_key": envelope})

	status, downloaded := api.doRaw("GET", "/api/v1/encryption-keys/download", token)
	if status != http.StatusOK || downloaded != envelope {
		t.Fatalf("expected the same envelope back, got %d %q", status, downloaded)
	}

	status, _ = api.do("DELETE", "/api/v1/encryption-keys", token, nil)
	if status != http.StatusOK {
		t.Fatalf("delete gave %d", status)
	}
	status, _ = api.do("GET", "/api/v1/encryption-keys", token, nil)
	if status != http.StatusNotFound {
		t.Fatalf("expected the key to be gone, got %d", status)
	}
}

func TestEncryptionKeysRequireLogin(t *testing.T) {
	api := newTestAPI(t)

	status, _ := api.do("POST", "/api/v1/encryption-keys/challenge", "", map[string]any{"public_key": "x"})
	if status != http.StatusUnauthorized {
		t.Fatalf("expected 401, got %d", status)
	}
}
