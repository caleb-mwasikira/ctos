// Package handlers holds the HTTP handlers, one file per resource
// (auth.go, departments.go, ...).
//
// Each handler reads the request, checks the input with the validate
// functions, calls the database package, and writes JSON. routes.go lists
// which URL goes to which handler.
package handlers

import (
	"encoding/json"
	"net/http"

	"ctos_api/internal/config"
	"ctos_api/internal/database"
	"ctos_api/internal/mail"
	"ctos_api/internal/realtime"
	"ctos_api/internal/storage"
)

// App is everything a handler needs. Handlers are methods on it.
type App struct {
	DB     *database.Database
	Config *config.Config
	Hub    *realtime.Hub
	Files  *storage.Store

	// Wrong passwords at login and wrong access codes when joining a
	// department, so neither can be guessed by trying them all. The zero
	// value is ready to use.
	LoginFailures failureCounter
	JoinFailures  failureCounter

	// How many times each user has shared a department by email in the last hour.
	ShareAttempts hourlyLimiter

	// A not-yet-trusted Secure Drop public key's proof-of-possession nonce, briefly, per user
	// (see docs/secure-drop.md). The zero value is ready to use.
	SecureDropChallenges pendingChallenges
}

// shareAttemptsPerHour matches the website's own "department-share" rate limit.
const shareAttemptsPerHour = 30

// mailConfig is where and how to send mail, read from the API's own configuration.
func (app *App) mailConfig() mail.Config {
	return mail.Config{
		Host:        app.Config.MailHost,
		Port:        app.Config.MailPort,
		Username:    app.Config.MailUsername,
		Password:    app.Config.MailPassword,
		FromAddress: app.Config.MailFromAddress,
		FromName:    app.Config.MailFromName,
	}
}

func writeJSON(w http.ResponseWriter, status int, body any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(body)
}

// writeInvalid answers 422 in the shape Laravel used, which the apps
// understand: {"message": "...", "errors": {"field": ["..."]}}.
func writeInvalid(w http.ResponseWriter, field string, message string) {
	writeJSON(w, http.StatusUnprocessableEntity, map[string]any{
		"message": message,
		"errors":  map[string][]string{field: {message}},
	})
}
