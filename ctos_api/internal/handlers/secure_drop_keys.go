package handlers

import (
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha1"
	"crypto/sha256"
	"crypto/x509"
	"encoding/base64"
	"encoding/hex"
	"encoding/pem"
	"errors"
	"strings"
)

// Checking the two PEMs of a Secure Drop key pair, and the one bit of RSA this API itself ever
// does: encrypting a proof-of-possession nonce with someone's public key. It never decrypts
// anything — that would need the private key, which it never has. See docs/secure-drop.md for
// the whole protocol; this mirrors the website's app/Support/SecureDropKey.php exactly, since a
// key pair set up from one has to work from the other.

const encryptedPrivateKeyHeader = "-----BEGIN CTOS ENCRYPTED PRIVATE KEY-----"

const encryptedPrivateKeyFooter = "-----END CTOS ENCRYPTED PRIVATE KEY-----"

// minEnvelopeBytes: version(1) + salt(16) + iterations(4) + iv(12) + a 16-byte GCM tag on an empty plaintext.
const minEnvelopeBytes = 1 + 16 + 4 + 12 + 16

// checkSecureDropPublicKey is "" when pem is a well-formed RSA public key of at least 2048 bits.
func checkSecureDropPublicKey(publicKeyPEM string) string {
	if !strings.Contains(publicKeyPEM, "-----BEGIN PUBLIC KEY-----") {
		return `That does not look like a public key (expected a "-----BEGIN PUBLIC KEY-----" PEM).`
	}

	block, _ := pem.Decode([]byte(publicKeyPEM))
	if block == nil {
		return "That public key could not be read. Check it was copied in full."
	}
	key, err := x509.ParsePKIXPublicKey(block.Bytes)
	if err != nil {
		return "That public key could not be read. Check it was copied in full."
	}
	rsaKey, ok := key.(*rsa.PublicKey)
	if !ok {
		return "Secure Drop keys must be RSA keys."
	}
	if rsaKey.Size()*8 < 2048 {
		return "Secure Drop keys must be at least 2048 bits."
	}
	return ""
}

// checkSecureDropEncryptedPrivateKey is "" when pem is shaped like one of our encrypted private
// key envelopes — framed right, and long enough to actually hold an encrypted key. This is a
// structural check only: there is no way to confirm what is inside without the passphrase, which
// this server never has.
func checkSecureDropEncryptedPrivateKey(privateKeyPEM string) string {
	trimmed := strings.TrimSpace(privateKeyPEM)

	if !strings.HasPrefix(trimmed, encryptedPrivateKeyHeader) || !strings.HasSuffix(trimmed, encryptedPrivateKeyFooter) {
		return `That does not look like an encrypted private key (expected a "-----BEGIN CTOS ENCRYPTED PRIVATE KEY-----" PEM). ` +
			`Make sure it was encrypted with a passphrase on your own device before it was sent here.`
	}

	body := strings.TrimSpace(trimmed[len(encryptedPrivateKeyHeader) : len(trimmed)-len(encryptedPrivateKeyFooter)])
	replacer := strings.NewReplacer("\r", "", "\n", "")
	bytes, err := base64.StdEncoding.DecodeString(replacer.Replace(body))
	if err != nil {
		return "That encrypted private key is not valid base64."
	}
	if len(bytes) < minEnvelopeBytes {
		return "That encrypted private key is too short to be real."
	}
	if bytes[0] != 1 {
		return "That encrypted private key uses a format this server does not recognise."
	}

	// A real private key must never reach here unencrypted — including as the "ciphertext" of an
	// otherwise well-shaped envelope, in case the passphrase step was skipped by mistake.
	ciphertext := bytes[1+16+4+12:]
	if _, err := x509.ParsePKCS8PrivateKey(ciphertext); err == nil {
		return "That private key is not encrypted. Encrypt it with a passphrase before uploading it."
	}

	return ""
}

// secureDropFingerprint: sha256 of the public key's DER bytes, hex — an SSH-style fingerprint.
func secureDropFingerprint(publicKeyPEM string) string {
	block, _ := pem.Decode([]byte(publicKeyPEM))
	if block == nil {
		return ""
	}
	sum := sha256.Sum256(block.Bytes)
	return hex.EncodeToString(sum[:])
}

// encryptSecureDropNonce encrypts nonce with the given public key: RSA-OAEP with SHA-1 as OAEP's
// own padding hash, matching the website's PHP openssl_public_encrypt(), which has no way to ask
// for a different one (see docs/secure-drop.md).
func encryptSecureDropNonce(publicKeyPEM string, nonce []byte) (string, error) {
	block, _ := pem.Decode([]byte(publicKeyPEM))
	if block == nil {
		return "", errors.New("not a valid public key")
	}
	key, err := x509.ParsePKIXPublicKey(block.Bytes)
	if err != nil {
		return "", err
	}
	rsaKey, ok := key.(*rsa.PublicKey)
	if !ok {
		return "", errors.New("not an RSA public key")
	}

	encrypted, err := rsa.EncryptOAEP(sha1.New(), rand.Reader, rsaKey, nonce, nil)
	if err != nil {
		return "", err
	}
	return base64.StdEncoding.EncodeToString(encrypted), nil
}
