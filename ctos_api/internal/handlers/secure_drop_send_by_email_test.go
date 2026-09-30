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

// sendSecureDropDirect posts a multipart secure drop the way the app's "send by email" screen
// does: not scoped to any department, with an optional password_recipient/password for someone
// with no account yet.
func (api *testAPI) sendSecureDropDirect(token string, recipients []map[string]any, passwordRecipient map[string]any, password string) (int, map[string]any) {
	api.t.Helper()

	var buffer bytes.Buffer
	writer := multipart.NewWriter(&buffer)
	writer.WriteField("name", "secret.txt")
	writer.WriteField("iv", "AAAAAAAAAAAAAAAA")
	recipientsJSON, _ := json.Marshal(recipients)
	writer.WriteField("recipients", string(recipientsJSON))
	if passwordRecipient != nil {
		recipientJSON, _ := json.Marshal(passwordRecipient)
		writer.WriteField("password_recipient", string(recipientJSON))
		writer.WriteField("password", password)
	}
	part, _ := writer.CreateFormFile("ciphertext", "ciphertext")
	part.Write([]byte("some ciphertext bytes"))
	writer.Close()

	request := httptest.NewRequest("POST", "/api/v1/secure-drops/send", &buffer)
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

func TestLookingUpAnAccountWithAKey(t *testing.T) {
	api := newTestAPI(t)
	_, senderEmail := api.user("lookupsender", true)
	senderToken := api.token(senderEmail)
	api.setUpSecureDropKey(senderToken)

	recipientID, recipientEmail := api.user("lookuprecipient", true)
	recipientToken := api.token(recipientEmail)
	api.setUpSecureDropKey(recipientToken)

	status, body := api.do("POST", "/api/v1/secure-drops/lookup", senderToken, map[string]any{"email": recipientEmail})
	if status != http.StatusOK {
		t.Fatalf("lookup gave %d: %v", status, body)
	}
	data := body["data"].(map[string]any)
	if data["has_account"] != true || int64(data["id"].(float64)) != recipientID {
		t.Fatalf("expected has_account and the right id, got %v", data)
	}
	if data["public_key"] == nil || data["fingerprint"] == nil {
		t.Fatalf("expected a public key and fingerprint, got %v", data)
	}
}

func TestLookingUpNoAccountOrNoKey(t *testing.T) {
	api := newTestAPI(t)
	_, senderEmail := api.user("lookupsender2", true)
	senderToken := api.token(senderEmail)
	api.setUpSecureDropKey(senderToken)

	status, body := api.do("POST", "/api/v1/secure-drops/lookup", senderToken, map[string]any{"email": "nobody@example.com"})
	if status != http.StatusOK || body["data"].(map[string]any)["has_account"] != false {
		t.Fatalf("expected has_account false, got %d %v", status, body)
	}

	_, noKeyEmail := api.user("lookupnokey", true)
	status, body = api.do("POST", "/api/v1/secure-drops/lookup", senderToken, map[string]any{"email": noKeyEmail})
	if status != http.StatusOK || body["data"].(map[string]any)["has_account"] != false {
		t.Fatalf("expected has_account false for a keyless account, got %d %v", status, body)
	}
}

func TestLookingUpYourOwnEmailIsRefused(t *testing.T) {
	api := newTestAPI(t)
	_, senderEmail := api.user("lookupself", true)
	senderToken := api.token(senderEmail)
	api.setUpSecureDropKey(senderToken)

	status, _ := api.do("POST", "/api/v1/secure-drops/lookup", senderToken, map[string]any{"email": senderEmail})
	if status != http.StatusUnprocessableEntity {
		t.Fatalf("expected 422, got %d", status)
	}
}

func TestSendingDirectlyToAnAccountByEmailHasNoDepartment(t *testing.T) {
	api := newTestAPI(t)
	senderID, senderEmail := api.user("directsender", true)
	senderToken := api.token(senderEmail)
	api.setUpSecureDropKey(senderToken)

	recipientID, recipientEmail := api.user("directrecipient", true)
	recipientToken := api.token(recipientEmail)
	api.setUpSecureDropKey(recipientToken)

	status, body := api.sendSecureDropDirect(senderToken, []map[string]any{
		{"user_id": senderID, "encrypted_key": "wrapped-for-sender"},
		{"user_id": recipientID, "encrypted_key": "wrapped-for-recipient"},
	}, nil, "")
	if status != http.StatusCreated {
		t.Fatalf("send gave %d: %v", status, body)
	}
	dropID := int64(body["data"].(map[string]any)["id"].(float64))

	status, showBody := api.do("GET", fmt.Sprintf("/api/v1/secure-drops/%d", dropID), recipientToken, nil)
	if status != http.StatusOK {
		t.Fatalf("show gave %d: %v", status, showBody)
	}
	data := showBody["data"].(map[string]any)
	if data["department_id"] != nil {
		t.Fatalf("expected a null department_id, got %v", data["department_id"])
	}
}

func TestSendingDirectlyRefusesARecipientWithNoKey(t *testing.T) {
	api := newTestAPI(t)
	senderID, senderEmail := api.user("directsender2", true)
	senderToken := api.token(senderEmail)
	api.setUpSecureDropKey(senderToken)

	noKeyID, _ := api.user("directnokey", true)

	status, _ := api.sendSecureDropDirect(senderToken, []map[string]any{
		{"user_id": senderID, "encrypted_key": "wrapped"},
		{"user_id": noKeyID, "encrypted_key": "wrapped"},
	}, nil, "")
	if status != http.StatusUnprocessableEntity {
		t.Fatalf("expected 422, got %d", status)
	}
}

func TestSendingDirectlyWithAPasswordForSomeoneWithNoAccount(t *testing.T) {
	api := newTestAPI(t)
	senderID, senderEmail := api.user("passwordsender", true)
	senderToken := api.token(senderEmail)
	api.setUpSecureDropKey(senderToken)

	status, body := api.sendSecureDropDirect(senderToken,
		[]map[string]any{{"user_id": senderID, "encrypted_key": "wrapped-for-sender"}},
		map[string]any{"email": "newperson@example.com", "encrypted_key": "password-wrapped-key"},
		"a-very-good-one-time-password",
	)
	if status != http.StatusCreated {
		t.Fatalf("send gave %d: %v", status, body)
	}
}

func TestSendingDirectlyRequiresThePasswordField(t *testing.T) {
	api := newTestAPI(t)
	senderID, senderEmail := api.user("passwordsender2", true)
	senderToken := api.token(senderEmail)
	api.setUpSecureDropKey(senderToken)

	status, _ := api.sendSecureDropDirect(senderToken,
		[]map[string]any{{"user_id": senderID, "encrypted_key": "wrapped-for-sender"}},
		map[string]any{"email": "newperson2@example.com", "encrypted_key": "password-wrapped-key"},
		"", // no password
	)
	if status != http.StatusUnprocessableEntity {
		t.Fatalf("expected 422, got %d", status)
	}
}

func TestDepartmentSecureDropRecipientsShowsEveryMember(t *testing.T) {
	api := newTestAPI(t)
	senderID, senderEmail := api.user("membersender", true)
	senderToken := api.token(senderEmail)
	api.setUpSecureDropKey(senderToken)

	noKeyID, _ := api.user("membernokey", true)

	orgID := api.organization(senderID)
	department := api.department(orgID, "Engineering")
	api.member(department.ID, senderID, "member", false)
	api.member(department.ID, noKeyID, "member", false)

	status, body := api.do("GET", "/api/v1/departments/"+department.Slug+"/secure-drop-recipients", senderToken, nil)
	if status != http.StatusOK {
		t.Fatalf("recipients gave %d: %v", status, body)
	}
	members := body["data"].([]any)
	if len(members) != 2 {
		t.Fatalf("expected 2 members, got %d", len(members))
	}
	byHasKey := map[bool]int{}
	for _, m := range members {
		byHasKey[m.(map[string]any)["has_key"].(bool)]++
	}
	if byHasKey[true] != 1 || byHasKey[false] != 1 {
		t.Fatalf("expected one keyed and one keyless member, got %v", byHasKey)
	}
}
