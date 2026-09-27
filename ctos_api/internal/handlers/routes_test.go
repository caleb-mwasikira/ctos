package handlers

import (
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"ctos_api/internal/config"
	"ctos_api/internal/realtime"
)

// These requests are all refused before the database is needed, so the App
// can be built without one.
func newTestApp() *App {
	return &App{Config: &config.Config{CORSAllowedOrigins: []string{"*"}}, Hub: realtime.NewHub()}
}

func TestHealthCheck(t *testing.T) {
	response := httptest.NewRecorder()
	newTestApp().Routes().ServeHTTP(response, httptest.NewRequest("GET", "/up", nil))

	if response.Code != http.StatusOK || response.Body.String() != "OK" {
		t.Errorf("/up gave %d %q", response.Code, response.Body.String())
	}
}

func TestRoutesBehindLoginRefuseAnonymousRequests(t *testing.T) {
	routes := newTestApp().Routes()

	for _, route := range [][2]string{
		{"POST", "/api/v1/auth/logout"},
		{"GET", "/api/v1/ws"},
		{"GET", "/api/v1/departments"},
		{"POST", "/api/v1/departments/join"},
	} {
		response := httptest.NewRecorder()
		routes.ServeHTTP(response, httptest.NewRequest(route[0], route[1], nil))
		if response.Code != http.StatusUnauthorized {
			t.Errorf("%s %s gave %d, want 401", route[0], route[1], response.Code)
		}
	}
}

func TestLoginChecksInputBeforeTouchingTheDatabase(t *testing.T) {
	routes := newTestApp().Routes()

	for body, wantField := range map[string]string{
		`{"email": "", "password": "x", "device_name": "laptop"}`:             "email",
		`{"email": "not-an-email", "password": "x", "device_name": "laptop"}`: "email",
		`{"email": "a@example.com", "password": "", "device_name": "laptop"}`: "password",
		`{"email": "a@example.com", "password": "x", "device_name": "  "}`:    "device_name",
	} {
		request := httptest.NewRequest("POST", "/api/v1/auth/login", strings.NewReader(body))
		response := httptest.NewRecorder()
		routes.ServeHTTP(response, request)

		if response.Code != http.StatusUnprocessableEntity {
			t.Errorf("%s gave %d, want 422", body, response.Code)
		}
		if !strings.Contains(response.Body.String(), `"`+wantField+`":[`) {
			t.Errorf("%s: the error should be about %q, got %s", body, wantField, response.Body.String())
		}
	}

	request := httptest.NewRequest("POST", "/api/v1/auth/login", strings.NewReader("not json"))
	response := httptest.NewRecorder()
	routes.ServeHTTP(response, request)
	if response.Code != http.StatusBadRequest {
		t.Errorf("invalid JSON gave %d, want 400", response.Code)
	}
}
