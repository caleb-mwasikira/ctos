package handlers

import (
	"context"
	"fmt"
	"net/http"
	"testing"
)

// Database-backed tests for sharing a department by email. No MAIL_HOST is set in these tests
// (see newTestAPI), so every share here exercises the "mail disabled" path — the row is still
// written and the answer is unchanged, the same way an unset APP_KEY still lets the rest of the
// API work.

func (api *testAPI) invite(token string, department testDepartment, email string) (int, map[string]any) {
	api.t.Helper()
	return api.do("POST", "/api/v1/departments/"+department.Slug+"/invitations", token, map[string]any{"email": email})
}

func (api *testAPI) withdraw(token string, department testDepartment, invitationID int64) (int, map[string]any) {
	api.t.Helper()
	return api.do("DELETE", fmt.Sprintf("/api/v1/departments/%s/invitations/%d", department.Slug, invitationID), token, nil)
}

// invitations returns the department's pending invitations, as a manager sees them.
func (api *testAPI) invitations(token string, department testDepartment) []map[string]any {
	api.t.Helper()
	status, body := api.do("GET", "/api/v1/departments/"+department.Slug, token, nil)
	if status != http.StatusOK {
		api.t.Fatalf("show department gave %d", status)
	}
	list := []map[string]any{}
	for _, item := range body["data"].(map[string]any)["invitations"].([]any) {
		list = append(list, item.(map[string]any))
	}
	return list
}

func (api *testAPI) roleOf(departmentID int64, userID int64) string {
	api.t.Helper()
	var role string
	api.pool.QueryRow(context.Background(), `select role from department_user where department_id = $1 and user_id = $2`, departmentID, userID).Scan(&role)
	return role
}

// shareSetup makes a department with an admin (who can share) and a plain member (who cannot).
func (api *testAPI) shareSetup() (department testDepartment, admin string, member string) {
	api.t.Helper()
	adminID, adminEmail := api.user("admin", true)
	department = api.department(api.organization(adminID), "Engineering")
	api.member(department.ID, adminID, "admin", false)
	memberID, memberEmail := api.user("member", true)
	api.member(department.ID, memberID, "member", false)
	return department, api.token(adminEmail), api.token(memberEmail)
}

func TestSharingWithAVerifiedAccountAddsThemStraightAway(t *testing.T) {
	api := newTestAPI(t)
	department, admin, _ := api.shareSetup()
	aliceID, aliceEmail := api.user("alice", true)

	status, body := api.invite(admin, department, aliceEmail)

	if status != http.StatusOK || body["data"].(map[string]any)["added"] != true {
		t.Fatalf("gave %d %v", status, body)
	}
	if role := api.roleOf(department.ID, aliceID); role != "member" {
		t.Errorf("alice's role = %q, want member", role)
	}
	if invitations := api.invitations(admin, department); len(invitations) != 0 {
		t.Errorf("a direct add should not also leave an invitation, got %v", invitations)
	}
}

func TestSharingWithNoAccountYetCreatesAnInvitation(t *testing.T) {
	api := newTestAPI(t)
	department, admin, _ := api.shareSetup()
	email := "future-hire@example.com"

	status, body := api.invite(admin, department, email)

	if status != http.StatusOK || body["data"].(map[string]any)["added"] != false {
		t.Fatalf("gave %d %v", status, body)
	}
	invitations := api.invitations(admin, department)
	if len(invitations) != 1 || invitations[0]["email"] != email {
		t.Fatalf("want one invitation for %s, got %v", email, invitations)
	}

	// inviting the same address again does not duplicate the invitation
	again, _ := api.invite(admin, department, email)
	if again != http.StatusOK {
		t.Errorf("repeating an invite gave %d, want 200", again)
	}
	if invitations := api.invitations(admin, department); len(invitations) != 1 {
		t.Errorf("still want exactly one invitation, got %v", invitations)
	}
}

func TestSharingWithAnUnverifiedAccountCreatesAnInvitationToo(t *testing.T) {
	api := newTestAPI(t)
	department, admin, _ := api.shareSetup()
	_, email := api.user("unverified", false)

	status, body := api.invite(admin, department, email)

	if status != http.StatusOK || body["data"].(map[string]any)["added"] != false {
		t.Fatalf("gave %d %v", status, body)
	}
	if invitations := api.invitations(admin, department); len(invitations) != 1 {
		t.Errorf("want an invitation for the unverified address, got %v", invitations)
	}
}

func TestSharingRefusesSomeoneAlreadyAMember(t *testing.T) {
	api := newTestAPI(t)
	department, admin, _ := api.shareSetup()
	existingID, existingEmail := api.user("existing", true)
	api.member(department.ID, existingID, "member", false)

	status, body := api.invite(admin, department, existingEmail)

	if status != http.StatusUnprocessableEntity {
		t.Fatalf("gave %d, want 422: %v", status, body)
	}
	if errs, _ := body["errors"].(map[string]any); errs == nil || errs["email"] == nil {
		t.Errorf("want an email error, got %v", body)
	}
}

func TestSharingRefusesSomeoneLockedOutOfTheDepartment(t *testing.T) {
	api := newTestAPI(t)
	department, admin, _ := api.shareSetup()
	lockedID, lockedEmail := api.user("locked", true)
	api.member(department.ID, lockedID, "member", true)

	status, body := api.invite(admin, department, lockedEmail)

	if status != http.StatusUnprocessableEntity {
		t.Fatalf("gave %d, want 422: %v", status, body)
	}
	if errs, _ := body["errors"].(map[string]any); errs == nil || errs["email"] == nil {
		t.Errorf("want an email error, got %v", body)
	}
}

func TestOnlyManagersCanShareOrWithdraw(t *testing.T) {
	api := newTestAPI(t)
	department, admin, member := api.shareSetup()
	_, targetEmail := api.user("target", true)
	api.invite(admin, department, "someone-else@example.com")
	invitationID := int64(api.invitations(admin, department)[0]["id"].(float64))

	if status, _ := api.invite(member, department, targetEmail); status != http.StatusNotFound {
		t.Errorf("a plain member sharing gave %d, want 404", status)
	}
	if status, _ := api.withdraw(member, department, invitationID); status != http.StatusNotFound {
		t.Errorf("a plain member withdrawing gave %d, want 404", status)
	}
}

func TestSharingValidatesTheEmailField(t *testing.T) {
	api := newTestAPI(t)
	department, admin, _ := api.shareSetup()

	status, body := api.invite(admin, department, "not-an-email")

	if status != http.StatusUnprocessableEntity {
		t.Fatalf("gave %d, want 422: %v", status, body)
	}
}

func TestSharingIsThrottledPerHour(t *testing.T) {
	api := newTestAPI(t)
	department, admin, _ := api.shareSetup()

	for i := 0; i < shareAttemptsPerHour; i++ {
		status, body := api.invite(admin, department, fmt.Sprintf("throttle-%d-%s@example.com", i, api.suffix))
		if status != http.StatusOK {
			t.Fatalf("invite %d gave %d: %v", i, status, body)
		}
	}
	status, _ := api.invite(admin, department, "one-too-many@example.com")
	if status != http.StatusTooManyRequests {
		t.Errorf("the invite after the limit gave %d, want 429", status)
	}
}

func TestWithdrawingAnInvitationRemovesIt(t *testing.T) {
	api := newTestAPI(t)
	department, admin, _ := api.shareSetup()
	api.invite(admin, department, "withdraw-me@example.com")
	invitationID := int64(api.invitations(admin, department)[0]["id"].(float64))

	status, _ := api.withdraw(admin, department, invitationID)

	if status != http.StatusOK {
		t.Fatalf("gave %d", status)
	}
	if invitations := api.invitations(admin, department); len(invitations) != 0 {
		t.Errorf("want the invitation gone, got %v", invitations)
	}
	if status, _ := api.withdraw(admin, department, invitationID); status != http.StatusNotFound {
		t.Errorf("withdrawing again gave %d, want 404", status)
	}
}

func TestShareEndpointsNeedALogin(t *testing.T) {
	api := newTestAPI(t)
	department, _, _ := api.shareSetup()

	if status, _ := api.invite("", department, "someone@example.com"); status != http.StatusUnauthorized {
		t.Errorf("invite without a token gave %d, want 401", status)
	}
	if status, _ := api.withdraw("", department, 1); status != http.StatusUnauthorized {
		t.Errorf("withdraw without a token gave %d, want 401", status)
	}
}

// ── organizations ────────────────────────────────────────────────────────────

func TestCreatingAnOrganizationMakesItsOwnerWithAUniqueSlug(t *testing.T) {
	api := newTestAPI(t)
	_, email := api.user("founder", true)
	token := api.token(email)

	status, body := api.do("POST", "/api/v1/organizations", token, map[string]any{"name": "Acme Corp"})
	if status != http.StatusCreated {
		t.Fatalf("gave %d: %v", status, body)
	}
	first := body["data"].(map[string]any)
	if first["name"] != "Acme Corp" || first["is_owner"] != true || first["department_count"] != float64(0) {
		t.Errorf("unexpected organization: %v", first)
	}
	firstSlug, _ := first["slug"].(string)
	if firstSlug == "" {
		t.Fatal("want a slug")
	}

	// a second organization with the same name still gets a slug of its own
	status, body = api.do("POST", "/api/v1/organizations", token, map[string]any{"name": "Acme Corp"})
	if status != http.StatusCreated {
		t.Fatalf("gave %d: %v", status, body)
	}
	secondSlug := body["data"].(map[string]any)["slug"].(string)
	if secondSlug == firstSlug {
		t.Errorf("the two organizations share a slug: %q", secondSlug)
	}
}

func TestCreateOrganizationValidatesTheName(t *testing.T) {
	api := newTestAPI(t)
	_, email := api.user("founder", true)
	token := api.token(email)

	status, body := api.do("POST", "/api/v1/organizations", token, map[string]any{"name": ""})

	if status != http.StatusUnprocessableEntity {
		t.Fatalf("gave %d: %v", status, body)
	}
}

func TestListOrganizationsShowsOwnedAndJoinedOnesEvenWithoutDepartments(t *testing.T) {
	api := newTestAPI(t)
	userID, email := api.user("user", true)
	token := api.token(email)

	// owns a fresh organization with no departments yet
	api.do("POST", "/api/v1/organizations", token, map[string]any{"name": "Fresh Org"})

	// belongs to a department of someone else's organization
	otherOwnerID, _ := api.user("other-owner", true)
	otherOrgID := api.organization(otherOwnerID)
	department := api.department(otherOrgID, "Engineering")
	api.member(department.ID, userID, "member", false)

	// has nothing to do with a third organization
	thirdOwnerID, _ := api.user("stranger", true)
	api.organization(thirdOwnerID)

	status, body := api.do("GET", "/api/v1/organizations", token, nil)
	if status != http.StatusOK {
		t.Fatalf("gave %d: %v", status, body)
	}

	byName := map[string]map[string]any{}
	for _, item := range body["data"].([]any) {
		organization := item.(map[string]any)
		byName[organization["name"].(string)] = organization
	}
	if fresh := byName["Fresh Org"]; fresh == nil || fresh["is_owner"] != true || fresh["department_count"] != float64(0) {
		t.Errorf("want the owned, empty organization listed, got %v", byName)
	}
	if joined := byName["Org"]; joined == nil || joined["is_owner"] != false || joined["department_count"] != float64(1) {
		t.Errorf("want the joined organization listed with its department counted, got %v", byName)
	}
	if len(byName) != 2 {
		t.Errorf("an unrelated organization must not be listed, got %v", byName)
	}
}

func TestOrganizationEndpointsNeedALogin(t *testing.T) {
	api := newTestAPI(t)

	if status, _ := api.do("GET", "/api/v1/organizations", "", nil); status != http.StatusUnauthorized {
		t.Errorf("list without a token gave %d, want 401", status)
	}
	if status, _ := api.do("POST", "/api/v1/organizations", "", map[string]any{"name": "x"}); status != http.StatusUnauthorized {
		t.Errorf("create without a token gave %d, want 401", status)
	}
}

// ── the access code ─────────────────────────────────────────────────────────

func (api *testAPI) regenerateAccessCode(token string, department testDepartment) (int, map[string]any) {
	api.t.Helper()
	return api.do("POST", "/api/v1/departments/"+department.Slug+"/access-code", token, nil)
}

func TestRegeneratingTheAccessCodeReplacesTheOldOne(t *testing.T) {
	api := newTestAPI(t)
	department, admin, _ := api.shareSetup()

	status, body := api.regenerateAccessCode(admin, department)

	if status != http.StatusOK {
		t.Fatalf("gave %d: %v", status, body)
	}
	code, _ := body["data"].(map[string]any)["access_code"].(string)
	if len(code) != accessCodeLength {
		t.Fatalf("want a %d-character code, got %q", accessCodeLength, code)
	}

	if status, _ := join(api, api.token(mustEmail(api, "outsider-for-code", true)), department.Code); status != http.StatusUnprocessableEntity {
		t.Errorf("the old code still worked, gave %d", status)
	}
	if status, _ := join(api, api.token(mustEmail(api, "newcomer-for-code", true)), code); status != http.StatusCreated {
		t.Errorf("the new code did not work, gave %d", status)
	}
}

func TestRegeneratingTheAccessCodeNeverRepeatsItself(t *testing.T) {
	api := newTestAPI(t)
	department, admin, _ := api.shareSetup()

	seen := map[string]bool{}
	for i := 0; i < 5; i++ {
		_, body := api.regenerateAccessCode(admin, department)
		code := body["data"].(map[string]any)["access_code"].(string)
		if seen[code] {
			t.Errorf("got the same code twice: %q", code)
		}
		seen[code] = true
	}
}

func TestOnlyManagersCanRegenerateTheAccessCode(t *testing.T) {
	api := newTestAPI(t)
	department, _, member := api.shareSetup()

	if status, _ := api.regenerateAccessCode(member, department); status != http.StatusNotFound {
		t.Errorf("a plain member gave %d, want 404", status)
	}
	if status, _ := api.regenerateAccessCode("", department); status != http.StatusUnauthorized {
		t.Errorf("no token gave %d, want 401", status)
	}
}

// mustEmail makes a fresh verified user and returns their email, for tests that only need someone to log in as.
func mustEmail(api *testAPI, name string, verified bool) string {
	_, email := api.user(name, verified)
	return email
}
