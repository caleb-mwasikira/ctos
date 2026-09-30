package handlers

import (
	"crypto/rand"
	"encoding/base64"
	"encoding/json"
	"log"
	"net/http"
	"sync"
	"time"

	"ctos_api/internal/database"
)

// A person's own Secure Drop key pair: generating or importing one, proving they hold the
// private half without this server ever seeing it, and getting the encrypted copy back if they
// need it on another device. Mirrors the website's EncryptionKeyController exactly — see
// docs/secure-drop.md for the whole protocol.

const challengeLifetime = 5 * time.Minute

// pendingChallenges remembers a not-yet-trusted public key's nonce for a few minutes, keyed by
// user id — the API's equivalent of the website's session, since this API keeps none. The zero
// value is ready to use, the same as LoginFailures and JoinFailures already are.
type pendingChallenges struct {
	mutex   sync.Mutex
	pending map[int64]pendingChallenge
}

type pendingChallenge struct {
	publicKey string
	nonce     []byte
	expiresAt time.Time
}

func (p *pendingChallenges) set(userID int64, publicKey string, nonce []byte) {
	p.mutex.Lock()
	defer p.mutex.Unlock()
	if p.pending == nil {
		p.pending = map[int64]pendingChallenge{}
	}
	p.pending[userID] = pendingChallenge{publicKey: publicKey, nonce: nonce, expiresAt: time.Now().Add(challengeLifetime)}
}

// take returns the pending challenge and forgets it (used or not, a challenge is answered once).
func (p *pendingChallenges) take(userID int64) (pendingChallenge, bool) {
	p.mutex.Lock()
	defer p.mutex.Unlock()
	found, ok := p.pending[userID]
	delete(p.pending, userID)
	if !ok || time.Now().After(found.expiresAt) {
		return pendingChallenge{}, false
	}
	return found, true
}

// encryptionKeyChallenge: POST /api/v1/encryption-keys/challenge  {"public_key"}
// Step one of setting up a key pair: encrypts a random nonce with the device's (new or imported)
// public key. Only the matching private key can recover it.
func (app *App) encryptionKeyChallenge(w http.ResponseWriter, r *http.Request) {
	user := r.Context().Value(userContextKey).(*database.User)

	var input struct {
		PublicKey string `json:"public_key"`
	}
	if err := json.NewDecoder(r.Body).Decode(&input); err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]any{"message": "Request body must be valid JSON."})
		return
	}
	if message := checkSecureDropPublicKey(input.PublicKey); message != "" {
		writeInvalid(w, "public_key", message)
		return
	}

	nonce := make([]byte, 32)
	if _, err := rand.Read(nonce); err != nil {
		log.Printf("encryption key challenge: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}
	ciphertext, err := encryptSecureDropNonce(input.PublicKey, nonce)
	if err != nil {
		log.Printf("encryption key challenge: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}

	app.SecureDropChallenges.set(user.ID, input.PublicKey, nonce)
	writeJSON(w, http.StatusOK, map[string]any{"data": map[string]any{"ciphertext": ciphertext}})
}

// storeEncryptionKey: POST /api/v1/encryption-keys  {"proof", "encrypted_private_key"}
// Step two: the device decrypted the challenge (proving it holds the private key) and has
// encrypted that same private key with a passphrase only its owner knows.
func (app *App) storeEncryptionKey(w http.ResponseWriter, r *http.Request) {
	user := r.Context().Value(userContextKey).(*database.User)

	var input struct {
		Proof               string `json:"proof"`
		EncryptedPrivateKey string `json:"encrypted_private_key"`
	}
	if err := json.NewDecoder(r.Body).Decode(&input); err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]any{"message": "Request body must be valid JSON."})
		return
	}

	pending, ok := app.SecureDropChallenges.take(user.ID)
	if !ok {
		writeInvalid(w, "proof", "That challenge has expired. Try again.")
		return
	}
	if input.Proof != base64.StdEncoding.EncodeToString(pending.nonce) {
		writeInvalid(w, "proof", "That did not prove you hold the matching private key.")
		return
	}
	if message := checkSecureDropEncryptedPrivateKey(input.EncryptedPrivateKey); message != "" {
		writeInvalid(w, "encrypted_private_key", message)
		return
	}

	fingerprint := secureDropFingerprint(pending.publicKey)
	if err := app.DB.SetEncryptionKey(r.Context(), user.ID, pending.publicKey, input.EncryptedPrivateKey, fingerprint); err != nil {
		log.Printf("store encryption key: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}

	writeJSON(w, http.StatusOK, map[string]any{"message": "Your Secure Drop key is set up. Keep your passphrase somewhere safe: it is the only way to read what is shared with you."})
}

// showEncryptionKey: GET /api/v1/encryption-keys
func (app *App) showEncryptionKey(w http.ResponseWriter, r *http.Request) {
	user := r.Context().Value(userContextKey).(*database.User)

	key, err := app.DB.GetEncryptionKey(r.Context(), user.ID)
	if err != nil {
		log.Printf("show encryption key: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}
	if key == nil {
		writeJSON(w, http.StatusNotFound, map[string]any{"message": "Not found."})
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"data": map[string]any{
		"fingerprint": key.Fingerprint,
		"created_at":  key.CreatedAt.UnixMilli(),
		// Needed to wrap a file's AES key for yourself when sending directly by email
		// (docs/secure-drop.md), where there is no department member list to find it in.
		"public_key": key.PublicKey,
	}})
}

// downloadEncryptionKey: GET /api/v1/encryption-keys/download
// The encrypted private key back, to import into another device. Only ever their own.
func (app *App) downloadEncryptionKey(w http.ResponseWriter, r *http.Request) {
	user := r.Context().Value(userContextKey).(*database.User)

	key, err := app.DB.GetEncryptionKey(r.Context(), user.ID)
	if err != nil {
		log.Printf("download encryption key: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}
	if key == nil {
		writeJSON(w, http.StatusNotFound, map[string]any{"message": "Not found."})
		return
	}
	w.Header().Set("Content-Type", "application/x-pem-file")
	w.Write([]byte(key.EncryptedPrivateKey))
}

// deleteEncryptionKey: DELETE /api/v1/encryption-keys
// Starting over: removes the key pair and every secure drop this person sent (nobody could still
// open them without it) along with their own copy of anything shared with them.
func (app *App) deleteEncryptionKey(w http.ResponseWriter, r *http.Request) {
	user := r.Context().Value(userContextKey).(*database.User)

	sent, err := app.DB.GetSecureDropsSent(r.Context(), user.ID)
	if err != nil {
		log.Printf("delete encryption key: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}
	for _, drop := range sent {
		app.Files.RemoveSecureDrop(drop.StoragePath)
		if err := app.DB.DeleteSecureDrop(r.Context(), drop.ID); err != nil {
			log.Printf("delete encryption key: %v", err)
			writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
			return
		}
	}
	if err := app.DB.DeleteSecureDropRecipientRowsFor(r.Context(), user.ID); err != nil {
		log.Printf("delete encryption key: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}
	if err := app.DB.DeleteEncryptionKey(r.Context(), user.ID); err != nil {
		log.Printf("delete encryption key: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}

	writeJSON(w, http.StatusOK, map[string]any{"message": "Your Secure Drop key, and everything sent with it, was removed."})
}
