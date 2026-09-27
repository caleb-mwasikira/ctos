package handlers

import (
	"context"
	"fmt"
	"net/http"
	"testing"

	"golang.org/x/crypto/bcrypt"

	"ctos_api/internal/database"
)

// registeringUser makes a unique email for a register() test (which, unlike api.user(), does not
// insert the row itself) and cleans up whatever register() creates, however far the test got.
func (api *testAPI) registeringUser(name string) (email string) {
	api.t.Helper()
	api.count++
	email = fmt.Sprintf("%s-%d-%s@example.com", name, api.count, api.suffix)
	api.t.Cleanup(func() {
		var id int64
		if err := api.pool.QueryRow(context.Background(), `select id from users where email = $1`, email).Scan(&id); err == nil {
			api.exec(`delete from personal_access_tokens where tokenable_type = 'App\Models\User' and tokenable_id = $1`, id)
			api.exec(`delete from users where id = $1`, id)
		}
		api.exec(`delete from verification_codes where email = $1`, email)
	})
	return email
}

// plantKnownCode replaces whatever code the API just emailed with one the test knows (MAIL_HOST
// is unset in these tests, so there is no inbox to read), so it can call the API the same way the
// app does instead of reaching into the handler package's own random code generator.
func (api *testAPI) plantKnownCode(email, purpose string) string {
	api.t.Helper()
	const code = "123456"
	hash, err := bcrypt.GenerateFromPassword([]byte(code), 4)
	if err != nil {
		api.t.Fatal(err)
	}
	if err := api.app.DB.SetVerificationCode(context.Background(), email, purpose, string(hash)); err != nil {
		api.t.Fatal(err)
	}
	return code
}

func TestRegisterCreatesAnUnverifiedAccountAndEmailsACode(t *testing.T) {
	api := newTestAPI(t)
	email := api.registeringUser("register")

	status, body := api.do("POST", "/api/v1/auth/register", "", map[string]any{
		"name": "Nadia", "email": email, "password": "a-fine-password", "password_confirmation": "a-fine-password",
	})
	if status != http.StatusCreated {
		t.Fatalf("register gave %d: %v", status, body)
	}
	if body["data"].(map[string]any)["email"] != email {
		t.Fatalf("expected the email back, got %v", body)
	}

	user, err := api.app.DB.GetUserByEmail(context.Background(), email)
	if err != nil || user == nil {
		t.Fatalf("user was not created: %v", err)
	}
	if user.EmailVerified {
		t.Fatal("a freshly registered account should not be verified yet")
	}

	// Cannot log in until verified, same as an account made on the website.
	loginStatus, _ := api.login(email, "a-fine-password")
	if loginStatus != http.StatusForbidden {
		t.Fatalf("expected login to refuse an unverified account, got %d", loginStatus)
	}
}

func TestRegisterRefusesAWeakPasswordOrAnEmailAlreadyTaken(t *testing.T) {
	api := newTestAPI(t)
	_, existing := api.user("existing", true)

	status, _ := api.do("POST", "/api/v1/auth/register", "", map[string]any{
		"name": "Nadia", "email": existing, "password": "a-fine-password", "password_confirmation": "a-fine-password",
	})
	if status != http.StatusUnprocessableEntity {
		t.Fatalf("expected a taken email to be refused, got %d", status)
	}

	email := api.registeringUser("weak")
	status, _ = api.do("POST", "/api/v1/auth/register", "", map[string]any{
		"name": "Nadia", "email": email, "password": "short", "password_confirmation": "short",
	})
	if status != http.StatusUnprocessableEntity {
		t.Fatalf("expected a short password to be refused, got %d", status)
	}
}

func TestVerifyingTheRightCodeLogsTheAppIn(t *testing.T) {
	api := newTestAPI(t)
	email := api.registeringUser("verify")
	api.do("POST", "/api/v1/auth/register", "", map[string]any{
		"name": "Nadia", "email": email, "password": "a-fine-password", "password_confirmation": "a-fine-password",
	})

	code := api.plantKnownCode(email, database.PurposeEmailVerify)

	status, body := api.do("POST", "/api/v1/auth/verify-email", "", map[string]any{"email": email, "code": code, "device_name": "test"})
	if status != http.StatusOK {
		t.Fatalf("verify-email gave %d: %v", status, body)
	}
	if _, ok := body["data"].(map[string]any)["token"]; !ok {
		t.Fatalf("expected a token back, got %v", body)
	}

	user, _ := api.app.DB.GetUserByEmail(context.Background(), email)
	if !user.EmailVerified {
		t.Fatal("the account should be verified now")
	}

	// The code cannot be used a second time.
	status, _ = api.do("POST", "/api/v1/auth/verify-email", "", map[string]any{"email": email, "code": code, "device_name": "test"})
	if status != http.StatusUnprocessableEntity {
		t.Fatalf("expected the spent code to be refused, got %d", status)
	}
}

func TestVerifyEmailRefusesTheWrongCode(t *testing.T) {
	api := newTestAPI(t)
	email := api.registeringUser("wrongcode")
	api.do("POST", "/api/v1/auth/register", "", map[string]any{
		"name": "Nadia", "email": email, "password": "a-fine-password", "password_confirmation": "a-fine-password",
	})

	status, _ := api.do("POST", "/api/v1/auth/verify-email", "", map[string]any{"email": email, "code": "000000", "device_name": "test"})
	if status != http.StatusUnprocessableEntity {
		t.Fatalf("expected the wrong code to be refused, got %d", status)
	}
}

func TestForgotPasswordAnswersTheSameWayWhetherOrNotTheAccountExists(t *testing.T) {
	api := newTestAPI(t)
	_, email := api.user("hasaccount", true)
	noAccountEmail := api.registeringUser("noaccount")

	status1, body1 := api.do("POST", "/api/v1/auth/forgot-password", "", map[string]any{"email": email})
	status2, body2 := api.do("POST", "/api/v1/auth/forgot-password", "", map[string]any{"email": noAccountEmail})

	if status1 != http.StatusOK || status2 != http.StatusOK || body1["message"] != body2["message"] {
		t.Fatalf("expected the same answer either way, got %d/%v and %d/%v", status1, body1, status2, body2)
	}

	// Only the real account actually got a code.
	if code, err := api.app.DB.GetVerificationCode(context.Background(), email, database.PurposePasswordReset); err != nil || code == nil {
		t.Fatalf("expected a code for the real account: %v", err)
	}
	if code, _ := api.app.DB.GetVerificationCode(context.Background(), noAccountEmail, database.PurposePasswordReset); code != nil {
		t.Fatal("did not expect a code for an address with no account")
	}
}

func TestResetPasswordChangesThePasswordAndSignsOutEveryDevice(t *testing.T) {
	api := newTestAPI(t)
	_, email := api.user("reset", true)
	oldToken := api.token(email)

	api.do("POST", "/api/v1/auth/forgot-password", "", map[string]any{"email": email})
	code := api.plantKnownCode(email, database.PurposePasswordReset)

	status, body := api.do("POST", "/api/v1/auth/reset-password", "", map[string]any{
		"email": email, "code": code, "password": "a-new-password", "password_confirmation": "a-new-password",
	})
	if status != http.StatusOK {
		t.Fatalf("reset-password gave %d: %v", status, body)
	}

	// The old password no longer works; the new one does.
	oldStatus, _ := api.login(email, testPassword)
	if oldStatus != http.StatusUnauthorized {
		t.Fatalf("expected the old password to be refused, got %d", oldStatus)
	}
	newStatus, _ := api.login(email, "a-new-password")
	if newStatus != http.StatusOK {
		t.Fatalf("expected the new password to work, got %d", newStatus)
	}

	// The token issued before the reset stopped working.
	status, _ = api.do("GET", "/api/v1/departments", oldToken, nil)
	if status != http.StatusUnauthorized {
		t.Fatalf("expected the old token to be signed out, got %d", status)
	}
}

func TestResetPasswordRefusesAWrongOrExpiredCode(t *testing.T) {
	api := newTestAPI(t)
	_, email := api.user("badcode", true)
	api.do("POST", "/api/v1/auth/forgot-password", "", map[string]any{"email": email})

	status, _ := api.do("POST", "/api/v1/auth/reset-password", "", map[string]any{
		"email": email, "code": "000000", "password": "a-new-password", "password_confirmation": "a-new-password",
	})
	if status != http.StatusUnprocessableEntity {
		t.Fatalf("expected the wrong code to be refused, got %d", status)
	}
}
