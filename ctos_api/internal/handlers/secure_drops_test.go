package handlers

import (
	"bytes"
	"encoding/json"
	"fmt"
	"mime/multipart"
	"net/http"
	"net/http/httptest"
	"testing"
)

// setUpSecureDropKey runs the full challenge/proof protocol for token's user, so it has a real
// key on record (its content does not matter for these tests, only that one exists).
func (api *testAPI) setUpSecureDropKey(token string) {
	api.t.Helper()
	publicPEM, private := makeSecureDropKeyPair(api.t)

	status, body := api.do("POST", "/api/v1/encryption-keys/challenge", token, map[string]any{"public_key": publicPEM})
	if status != http.StatusOK {
		api.t.Fatalf("challenge gave %d: %v", status, body)
	}
	proof := decryptSecureDropChallenge(api.t, private, body["data"].(map[string]any)["ciphertext"].(string))

	status, body = api.do("POST", "/api/v1/encryption-keys", token, map[string]any{"proof": proof, "encrypted_private_key": makeSecureDropEnvelope(api.t)})
	if status != http.StatusOK {
		api.t.Fatalf("store key gave %d: %v", status, body)
	}
}

// sendSecureDrop posts a multipart secure drop the way the app does: a file and a JSON array of recipients.
func (api *testAPI) sendSecureDrop(token, departmentSlug, name string, recipients []map[string]any) (int, map[string]any) {
	api.t.Helper()

	var buffer bytes.Buffer
	writer := multipart.NewWriter(&buffer)
	writer.WriteField("name", name)
	writer.WriteField("iv", "AAAAAAAAAAAAAAAA")
	recipientsJSON, _ := json.Marshal(recipients)
	writer.WriteField("recipients", string(recipientsJSON))
	part, _ := writer.CreateFormFile("ciphertext", "ciphertext")
	part.Write([]byte("some ciphertext bytes"))
	writer.Close()

	request := httptest.NewRequest("POST", "/api/v1/departments/"+departmentSlug+"/secure-drop", &buffer)
	request.Header.Set("Content-Type", writer.FormDataContentType())
	if token != "" {
		request.Header.Set("Authorization", "Bearer "+token)
	}
	response := httptest.NewRecorder()
	api.routes.ServeHTTP(response, request)

	var decoded map[string]any
	json.Unmarshal(response.Body.Bytes(), &decoded)
	return response.Code, decoded
}

func TestSendingASecureDropToEveryRecipientIncludingTheSender(t *testing.T) {
	api := newTestAPI(t)
	senderID, senderEmail := api.user("sender", true)
	senderToken := api.token(senderEmail)
	api.setUpSecureDropKey(senderToken)

	recipientID, recipientEmail := api.user("recipient", true)
	recipientToken := api.token(recipientEmail)
	api.setUpSecureDropKey(recipientToken)

	orgID := api.organization(senderID)
	department := api.department(orgID, "Engineering")
	api.member(department.ID, senderID, "member", false)
	api.member(department.ID, recipientID, "member", false)

	status, body := api.sendSecureDrop(senderToken, department.Slug, "secret.txt", []map[string]any{
		{"user_id": senderID, "encrypted_key": "wrapped-for-sender"},
		{"user_id": recipientID, "encrypted_key": "wrapped-for-recipient"},
	})
	if status != http.StatusCreated {
		t.Fatalf("send gave %d: %v", status, body)
	}
	dropID := int64(body["data"].(map[string]any)["id"].(float64))

	status, listBody := api.do("GET", "/api/v1/secure-drops", senderToken, nil)
	if status != http.StatusOK {
		t.Fatalf("list gave %d", status)
	}
	sent := listBody["data"].(map[string]any)["sent"].([]any)
	if len(sent) != 1 {
		t.Fatalf("expected 1 sent drop, got %d", len(sent))
	}

	status, receivedBody := api.do("GET", "/api/v1/secure-drops", recipientToken, nil)
	if status != http.StatusOK {
		t.Fatalf("list gave %d", status)
	}
	received := receivedBody["data"].(map[string]any)["received"].([]any)
	if len(received) != 1 {
		t.Fatalf("expected 1 received drop, got %d", len(received))
	}

	status, showBody := api.do("GET", fmt.Sprintf("/api/v1/secure-drops/%d", dropID), recipientToken, nil)
	if status != http.StatusOK || showBody["data"].(map[string]any)["encrypted_key"] != "wrapped-for-recipient" {
		t.Fatalf("expected the recipient's own wrapped key, got %d %v", status, showBody)
	}

	status, download := api.doRaw("GET", fmt.Sprintf("/api/v1/secure-drops/%d/download", dropID), recipientToken)
	if status != http.StatusOK || download != "some ciphertext bytes" {
		t.Fatalf("expected the ciphertext back, got %d %q", status, download)
	}
}

func TestSendingToSomeoneWithNoKeyIsRefused(t *testing.T) {
	api := newTestAPI(t)
	senderID, senderEmail := api.user("sender2", true)
	senderToken := api.token(senderEmail)
	api.setUpSecureDropKey(senderToken)

	noKeyID, _ := api.user("nokey", true)

	orgID := api.organization(senderID)
	department := api.department(orgID, "Engineering")
	api.member(department.ID, senderID, "member", false)
	api.member(department.ID, noKeyID, "member", false)

	status, _ := api.sendSecureDrop(senderToken, department.Slug, "secret.txt", []map[string]any{
		{"user_id": noKeyID, "encrypted_key": "wrapped"},
	})
	if status != http.StatusUnprocessableEntity {
		t.Fatalf("expected 422, got %d", status)
	}
}

func TestSendingRequiresTheSendersOwnKey(t *testing.T) {
	api := newTestAPI(t)
	senderID, senderEmail := api.user("nokeysender", true)
	senderToken := api.token(senderEmail)
	// No setUpSecureDropKey call: this sender has no key of their own.

	orgID := api.organization(senderID)
	department := api.department(orgID, "Engineering")
	api.member(department.ID, senderID, "member", false)

	status, _ := api.sendSecureDrop(senderToken, department.Slug, "secret.txt", []map[string]any{
		{"user_id": senderID, "encrypted_key": "wrapped"},
	})
	if status != http.StatusForbidden {
		t.Fatalf("expected 403, got %d", status)
	}
}

func TestOnlyTheOwnerAndRecipientsCanOpenADrop(t *testing.T) {
	api := newTestAPI(t)
	senderID, senderEmail := api.user("sender3", true)
	senderToken := api.token(senderEmail)
	api.setUpSecureDropKey(senderToken)

	_, outsiderEmail := api.user("outsider", true)
	outsiderToken := api.token(outsiderEmail)

	orgID := api.organization(senderID)
	department := api.department(orgID, "Engineering")
	api.member(department.ID, senderID, "member", false)

	_, body := api.sendSecureDrop(senderToken, department.Slug, "secret.txt", []map[string]any{
		{"user_id": senderID, "encrypted_key": "wrapped"},
	})
	dropID := int64(body["data"].(map[string]any)["id"].(float64))

	status, _ := api.do("GET", fmt.Sprintf("/api/v1/secure-drops/%d", dropID), outsiderToken, nil)
	if status != http.StatusNotFound {
		t.Fatalf("expected an outsider to get 404, got %d", status)
	}
}

func TestOnlyTheOwnerCanDeleteADrop(t *testing.T) {
	api := newTestAPI(t)
	senderID, senderEmail := api.user("sender4", true)
	senderToken := api.token(senderEmail)
	api.setUpSecureDropKey(senderToken)

	recipientID, recipientEmail := api.user("recipient4", true)
	recipientToken := api.token(recipientEmail)
	api.setUpSecureDropKey(recipientToken)

	orgID := api.organization(senderID)
	department := api.department(orgID, "Engineering")
	api.member(department.ID, senderID, "member", false)
	api.member(department.ID, recipientID, "member", false)

	_, body := api.sendSecureDrop(senderToken, department.Slug, "secret.txt", []map[string]any{
		{"user_id": senderID, "encrypted_key": "a"},
		{"user_id": recipientID, "encrypted_key": "b"},
	})
	dropID := int64(body["data"].(map[string]any)["id"].(float64))

	status, _ := api.do("DELETE", fmt.Sprintf("/api/v1/secure-drops/%d", dropID), recipientToken, nil)
	if status != http.StatusForbidden {
		t.Fatalf("expected the recipient to be refused, got %d", status)
	}

	status, _ = api.do("DELETE", fmt.Sprintf("/api/v1/secure-drops/%d", dropID), senderToken, nil)
	if status != http.StatusOK {
		t.Fatalf("expected the owner to delete it, got %d", status)
	}
}
