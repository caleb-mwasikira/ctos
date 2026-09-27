package handlers

import (
	"context"
	"crypto/rand"
	"encoding/json"
	"fmt"
	"log"
	"math/big"
	"net/http"
	"strings"
	"time"

	"golang.org/x/crypto/bcrypt"

	"ctos_api/internal/database"
	"ctos_api/internal/mail"
)

// Registering, verifying an email address and resetting a password, all from the app instead of
// a browser: each one emails a 6 digit code (see database.VerificationCode) that the app's own
// screen asks the person to type back in, rather than a link that opens a browser like the
// website's own auth pages do. codeExpiry matches how long the website's own password reset
// links and email verification stay good for.
const codeExpiry = 60 * time.Minute

const codeLength = 6

func generateNumericCode() (string, error) {
	digits := make([]byte, codeLength)
	for i := range digits {
		n, err := rand.Int(rand.Reader, big.NewInt(10))
		if err != nil {
			return "", err
		}
		digits[i] = byte('0') + byte(n.Int64())
	}
	return string(digits), nil
}

// sendCode makes a fresh code, stores its hash for email/purpose (replacing any code already
// waiting there) and emails it. Returns false and already wrote the response when something
// about the code itself (not the email) failed.
func (app *App) sendCode(ctx context.Context, w http.ResponseWriter, email, purpose, subject, body string) bool {
	code, err := generateNumericCode()
	if err != nil {
		log.Printf("generate code: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return false
	}
	hash, err := bcrypt.GenerateFromPassword([]byte(code), bcrypt.DefaultCost)
	if err != nil {
		log.Printf("hash code: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return false
	}
	if err := app.DB.SetVerificationCode(ctx, email, purpose, string(hash)); err != nil {
		log.Printf("store code: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return false
	}

	// The account or the reset still works even if the mail server is down: report it and move
	// on, the same as the website does around its own Registered event.
	if err := mail.Send(app.mailConfig(), email, subject, fmt.Sprintf("%s\n\nYour code: %s\n\nIt stops working in an hour.", body, code)); err != nil {
		log.Printf("send code email: %v", err)
	}
	return true
}

// checkCode compares code against whatever is stored for email/purpose. ok is false, and the
// code consumed either way (deleted), when it does not match or is too old.
func (app *App) checkCode(ctx context.Context, email, purpose, code string) (ok bool, err error) {
	stored, err := app.DB.GetVerificationCode(ctx, email, purpose)
	if err != nil || stored == nil {
		return false, err
	}
	matches := bcrypt.CompareHashAndPassword([]byte(stored.CodeHash), []byte(code)) == nil
	fresh := time.Since(stored.CreatedAt) < codeExpiry
	if matches && fresh {
		return true, app.DB.DeleteVerificationCode(ctx, email, purpose)
	}
	return false, nil
}

// register: POST /api/v1/auth/register  {"name", "email", "password", "password_confirmation"}
// Creates the account, unverified, and emails a code to confirm the address β€” matching
// RegisteredUserController on the website, other than the code instead of a signed link. No
// token is issued yet: login (and this API's other routes) refuse an unverified account, so the
// app calls POST /auth/verify-email next to finish signing in.
func (app *App) register(w http.ResponseWriter, r *http.Request) {
	var input struct {
		Name                 string `json:"name"`
		Email                string `json:"email"`
		Password             string `json:"password"`
		PasswordConfirmation string `json:"password_confirmation"`
	}
	if err := json.NewDecoder(r.Body).Decode(&input); err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]any{"message": "Request body must be valid JSON."})
		return
	}

	if message := validateRequiredText("name", input.Name, 100); message != "" {
		writeInvalid(w, "name", message)
		return
	}
	if message := validateEmail(input.Email); message != "" {
		writeInvalid(w, "email", message)
		return
	}
	if message := validateNewPassword(input.Password, input.PasswordConfirmation); message != "" {
		writeInvalid(w, "password", message)
		return
	}

	email := strings.ToLower(strings.TrimSpace(input.Email))
	existing, err := app.DB.GetUserByEmail(r.Context(), email)
	if err != nil {
		log.Printf("register: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}
	if existing != nil {
		writeInvalid(w, "email", "The email has already been taken.")
		return
	}

	hash, err := bcrypt.GenerateFromPassword([]byte(input.Password), bcrypt.DefaultCost)
	if err != nil {
		log.Printf("register: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}
	if _, err := app.DB.CreateUser(r.Context(), strings.TrimSpace(input.Name), email, string(hash)); err != nil {
		log.Printf("register: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}

	subject := fmt.Sprintf("Confirm your %s account", app.Config.AppName)
	body := fmt.Sprintf("Hello!\n\nEnter this code in the app to confirm %s is your email address.", email)
	if !app.sendCode(r.Context(), w, email, database.PurposeEmailVerify, subject, body) {
		return
	}

	writeJSON(w, http.StatusCreated, map[string]any{"data": map[string]any{"email": email}})
}

// verifyEmail: POST /api/v1/auth/verify-email  {"email", "code", "device_name"}
// Confirms the address and, since only a verified account may sign in, logs the app straight in
// with a fresh token β€” the code did what opening the emailed link does on the website.
func (app *App) verifyEmail(w http.ResponseWriter, r *http.Request) {
	var input struct {
		Email      string `json:"email"`
		Code       string `json:"code"`
		DeviceName string `json:"device_name"`
	}
	if err := json.NewDecoder(r.Body).Decode(&input); err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]any{"message": "Request body must be valid JSON."})
		return
	}
	if message := validateEmail(input.Email); message != "" {
		writeInvalid(w, "email", message)
		return
	}
	if message := validateVerificationCode(input.Code); message != "" {
		writeInvalid(w, "code", message)
		return
	}
	if message := validateRequiredText("device name", input.DeviceName, 100); message != "" {
		writeInvalid(w, "device_name", message)
		return
	}

	email := strings.ToLower(strings.TrimSpace(input.Email))
	user, err := app.DB.GetUserByEmail(r.Context(), email)
	if err != nil {
		log.Printf("verify email: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}
	if user == nil {
		writeInvalid(w, "code", "That code is invalid or has expired.")
		return
	}
	if user.EmailVerified {
		writeInvalid(w, "code", "That address is already confirmed. Log in instead.")
		return
	}

	ok, err := app.checkCode(r.Context(), email, database.PurposeEmailVerify, input.Code)
	if err != nil {
		log.Printf("verify email: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}
	if !ok {
		writeInvalid(w, "code", "That code is invalid or has expired.")
		return
	}

	if err := app.DB.MarkEmailVerified(r.Context(), user.ID); err != nil {
		log.Printf("verify email: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}

	token, err := app.createToken(r.Context(), user.ID, input.DeviceName)
	if err != nil {
		log.Printf("verify email: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"data": map[string]any{
		"token": token,
		"user":  map[string]any{"id": user.ID, "name": user.Name, "email": user.Email},
	}})
}

// resendVerificationCode: POST /api/v1/auth/verify-email/resend  {"email"}
// Always answers the same way whether or not the address belongs to an account, or is already
// confirmed, so it cannot be used to find out who is registered.
func (app *App) resendVerificationCode(w http.ResponseWriter, r *http.Request) {
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
	user, err := app.DB.GetUserByEmail(r.Context(), email)
	if err != nil {
		log.Printf("resend verification code: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}
	if user != nil && !user.EmailVerified {
		subject := fmt.Sprintf("Confirm your %s account", app.Config.AppName)
		body := fmt.Sprintf("Hello!\n\nEnter this code in the app to confirm %s is your email address.", email)
		if !app.sendCode(r.Context(), w, email, database.PurposeEmailVerify, subject, body) {
			return
		}
	}
	writeJSON(w, http.StatusOK, map[string]any{"message": "If that address needs confirming, we sent it a new code."})
}

// forgotPassword: POST /api/v1/auth/forgot-password  {"email"}
// Always answers the same way whether or not the address belongs to an account, so it cannot be
// used to find out who is registered β€” matches PasswordResetLinkController on the website.
func (app *App) forgotPassword(w http.ResponseWriter, r *http.Request) {
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
	user, err := app.DB.GetUserByEmail(r.Context(), email)
	if err != nil {
		log.Printf("forgot password: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}
	if user != nil {
		subject := fmt.Sprintf("Reset your %s password", app.Config.AppName)
		body := "Hello!\n\nEnter this code in the app to choose a new password. If you did not ask for this, nothing has changed and you can ignore it."
		if !app.sendCode(r.Context(), w, email, database.PurposePasswordReset, subject, body) {
			return
		}
	}
	writeJSON(w, http.StatusOK, map[string]any{"message": "If an account exists for that email address, we sent it a code to reset the password."})
}

// resetPassword: POST /api/v1/auth/reset-password  {"email", "code", "password", "password_confirmation"}
// Every device signed in with the old password is signed out, the same way the website's own
// reset is: the app that made this request has to sign in again with the new one.
func (app *App) resetPassword(w http.ResponseWriter, r *http.Request) {
	var input struct {
		Email                string `json:"email"`
		Code                 string `json:"code"`
		Password             string `json:"password"`
		PasswordConfirmation string `json:"password_confirmation"`
	}
	if err := json.NewDecoder(r.Body).Decode(&input); err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]any{"message": "Request body must be valid JSON."})
		return
	}
	if message := validateEmail(input.Email); message != "" {
		writeInvalid(w, "email", message)
		return
	}
	if message := validateVerificationCode(input.Code); message != "" {
		writeInvalid(w, "code", message)
		return
	}
	if message := validateNewPassword(input.Password, input.PasswordConfirmation); message != "" {
		writeInvalid(w, "password", message)
		return
	}

	email := strings.ToLower(strings.TrimSpace(input.Email))
	user, err := app.DB.GetUserByEmail(r.Context(), email)
	if err != nil {
		log.Printf("reset password: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}
	if user == nil {
		writeInvalid(w, "code", "This password reset code is invalid or has expired.")
		return
	}

	ok, err := app.checkCode(r.Context(), email, database.PurposePasswordReset, input.Code)
	if err != nil {
		log.Printf("reset password: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}
	if !ok {
		writeInvalid(w, "code", "This password reset code is invalid or has expired.")
		return
	}

	hash, err := bcrypt.GenerateFromPassword([]byte(input.Password), bcrypt.DefaultCost)
	if err != nil {
		log.Printf("reset password: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}
	if err := app.DB.SetPassword(r.Context(), user.ID, string(hash)); err != nil {
		log.Printf("reset password: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}
	if err := app.DB.DeleteAccessTokensForUser(r.Context(), user.ID); err != nil {
		log.Printf("reset password: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}

	writeJSON(w, http.StatusOK, map[string]any{"message": "Your password has been reset. You can log in now."})
}
