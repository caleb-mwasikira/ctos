package handlers

import (
	"context"
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"os"
	"strings"
	"testing"

	"github.com/jackc/pgx/v5/pgxpool"
	"golang.org/x/crypto/bcrypt"

	"ctos_api/internal/config"
	"ctos_api/internal/database"
	"ctos_api/internal/realtime"
	"ctos_api/internal/storage"
)

// These tests run the whole API against a real Postgres database that has the Laravel schema
// (php artisan migrate). Point TEST_DATABASE_URL at a scratch database, for example:
//
//	TEST_DATABASE_URL=postgres://postgres:secret@127.0.0.1:5432/ctos_test go test ./...
//
// Without it they are skipped. Every row a test creates is deleted again when it ends.

const testPassword = "secret-password"
const testAccessCode = "ABCDE23456"

const testAppKey = "base64:s6vDWPFguM12fwZXelMk4+MPfwnXm6BkKpnyCu55JLE="

type testAPI struct {
	t        *testing.T
	app      *App
	filesDir string
	routes   http.Handler
	pool     *pgxpool.Pool
	suffix   string
	count    int
}

func newTestAPI(t *testing.T) *testAPI {
	t.Helper()

	url := os.Getenv("TEST_DATABASE_URL")
	if url == "" {
		t.Skip("TEST_DATABASE_URL is not set")
	}
	db, err := database.Connect(context.Background(), url)
	if err != nil {
		t.Fatalf("connect: %v", err)
	}
	t.Cleanup(db.Close)

	filesDir := t.TempDir()
	files, err := storage.New(filesDir)
	if err != nil {
		t.Fatal(err)
	}
	app := &App{
		DB:     db,
		Config: &config.Config{CORSAllowedOrigins: []string{"*"}, MaxUploadBytes: 10 * 1024 * 1024, AppKey: testAppKey},
		Hub:    realtime.NewHub(),
		Files:  files,
	}
	random := make([]byte, 4)
	rand.Read(random)

	return &testAPI{t: t, app: app, filesDir: filesDir, routes: app.Routes(), pool: db.Pool, suffix: hex.EncodeToString(random)}
}

func (api *testAPI) exec(sql string, args ...any) {
	api.t.Helper()
	if _, err := api.pool.Exec(context.Background(), sql, args...); err != nil {
		api.t.Fatalf("%s: %v", sql, err)
	}
}

func (api *testAPI) insertID(sql string, args ...any) int64 {
	api.t.Helper()
	var id int64
	if err := api.pool.QueryRow(context.Background(), sql, args...).Scan(&id); err != nil {
		api.t.Fatalf("%s: %v", sql, err)
	}
	return id
}

// user creates an account, deleted again (with everything it owns) at the end of the test.
func (api *testAPI) user(name string, verified bool) (int64, string) {
	api.t.Helper()
	api.count++
	email := fmt.Sprintf("%s-%d-%s@example.com", name, api.count, api.suffix)
	hash, _ := bcrypt.GenerateFromPassword([]byte(testPassword), 4)

	id := api.insertID(`
		insert into users (name, email, password, email_verified_at, created_at, updated_at)
		values ($1, $2, $3, case when $4 then now() else null end, now(), now()) returning id`,
		name, email, string(hash), verified)
	api.t.Cleanup(func() {
		api.exec(`delete from personal_access_tokens where tokenable_type = 'App\Models\User' and tokenable_id = $1`, id)
		api.exec(`delete from users where id = $1`, id)
	})
	return id, email
}

func (api *testAPI) organization(ownerID int64) int64 {
	api.t.Helper()
	api.count++
	return api.insertID(`
		insert into organizations (name, slug, owner_id, created_at, updated_at)
		values ('Org', $1, $2, now(), now()) returning id`,
		fmt.Sprintf("org-%d-%s", api.count, api.suffix), ownerID)
}

type testDepartment struct {
	ID   int64
	Slug string
	// Code is unique per department: access_code is looked up by value now (see
	// GetDepartmentByAccessCodeHash), so two departments can no longer share testAccessCode the
	// way they could when it was bcrypt'd and checked one department at a time.
	Code string
}

func (api *testAPI) department(organizationID int64, name string) testDepartment {
	api.t.Helper()
	api.count++
	slug := fmt.Sprintf("dept-%d-%s", api.count, api.suffix)
	code := fmt.Sprintf("%s%d%s", testAccessCode, api.count, api.suffix)
	id := api.insertID(`
		insert into departments (organization_id, name, slug, access_code, created_at, updated_at)
		values ($1, $2, $3, $4, now(), now()) returning id`, organizationID, name, slug, hashAccessCode(code))
	return testDepartment{ID: id, Slug: slug, Code: code}
}

func (api *testAPI) member(departmentID int64, userID int64, role string, locked bool) {
	api.t.Helper()
	api.exec(`
		insert into department_user (department_id, user_id, role, locked_at, created_at, updated_at)
		values ($1, $2, $3, case when $4 then now() else null end, now(), now())`, departmentID, userID, role, locked)
}

func (api *testAPI) lockOutOfOrganization(organizationID int64, userID int64) {
	api.t.Helper()
	api.exec(`insert into organization_locks (organization_id, user_id, created_at, updated_at) values ($1, $2, now(), now())`, organizationID, userID)
}

// do makes a request and returns the status and the JSON body (nil when there is none).
func (api *testAPI) do(method string, path string, token string, body any) (int, map[string]any) {
	api.t.Helper()
	var payload strings.Builder
	if body != nil {
		json.NewEncoder(&payload).Encode(body)
	}
	request := httptest.NewRequest(method, path, strings.NewReader(payload.String()))
	request.Header.Set("Content-Type", "application/json")
	if token != "" {
		request.Header.Set("Authorization", "Bearer "+token)
	}
	response := httptest.NewRecorder()
	api.routes.ServeHTTP(response, request)

	var decoded map[string]any
	json.Unmarshal(response.Body.Bytes(), &decoded)
	return response.Code, decoded
}

// doRaw is do, for an answer that is not JSON (a downloaded file).
func (api *testAPI) doRaw(method string, path string, token string) (int, string) {
	api.t.Helper()
	request := httptest.NewRequest(method, path, nil)
	if token != "" {
		request.Header.Set("Authorization", "Bearer "+token)
	}
	response := httptest.NewRecorder()
	api.routes.ServeHTTP(response, request)
	return response.Code, response.Body.String()
}

// login signs in and returns the status and, when it worked, the token.
func (api *testAPI) login(email string, password string) (int, string) {
	api.t.Helper()
	status, body := api.do("POST", "/api/v1/auth/login", "", map[string]any{"email": email, "password": password, "device_name": "test"})
	if status != http.StatusOK {
		return status, ""
	}
	return status, body["data"].(map[string]any)["token"].(string)
}

func (api *testAPI) token(email string) string {
	api.t.Helper()
	status, token := api.login(email, testPassword)
	if status != http.StatusOK {
		api.t.Fatalf("login as %s gave %d", email, status)
	}
	return token
}

// departmentSlugs lists the slugs of the departments the token's user can see.
func (api *testAPI) departmentSlugs(token string) []string {
	api.t.Helper()
	status, body := api.do("GET", "/api/v1/departments", token, nil)
	if status != http.StatusOK {
		api.t.Fatalf("list departments gave %d", status)
	}
	slugs := []string{}
	for _, item := range body["data"].([]any) {
		slugs = append(slugs, item.(map[string]any)["slug"].(string))
	}
	return slugs
}

func contains(list []string, want string) bool {
	for _, item := range list {
		if item == want {
			return true
		}
	}
	return false
}

// ── login and logout ───────────────────────────────────────────────────────

func TestLoginIssuesATokenThatWorksUntilLogout(t *testing.T) {
	api := newTestAPI(t)
	_, email := api.user("alice", true)

	status, body := api.do("POST", "/api/v1/auth/login", "", map[string]any{"email": strings.ToUpper(email), "password": testPassword, "device_name": "Alice laptop"})
	if status != http.StatusOK {
		t.Fatalf("login gave %d", status)
	}
	data := body["data"].(map[string]any)
	token := data["token"].(string)
	if !strings.Contains(token, "|") || data["user"].(map[string]any)["email"] != email {
		t.Errorf("unexpected login answer: %v", body)
	}

	if status, _ := api.do("GET", "/api/v1/departments", token, nil); status != http.StatusOK {
		t.Errorf("the token should work, got %d", status)
	}
	if status, _ := api.do("POST", "/api/v1/auth/logout", token, nil); status != http.StatusOK {
		t.Errorf("logout gave %d", status)
	}
	if status, _ := api.do("GET", "/api/v1/departments", token, nil); status != http.StatusUnauthorized {
		t.Errorf("a token must stop working after logout, got %d", status)
	}
}

func TestLoginRefusesWrongPasswordsAndUnverifiedEmails(t *testing.T) {
	api := newTestAPI(t)
	_, verified := api.user("alice", true)
	unverifiedID, unverified := api.user("dave", false)

	if status, _ := api.login(verified, "wrong"); status != http.StatusUnauthorized {
		t.Errorf("wrong password gave %d, want 401", status)
	}
	if status, _ := api.login("nobody-"+api.suffix+"@example.com", testPassword); status != http.StatusUnauthorized {
		t.Errorf("unknown email gave %d, want 401", status)
	}
	if status, _ := api.login(unverified, testPassword); status != http.StatusForbidden {
		t.Errorf("unverified email gave %d, want 403", status)
	}

	var tokens int
	api.pool.QueryRow(context.Background(), `select count(*) from personal_access_tokens where tokenable_id = $1`, unverifiedID).Scan(&tokens)
	if tokens != 0 {
		t.Errorf("no token may be issued to an unverified user, found %d", tokens)
	}
}

func TestLoginLocksAfterFiveWrongPasswords(t *testing.T) {
	api := newTestAPI(t)
	_, email := api.user("alice", true)

	for attempt := 1; attempt <= 5; attempt++ {
		if status, _ := api.login(email, "wrong"); status != http.StatusUnauthorized {
			t.Fatalf("attempt %d gave %d, want 401", attempt, status)
		}
	}
	if status, _ := api.login(email, testPassword); status != http.StatusTooManyRequests {
		t.Errorf("the account should be locked even for the right password, got %d", status)
	}
}

func TestATokenMadeByLaravelWorks(t *testing.T) {
	api := newTestAPI(t)
	userID, _ := api.user("alice", true)

	secret := "laravelissuedsecret" + api.suffix
	hash := sha256.Sum256([]byte(secret))
	tokenID := api.insertID(`
		insert into personal_access_tokens (tokenable_type, tokenable_id, name, token, abilities, created_at, updated_at)
		values ('App\Models\User', $1, 'laravel', $2, '["*"]', now(), now()) returning id`, userID, hex.EncodeToString(hash[:]))

	if status, _ := api.do("GET", "/api/v1/departments", fmt.Sprintf("%d|%s", tokenID, secret), nil); status != http.StatusOK {
		t.Errorf("a Sanctum token should work, got %d", status)
	}
	if status, _ := api.do("GET", "/api/v1/departments", fmt.Sprintf("%d|wrong%s", tokenID, secret), nil); status != http.StatusUnauthorized {
		t.Errorf("a wrong secret should be refused, got %d", status)
	}
}

// ── departments ────────────────────────────────────────────────────────────

func TestDepartmentsListsOnlyTheUsersOwn(t *testing.T) {
	api := newTestAPI(t)
	ownerID, _ := api.user("owner", true)
	aliceID, email := api.user("alice", true)
	organization := api.organization(ownerID)
	mine := api.department(organization, "Engineering")
	other := api.department(organization, "Secret")
	api.member(mine.ID, aliceID, "lead", false)

	token := api.token(email)
	_, body := api.do("GET", "/api/v1/departments", token, nil)
	list := body["data"].([]any)
	if len(list) != 1 {
		t.Fatalf("want 1 department, got %d: %v", len(list), list)
	}
	item := list[0].(map[string]any)
	if item["slug"] != mine.Slug || item["role"] != "lead" || item["organization"].(map[string]any)["name"] != "Org" {
		t.Errorf("unexpected department: %v", item)
	}
	if contains(api.departmentSlugs(token), other.Slug) {
		t.Error("a department the user has not joined must not be listed")
	}
	if _, leaked := item["access_code"]; leaked {
		t.Error("the access code must never be in the answer")
	}
}

func TestLockedMembershipsAreHidden(t *testing.T) {
	api := newTestAPI(t)
	ownerID, _ := api.user("owner", true)
	aliceID, email := api.user("alice", true)

	organization := api.organization(ownerID)
	lockedHere := api.department(organization, "Locked here")
	open := api.department(organization, "Open")
	api.member(lockedHere.ID, aliceID, "member", true)
	api.member(open.ID, aliceID, "member", false)

	lockedOrganization := api.organization(ownerID)
	insideLockedOrganization := api.department(lockedOrganization, "Inside a locked organization")
	api.member(insideLockedOrganization.ID, aliceID, "member", false)
	api.lockOutOfOrganization(lockedOrganization, aliceID)

	slugs := api.departmentSlugs(api.token(email))
	if len(slugs) != 1 || slugs[0] != open.Slug {
		t.Errorf("only the open department should be listed, got %v", slugs)
	}
}

// ── joining ────────────────────────────────────────────────────────────────

func join(api *testAPI, token string, code string) (int, map[string]any) {
	return api.do("POST", "/api/v1/departments/join", token, map[string]any{"code": code})
}

func TestJoinNeedsOnlyTheCode(t *testing.T) {
	api := newTestAPI(t)
	ownerID, _ := api.user("owner", true)
	_, email := api.user("bob", true)
	department := api.department(api.organization(ownerID), "Engineering")
	token := api.token(email)

	status, body := join(api, token, " "+strings.ToLower(department.Code)+" ")
	if status != http.StatusCreated {
		t.Fatalf("join gave %d: %v", status, body)
	}
	data := body["data"].(map[string]any)
	if data["slug"] != department.Slug || data["role"] != "member" {
		t.Errorf("unexpected answer: %v", data)
	}
	if !contains(api.departmentSlugs(token), department.Slug) {
		t.Error("the joined department should now be listed")
	}
}

func TestJoinGivesTheSameAnswerForAWrongCodeAndOneNobodyHas(t *testing.T) {
	api := newTestAPI(t)
	ownerID, _ := api.user("owner", true)
	_, email := api.user("bob", true)
	api.department(api.organization(ownerID), "Engineering")
	token := api.token(email)

	wrongCodeStatus, wrongCode := join(api, token, "WRONGWRONG")
	unknownCodeStatus, unknownCode := join(api, token, "NOSUCHCODE1")

	if wrongCodeStatus != http.StatusUnprocessableEntity || unknownCodeStatus != http.StatusUnprocessableEntity {
		t.Fatalf("both should be 422, got %d and %d", wrongCodeStatus, unknownCodeStatus)
	}
	if wrongCode["message"] != unknownCode["message"] || wrongCode["message"] != "That access code is incorrect." {
		t.Errorf("the answers must be identical, got %q and %q", wrongCode["message"], unknownCode["message"])
	}
}

func TestJoinKeepsAnExistingRole(t *testing.T) {
	api := newTestAPI(t)
	ownerID, ownerEmail := api.user("owner", true)
	department := api.department(api.organization(ownerID), "Engineering")
	api.member(department.ID, ownerID, "admin", false)

	status, body := join(api, api.token(ownerEmail), department.Code)
	if status != http.StatusCreated || body["data"].(map[string]any)["role"] != "admin" {
		t.Errorf("an admin joining again must stay an admin, got %d %v", status, body)
	}
}

func TestJoinLocksAfterFiveWrongCodes(t *testing.T) {
	api := newTestAPI(t)
	ownerID, _ := api.user("owner", true)
	_, email := api.user("bob", true)
	department := api.department(api.organization(ownerID), "Engineering")
	token := api.token(email)

	for attempt := 1; attempt <= 5; attempt++ {
		if status, _ := join(api, token, "WRONGWRONG"); status != http.StatusUnprocessableEntity {
			t.Fatalf("attempt %d gave %d, want 422", attempt, status)
		}
	}
	if status, _ := join(api, token, department.Code); status != http.StatusTooManyRequests {
		t.Errorf("further tries must be refused, even with the right code, got %d", status)
	}
}

func TestLockedMembersCannotJoinTheirWayBackIn(t *testing.T) {
	for _, kind := range []string{"department", "organization"} {
		t.Run(kind, func(t *testing.T) {
			api := newTestAPI(t)
			ownerID, _ := api.user("owner", true)
			bobID, email := api.user("bob", true)
			organization := api.organization(ownerID)
			department := api.department(organization, "Engineering")
			api.member(department.ID, bobID, "member", kind == "department")
			if kind == "organization" {
				api.lockOutOfOrganization(organization, bobID)
			}
			token := api.token(email)

			status, body := join(api, token, department.Code)
			if status != http.StatusUnprocessableEntity || !strings.Contains(fmt.Sprint(body["message"]), "locked") {
				t.Errorf("want 422 with a message about the lock, got %d %v", status, body)
			}
			if contains(api.departmentSlugs(token), department.Slug) {
				t.Error("joining must not undo the lock")
			}
		})
	}
}
