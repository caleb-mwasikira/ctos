package handlers

import (
	"context"
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"hash/crc32"
	"log"
	"math/big"
	"net/http"
	"strconv"
	"strings"
	"sync"
	"time"

	"golang.org/x/crypto/bcrypt"

	"ctos_api/internal/database"
)

// Login tokens live in Laravel's personal_access_tokens table (Sanctum), so
// a token works on both the website's API and this one. The client sends
// "Authorization: Bearer 12|abc..."; the database stores sha256("abc...").

type contextKey string

// A handler behind requireLogin reads the logged-in user with:
//
//	user := r.Context().Value(userContextKey).(*database.User)
const (
	userContextKey    contextKey = "user"
	tokenIDContextKey contextKey = "tokenID"
)

// ── middleware ─────────────────────────────────────────────────────────────

// parseBearerToken splits "Bearer 12|abc..." into the token id (12) and the
// secret ("abc..."). The id is 0 when the client did not send the "id|" prefix.
// ok is false when there is no Bearer token at all.
func parseBearerToken(authorization string) (tokenID int64, secret string, ok bool) {
	token, hasBearer := strings.CutPrefix(authorization, "Bearer ")
	if !hasBearer || token == "" {
		return 0, "", false
	}

	idText, rest, hasID := strings.Cut(token, "|")
	if hasID {
		parsedID, err := strconv.ParseInt(idText, 10, 64)
		if err == nil {
			return parsedID, rest, true
		}
	}
	return 0, token, true
}

// findUserByToken looks up the user of the request's Bearer token.
// It returns nil when there is no token or it is unknown.
func (app *App) findUserByToken(r *http.Request) (*database.User, int64) {
	tokenID, secret, ok := parseBearerToken(r.Header.Get("Authorization"))
	if !ok {
		return nil, 0
	}
	secretHash := sha256.Sum256([]byte(secret))

	user, tokenID, err := app.DB.GetUserByAccessToken(r.Context(), tokenID, hex.EncodeToString(secretHash[:]))
	if err != nil || user == nil {
		return nil, 0
	}
	app.DB.MarkAccessTokenUsed(r.Context(), tokenID)
	return user, tokenID
}

// requireLogin rejects requests without a valid Bearer token.
func (app *App) requireLogin(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		user, tokenID := app.findUserByToken(r)
		if user == nil {
			writeJSON(w, http.StatusUnauthorized, map[string]any{"message": "Unauthenticated."})
			return
		}
		ctx := context.WithValue(r.Context(), userContextKey, user)
		ctx = context.WithValue(ctx, tokenIDContextKey, tokenID)
		next.ServeHTTP(w, r.WithContext(ctx))
	})
}

// createToken makes a new login token the same way Laravel Sanctum does:
// 40 random letters/digits plus an 8 character crc32 checksum. The database
// gets the sha256 hash; the client gets "{id}|{token}".
func (app *App) createToken(ctx context.Context, userID int64, deviceName string) (string, error) {
	const characters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
	randomPart := make([]byte, 40)
	for i := range randomPart {
		index, err := rand.Int(rand.Reader, big.NewInt(int64(len(characters))))
		if err != nil {
			return "", err
		}
		randomPart[i] = characters[index.Int64()]
	}
	token := string(randomPart) + fmt.Sprintf("%08x", crc32.ChecksumIEEE(randomPart))

	tokenHash := sha256.Sum256([]byte(token))
	tokenID, err := app.DB.CreateAccessToken(ctx, userID, deviceName, hex.EncodeToString(tokenHash[:]))
	if err != nil {
		return "", err
	}
	return fmt.Sprintf("%d|%s", tokenID, token), nil
}

// ── login attempts ─────────────────────────────────────────────────────────

// failureCounter remembers how many wrong attempts each key (an email, a user
// id) made lately. After maxFailures in a row the key is locked for
// lockMinutes, counted from the last wrong attempt.
type failureCounter struct {
	mutex    sync.Mutex
	failures map[string]attemptFailures
}

type attemptFailures struct {
	count int
	last  time.Time
}

const (
	maxFailures = 5
	lockMinutes = 5
)

func (counter *failureCounter) isLocked(key string) bool {
	counter.mutex.Lock()
	defer counter.mutex.Unlock()
	record := counter.failures[key]
	return record.count >= maxFailures && time.Since(record.last) < lockMinutes*time.Minute
}

func (counter *failureCounter) add(key string) {
	counter.mutex.Lock()
	defer counter.mutex.Unlock()
	if counter.failures == nil {
		counter.failures = map[string]attemptFailures{}
	}
	record := counter.failures[key]
	if time.Since(record.last) >= lockMinutes*time.Minute {
		record.count = 0 // the last lock has run out: start counting again
	}
	counter.failures[key] = attemptFailures{count: record.count + 1, last: time.Now()}
}

func (counter *failureCounter) clear(key string) {
	counter.mutex.Lock()
	defer counter.mutex.Unlock()
	delete(counter.failures, key)
}

// hourlyLimiter counts how many times a key was used in the last hour, for actions that must not
// be repeated without bound (sharing a department by email). The zero value is ready to use.
type hourlyLimiter struct {
	mutex   sync.Mutex
	windows map[string]hourlyWindow
}

type hourlyWindow struct {
	count int
	start time.Time
}

// allow says whether key may be used again, and counts this use if so. The window resets an hour
// after it started, the same period Laravel's Limit::perHour uses.
func (limiter *hourlyLimiter) allow(key string, max int) bool {
	limiter.mutex.Lock()
	defer limiter.mutex.Unlock()
	if limiter.windows == nil {
		limiter.windows = map[string]hourlyWindow{}
	}
	window := limiter.windows[key]
	if time.Since(window.start) >= time.Hour {
		window = hourlyWindow{start: time.Now()}
	}
	if window.count >= max {
		limiter.windows[key] = window
		return false
	}
	window.count++
	limiter.windows[key] = window
	return true
}

// ── login / logout ─────────────────────────────────────────────────────────

// login: POST /api/v1/auth/login  {"email", "password", "device_name"}
// device_name is what the token is called in the user's list of devices ("Alice's laptop").
func (app *App) login(w http.ResponseWriter, r *http.Request) {
	var input struct {
		Email      string `json:"email"`
		Password   string `json:"password"`
		DeviceName string `json:"device_name"`
	}
	if err := json.NewDecoder(r.Body).Decode(&input); err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]any{"message": "Request body must be valid JSON."})
		return
	}
	message := validateEmail(input.Email)
	if message != "" {
		writeInvalid(w, "email", message)
		return
	}
	message = validateLoginPassword(input.Password)
	if message != "" {
		writeInvalid(w, "password", message)
		return
	}
	message = validateRequiredText("device name", input.DeviceName, 100)
	if message != "" {
		writeInvalid(w, "device_name", message)
		return
	}

	// Wrong passwords are counted per email, so a password cannot be guessed by trying them all.
	emailKey := strings.ToLower(input.Email)
	if app.LoginFailures.isLocked(emailKey) {
		writeJSON(w, http.StatusTooManyRequests, map[string]any{"message": "Too many wrong passwords. Try again in a few minutes."})
		return
	}

	user, err := app.DB.GetUserByEmail(r.Context(), emailKey)
	if err != nil {
		log.Printf("login: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}
	if user == nil || bcrypt.CompareHashAndPassword([]byte(user.PasswordHash), []byte(input.Password)) != nil {
		app.LoginFailures.add(emailKey)
		writeJSON(w, http.StatusUnauthorized, map[string]any{"message": "Invalid credentials."})
		return
	}
	app.LoginFailures.clear(emailKey)

	// The website requires a verified email before anything else, so the apps do too.
	if !user.EmailVerified {
		writeJSON(w, http.StatusForbidden, map[string]any{"message": "Your email address is not verified. Open the link we emailed you, then log in again."})
		return
	}

	token, err := app.createToken(r.Context(), user.ID, input.DeviceName)
	if err != nil {
		log.Printf("login: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"data": map[string]any{
		"token": token,
		"user":  map[string]any{"id": user.ID, "name": user.Name, "email": user.Email},
	}})
}

// logout: POST /api/v1/auth/logout
// Deletes the token this request was made with.
func (app *App) logout(w http.ResponseWriter, r *http.Request) {
	tokenID := r.Context().Value(tokenIDContextKey).(int64)

	err := app.DB.DeleteAccessToken(r.Context(), tokenID)
	if err != nil {
		log.Printf("logout: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"message": "Logged out."})
}
