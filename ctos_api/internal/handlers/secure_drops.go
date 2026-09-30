package handlers

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"strconv"
	"strings"

	"github.com/go-chi/chi/v5"

	"ctos_api/internal/database"
	"ctos_api/internal/mail"
)

// End-to-end encrypted files shared between the members of a department, or sent directly by
// email (see docs/secure-drop.md). Every byte this file ever touches is already ciphertext or an
// RSA- or password-wrapped AES key made on someone else's device; it neither makes nor needs the
// ability to read either. The one exception is a one-time password for someone with no account
// yet (sendSecureDropDirect): this server has to see that in order to put it in their email.
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
// Every active member, whether or not they have a Secure Drop key yet — so the sending screen can
// show who to nudge, not just who is already pickable.
func (app *App) secureDropRecipients(w http.ResponseWriter, r *http.Request) {
	department, ok := app.memberDepartment(w, r)
	if !ok {
		return
	}

	members, err := app.DB.GetDepartmentMembersWithKeyStatus(r.Context(), department.ID)
	if err != nil {
		log.Printf("secure drop recipients: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}

	out := make([]map[string]any, len(members))
	for i, member := range members {
		out[i] = map[string]any{
			"id": member.ID, "name": member.Name,
			"public_key": member.PublicKey, "fingerprint": member.Fingerprint,
			"has_key": member.PublicKey != nil,
		}
	}
	writeJSON(w, http.StatusOK, map[string]any{"data": out})
}

// lookupSecureDropRecipient: POST /api/v1/secure-drops/lookup  {"email"}
// Whether an email belongs to an account with a Secure Drop key — enough to send to them
// directly, not scoped to any department.
func (app *App) lookupSecureDropRecipient(w http.ResponseWriter, r *http.Request) {
	user := r.Context().Value(userContextKey).(*database.User)

	var input struct {
		Email string `json:"email"`
	}
	if err := json.NewDecoder(r.Body).Decode(&input); err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]any{"message": "Request body must be valid JSON."})
		return
	}
	if message := validateEmail(input.Email); message != "" {
		writeInvalid(w, "email", message)
		return
	}
	email := strings.ToLower(strings.TrimSpace(input.Email))

	candidate, err := app.DB.FindUserByEmail(r.Context(), email)
	if err != nil {
		log.Printf("lookup secure drop recipient: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}
	if candidate == nil {
		writeJSON(w, http.StatusOK, map[string]any{"data": map[string]any{"has_account": false}})
		return
	}

	key, err := app.DB.GetEncryptionKey(r.Context(), candidate.ID)
	if err != nil {
		log.Printf("lookup secure drop recipient: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}
	if key == nil {
		writeJSON(w, http.StatusOK, map[string]any{"data": map[string]any{"has_account": false}})
		return
	}
	if candidate.ID == user.ID {
		writeJSON(w, http.StatusUnprocessableEntity, map[string]any{"message": "That is your own email."})
		return
	}

	writeJSON(w, http.StatusOK, map[string]any{"data": map[string]any{
		"has_account": true, "id": candidate.ID, "name": candidate.Name,
		"public_key": key.PublicKey, "fingerprint": key.Fingerprint,
	}})
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
		userID := recipient.UserID
		dbRecipients[i] = database.SecureDropRecipientInput{UserID: &userID, EncryptedKey: recipient.EncryptedKey}
	}
	dropID, err := app.DB.CreateSecureDrop(r.Context(), &department.ID, user.ID, name, saved.Size, saved.Key, iv, dbRecipients)
	if err != nil {
		log.Printf("store secure drop: %v", err)
		app.Files.RemoveSecureDrop(saved.Key)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}

	app.emailRealSecureDropRecipients(r.Context(), recipients, user, name)

	writeJSON(w, http.StatusCreated, map[string]any{"data": map[string]any{"id": dropID}})
}

// sendSecureDropDirect: POST /api/v1/secure-drops/send
// Like storeSecureDrop, but not scoped to a department: multipart/form-data with the same name,
// iv, ciphertext and recipients fields, plus an optional password_recipient (JSON object of
// {"email", "encrypted_key"}) and password (plain, only ever used to email it) for someone with
// no account yet.
func (app *App) sendSecureDropDirect(w http.ResponseWriter, r *http.Request) {
	user := r.Context().Value(userContextKey).(*database.User)

	ownKey, err := app.DB.GetEncryptionKey(r.Context(), user.ID)
	if err != nil {
		log.Printf("send secure drop: %v", err)
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
	if len(iv) != 16 {
		writeInvalid(w, "iv", "The iv field must be 12 bytes, base64 encoded.")
		return
	}

	var recipients []secureDropRecipientJSON
	if raw := r.FormValue("recipients"); raw != "" {
		if err := json.Unmarshal([]byte(raw), &recipients); err != nil {
			writeInvalid(w, "recipients", "Malformed recipients.")
			return
		}
	}

	var passwordRecipient *struct {
		Email        string `json:"email"`
		EncryptedKey string `json:"encrypted_key"`
	}
	password := r.FormValue("password")
	if raw := r.FormValue("password_recipient"); raw != "" {
		if err := json.Unmarshal([]byte(raw), &passwordRecipient); err != nil || passwordRecipient.Email == "" || passwordRecipient.EncryptedKey == "" {
			writeInvalid(w, "password_recipient", "Malformed password recipient.")
			return
		}
		if len(password) < 8 {
			writeInvalid(w, "password", "A password is required alongside a password_recipient.")
			return
		}
	}

	if len(recipients) == 0 && passwordRecipient == nil {
		writeInvalid(w, "recipients", "Pick at least one person to send it to.")
		return
	}

	// Every named recipient must actually have a Secure Drop key — the owner always does (checked
	// above); anyone else's device could only have fetched a public key to wrap for honestly if
	// that key is real.
	for _, recipient := range recipients {
		if recipient.UserID == user.ID {
			continue
		}
		key, err := app.DB.GetEncryptionKey(r.Context(), recipient.UserID)
		if err != nil {
			log.Printf("send secure drop: %v", err)
			writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
			return
		}
		if key == nil {
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
		log.Printf("send secure drop: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}

	dbRecipients := make([]database.SecureDropRecipientInput, len(recipients))
	for i, recipient := range recipients {
		userID := recipient.UserID
		dbRecipients[i] = database.SecureDropRecipientInput{UserID: &userID, EncryptedKey: recipient.EncryptedKey}
	}
	var accessToken string
	if passwordRecipient != nil {
		accessToken = randomToken()
		dbRecipients = append(dbRecipients, database.SecureDropRecipientInput{
			Email: &passwordRecipient.Email, EncryptedKey: passwordRecipient.EncryptedKey, AccessToken: &accessToken,
		})
	}

	dropID, err := app.DB.CreateSecureDrop(r.Context(), nil, user.ID, name, saved.Size, saved.Key, iv, dbRecipients)
	if err != nil {
		log.Printf("send secure drop: %v", err)
		app.Files.RemoveSecureDrop(saved.Key)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}

	app.emailRealSecureDropRecipients(r.Context(), recipients, user, name)
	if passwordRecipient != nil {
		subject := fmt.Sprintf("%s sent you a file, encrypted", user.Name)
		body := secureDropPasswordEmailBody(app.Config.WebsiteURL, user.Name, name, password, accessToken)
		if err := mail.Send(app.mailConfig(), passwordRecipient.Email, subject, body); err != nil {
			log.Printf("send secure drop password email: %v", err)
		}
	}

	writeJSON(w, http.StatusCreated, map[string]any{"data": map[string]any{"id": dropID}})
}

// emailRealSecureDropRecipients tells every recipient but the owner (already there, and already
// knows) that something was sent to them. A mail failure is only logged; it never undoes the send.
func (app *App) emailRealSecureDropRecipients(ctx context.Context, recipients []secureDropRecipientJSON, owner *database.User, name string) {
	for _, recipient := range recipients {
		if recipient.UserID == owner.ID {
			continue
		}
		recipientUser, err := app.DB.GetUserByID(ctx, recipient.UserID)
		if err != nil || recipientUser == nil {
			continue
		}
		subject := fmt.Sprintf("%s sent you a file, encrypted", owner.Name)
		body := secureDropReceivedEmailBody(app.Config.WebsiteURL, owner.Name, name)
		if err := mail.Send(app.mailConfig(), recipientUser.Email, subject, body); err != nil {
			log.Printf("send secure drop received email: %v", err)
		}
	}
}

func secureDropReceivedEmailBody(websiteURL, senderName, fileName string) string {
	return fmt.Sprintf(
		"Hello!\n\n%s sent you \"%s\" through ctOS's Secure Drop, encrypted on their own device before it ever left it.\n\n"+
			"Open it on ctOS:\n%s/secure-drop\n\n"+
			"Opening it there asks for your Secure Drop passphrase once, decrypts the file in your browser, and saves the result to your device.\n",
		senderName, fileName, websiteURL)
}

func secureDropPasswordEmailBody(websiteURL, senderName, fileName, password, token string) string {
	return fmt.Sprintf(
		"Hello!\n\n%s sent you \"%s\" through ctOS's Secure Drop.\n\n"+
			"You do not have a ctOS account yet, so instead of your own key, it is protected with a one-time password:\n\n%s\n\n"+
			"Open it:\n%s/secure-drop/shared/%s\n\n"+
			"Anyone with this email and that password can open it, so treat it the way you would the file itself.\n\n"+
			"---\n\nDon't have an account yet? Create one:\n%s/register\n\n"+
			"Set up your own Secure Drop key, and next time a file like this one arrives already yours to open — no password needed.\n",
		senderName, fileName, password, websiteURL, token, websiteURL)
}

// randomToken is an unguessable link for someone with no account: the job a login would do.
func randomToken() string {
	bytes := make([]byte, 32)
	if _, err := rand.Read(bytes); err != nil {
		panic(err) // crypto/rand failing means the system's entropy source is broken; nothing safe to do but stop.
	}
	return hex.EncodeToString(bytes)
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
