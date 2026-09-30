package handlers

import (
	"encoding/json"
	"io"
	"log"
	"net/http"
	"strconv"

	"github.com/go-chi/chi/v5"

	"ctos_api/internal/database"
)

// End-to-end encrypted files shared between the members of a department (see
// docs/secure-drop.md). Every byte this file ever touches is already ciphertext or an RSA-wrapped
// AES key made on someone else's device; it neither makes nor needs the ability to read either.
// Mirrors the website's SecureDropController exactly.

const maxSecureDropBytes = 200 << 20 // 200 MB of ciphertext

func secureDropResponse(drop database.SecureDrop) map[string]any {
	return map[string]any{
		"id":              drop.ID,
		"department_id":   drop.DepartmentID,
		"department_name": drop.DepartmentName,
		"owner_id":        drop.OwnerID,
		"owner_name":      drop.OwnerName,
		"name":            drop.Name,
		"size":            drop.Size,
		"iv":              drop.IV,
		"created_at":      drop.CreatedAt.UnixMilli(),
	}
}

// listSecureDrops: GET /api/v1/secure-drops
func (app *App) listSecureDrops(w http.ResponseWriter, r *http.Request) {
	user := r.Context().Value(userContextKey).(*database.User)

	sent, err := app.DB.GetSecureDropsSent(r.Context(), user.ID)
	if err != nil {
		log.Printf("list secure drops: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}
	received, err := app.DB.GetSecureDropsReceived(r.Context(), user.ID)
	if err != nil {
		log.Printf("list secure drops: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}

	sentOut := make([]map[string]any, len(sent))
	for i, drop := range sent {
		sentOut[i] = secureDropResponse(drop)
	}
	receivedOut := make([]map[string]any, len(received))
	for i, drop := range received {
		receivedOut[i] = secureDropResponse(drop)
	}
	writeJSON(w, http.StatusOK, map[string]any{"data": map[string]any{"sent": sentOut, "received": receivedOut}})
}

// secureDropRecipients: GET /api/v1/departments/{department}/secure-drop-recipients
// The department's members who have a Secure Drop key: their public key, to wrap a file's AES key for them.
func (app *App) secureDropRecipients(w http.ResponseWriter, r *http.Request) {
	department, ok := app.memberDepartment(w, r)
	if !ok {
		return
	}

	members, err := app.DB.GetDepartmentMembersWithKeys(r.Context(), department.ID)
	if err != nil {
		log.Printf("secure drop recipients: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}

	out := make([]map[string]any, len(members))
	for i, member := range members {
		out[i] = map[string]any{"id": member.ID, "name": member.Name, "public_key": member.PublicKey, "fingerprint": member.Fingerprint}
	}
	writeJSON(w, http.StatusOK, map[string]any{"data": out})
}

type secureDropRecipientJSON struct {
	UserID       int64  `json:"user_id"`
	EncryptedKey string `json:"encrypted_key"`
}

// storeSecureDrop: POST /api/v1/departments/{department}/secure-drop
// multipart/form-data: name, iv (base64), ciphertext (file), recipients (JSON array of
// {"user_id", "encrypted_key"}). Every byte of it was already encrypted on the sender's device.
func (app *App) storeSecureDrop(w http.ResponseWriter, r *http.Request) {
	department, ok := app.memberDepartment(w, r)
	if !ok {
		return
	}
	user := r.Context().Value(userContextKey).(*database.User)

	ownKey, err := app.DB.GetEncryptionKey(r.Context(), user.ID)
	if err != nil {
		log.Printf("store secure drop: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}
	if ownKey == nil {
		writeJSON(w, http.StatusForbidden, map[string]any{"message": "Set up your Secure Drop key first."})
		return
	}

	if err := r.ParseMultipartForm(maxSecureDropBytes); err != nil {
		writeJSON(w, http.StatusUnprocessableEntity, map[string]any{"message": "The upload is too large, or malformed."})
		return
	}

	name := r.FormValue("name")
	if message := validateRequiredText("name", name, 1024); message != "" {
		writeInvalid(w, "name", message)
		return
	}
	iv := r.FormValue("iv")
	if len(iv) != 16 { // 12 raw bytes, base64
		writeInvalid(w, "iv", "The iv field must be 12 bytes, base64 encoded.")
		return
	}

	var recipients []secureDropRecipientJSON
	if err := json.Unmarshal([]byte(r.FormValue("recipients")), &recipients); err != nil || len(recipients) == 0 {
		writeInvalid(w, "recipients", "At least one recipient is required.")
		return
	}

	keyedMembers, err := app.DB.GetDepartmentMembersWithKeys(r.Context(), department.ID)
	if err != nil {
		log.Printf("store secure drop: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}
	allowed := map[int64]bool{user.ID: true}
	for _, member := range keyedMembers {
		allowed[member.ID] = true
	}
	for _, recipient := range recipients {
		if !allowed[recipient.UserID] {
			writeInvalid(w, "recipients", "That recipient has no Secure Drop key.")
			return
		}
	}

	file, _, err := r.FormFile("ciphertext")
	if err != nil {
		writeInvalid(w, "ciphertext", "The ciphertext field is required.")
		return
	}
	defer file.Close()

	saved, err := app.Files.SaveSecureDrop(file, maxSecureDropBytes)
	if err != nil {
		log.Printf("store secure drop: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}

	dbRecipients := make([]database.SecureDropRecipientInput, len(recipients))
	for i, recipient := range recipients {
		dbRecipients[i] = database.SecureDropRecipientInput{UserID: recipient.UserID, EncryptedKey: recipient.EncryptedKey}
	}
	dropID, err := app.DB.CreateSecureDrop(r.Context(), department.ID, user.ID, name, saved.Size, saved.Key, iv, dbRecipients)
	if err != nil {
		log.Printf("store secure drop: %v", err)
		app.Files.RemoveSecureDrop(saved.Key)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}

	writeJSON(w, http.StatusCreated, map[string]any{"data": map[string]any{"id": dropID}})
}

// showSecureDrop: GET /api/v1/secure-drops/{secureDrop}
// What a recipient (or the owner) needs to decrypt it: the nonce and their own wrapped key.
func (app *App) showSecureDrop(w http.ResponseWriter, r *http.Request) {
	drop, recipientKey, ok := app.authorizedSecureDrop(w, r)
	if !ok {
		return
	}

	response := secureDropResponse(*drop)
	response["encrypted_key"] = recipientKey
	writeJSON(w, http.StatusOK, map[string]any{"data": response})
}

// downloadSecureDrop: GET /api/v1/secure-drops/{secureDrop}/download
// The ciphertext itself; identical for every recipient, only openable with their own key.
func (app *App) downloadSecureDrop(w http.ResponseWriter, r *http.Request) {
	drop, _, ok := app.authorizedSecureDrop(w, r)
	if !ok {
		return
	}
	user := r.Context().Value(userContextKey).(*database.User)

	file, err := app.Files.OpenSecureDrop(drop.StoragePath)
	if err != nil {
		log.Printf("download secure drop: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}
	defer file.Close()

	if err := app.DB.MarkSecureDropOpened(r.Context(), drop.ID, user.ID); err != nil {
		log.Printf("download secure drop: %v", err)
	}

	w.Header().Set("Content-Type", "application/octet-stream")
	io.Copy(w, file)
}

// deleteSecureDrop: DELETE /api/v1/secure-drops/{secureDrop}
func (app *App) deleteSecureDrop(w http.ResponseWriter, r *http.Request) {
	user := r.Context().Value(userContextKey).(*database.User)

	dropID, err := strconv.ParseInt(chi.URLParam(r, "secureDrop"), 10, 64)
	if err != nil {
		writeJSON(w, http.StatusNotFound, map[string]any{"message": "Not found."})
		return
	}
	drop, err := app.DB.GetSecureDrop(r.Context(), dropID)
	if err != nil {
		log.Printf("delete secure drop: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}
	if drop == nil || drop.OwnerID != user.ID {
		writeJSON(w, http.StatusForbidden, map[string]any{"message": "Not found."})
		return
	}

	app.Files.RemoveSecureDrop(drop.StoragePath)
	if err := app.DB.DeleteSecureDrop(r.Context(), drop.ID); err != nil {
		log.Printf("delete secure drop: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}

	writeJSON(w, http.StatusOK, map[string]any{"message": "Deleted."})
}

// authorizedSecureDrop: the drop's owner, or someone it was sent to — 404 either way for everyone
// else, so a drop's existence is not leaked.
func (app *App) authorizedSecureDrop(w http.ResponseWriter, r *http.Request) (*database.SecureDrop, string, bool) {
	user := r.Context().Value(userContextKey).(*database.User)

	dropID, err := strconv.ParseInt(chi.URLParam(r, "secureDrop"), 10, 64)
	if err != nil {
		writeJSON(w, http.StatusNotFound, map[string]any{"message": "Not found."})
		return nil, "", false
	}

	drop, err := app.DB.GetSecureDrop(r.Context(), dropID)
	if err != nil {
		log.Printf("authorized secure drop: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return nil, "", false
	}
	if drop == nil {
		writeJSON(w, http.StatusNotFound, map[string]any{"message": "Not found."})
		return nil, "", false
	}

	encryptedKey, err := app.DB.GetSecureDropRecipientKey(r.Context(), dropID, user.ID)
	if err != nil {
		log.Printf("authorized secure drop: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return nil, "", false
	}
	if encryptedKey == nil {
		writeJSON(w, http.StatusNotFound, map[string]any{"message": "Not found."})
		return nil, "", false
	}

	return drop, *encryptedKey, true
}
