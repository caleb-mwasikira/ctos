package handlers

import (
	"context"
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"log"
	"math/big"
	"net/http"
	"strconv"
	"strings"

	"github.com/go-chi/chi/v5"

	"ctos_api/internal/database"
	"ctos_api/internal/laravelcrypt"
	"ctos_api/internal/mail"
)

// wrongAccessCode is the one answer for every way joining can fail (no department has that
// code), so nobody can find out which codes are close to a real one.
const wrongAccessCode = "That access code is incorrect."

// normalizeAccessCode: codes are shown in capitals, but typing them in lower
// case or with spaces around them must work too.
func normalizeAccessCode(code string) string {
	return strings.ToUpper(strings.TrimSpace(code))
}

// hashAccessCode: a plain SHA-256 hex digest of the normalized code, matching
// Department::hashAccessCode on the website — a deterministic digest (rather than bcrypt) is what
// lets a department be found by its code alone, and is a normal way to store a high-entropy random
// token nobody has to memorize, the same way an API key is usually hashed for lookup.
func hashAccessCode(code string) string {
	sum := sha256.Sum256([]byte(normalizeAccessCode(code)))
	return hex.EncodeToString(sum[:])
}

// departmentResponse is how a department goes out in JSON.
func departmentResponse(department *database.MemberDepartment) map[string]any {
	return map[string]any{
		"id":   department.ID,
		"name": department.Name,
		"slug": department.Slug,
		"organization": map[string]any{
			"id":   department.OrganizationID,
			"name": department.OrganizationName,
			"slug": department.OrganizationSlug,
		},
		"role": department.Role,
	}
}

// listDepartments: GET /api/v1/departments — the departments the user belongs to.
func (app *App) listDepartments(w http.ResponseWriter, r *http.Request) {
	user := r.Context().Value(userContextKey).(*database.User)

	departments, err := app.DB.GetDepartmentsOfUser(r.Context(), user.ID)
	if err != nil {
		log.Printf("list departments: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}

	response := []map[string]any{}
	for i := range departments {
		response = append(response, departmentResponse(&departments[i]))
	}
	writeJSON(w, http.StatusOK, map[string]any{"data": response})
}

// joinDepartment: POST /api/v1/departments/join
// Body: {"code": "ABCDE23456"}
// The code alone finds its own department (see hashAccessCode); no separate department ID needed.
func (app *App) joinDepartment(w http.ResponseWriter, r *http.Request) {
	user := r.Context().Value(userContextKey).(*database.User)

	var input struct {
		Code string `json:"code"`
	}
	if err := json.NewDecoder(r.Body).Decode(&input); err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]any{"message": "Request body must be valid JSON."})
		return
	}
	message := validateRequiredText("code", input.Code, 64)
	if message != "" {
		writeInvalid(w, "code", message)
		return
	}

	// Wrong codes are counted per user, so a code cannot be guessed by trying them all.
	userKey := strconv.FormatInt(user.ID, 10)
	if app.JoinFailures.isLocked(userKey) {
		writeJSON(w, http.StatusTooManyRequests, map[string]any{"message": "Too many wrong access codes. Try again in a few minutes."})
		return
	}

	department, err := app.DB.GetDepartmentByAccessCodeHash(r.Context(), hashAccessCode(input.Code))
	if err != nil {
		log.Printf("join department: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}
	if department == nil {
		app.JoinFailures.add(userKey)
		writeInvalid(w, "code", wrongAccessCode)
		return
	}
	app.JoinFailures.clear(userKey)

	locked, err := app.DB.IsLockedOutOfDepartment(r.Context(), user.ID, department.ID)
	if err != nil {
		log.Printf("join department: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}
	if locked {
		writeInvalid(w, "code", "Your access to this department has been locked by the organization owner.")
		return
	}

	if err := app.DB.AddDepartmentMember(r.Context(), department.ID, user.ID); err != nil {
		log.Printf("join department: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}
	joined, err := app.DB.GetDepartmentOfUser(r.Context(), user.ID, department.ID)
	if err != nil || joined == nil {
		log.Printf("join department: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}
	writeJSON(w, http.StatusCreated, map[string]any{"data": departmentResponse(joined)})
}

// showDepartment: GET /api/v1/departments/{department}
// The department as its page on the website shows it: who is in it, and, for the people who manage
// it, the access code and who has been invited but not signed up yet.
func (app *App) showDepartment(w http.ResponseWriter, r *http.Request) {
	department, ok := app.memberDepartment(w, r)
	if !ok {
		return
	}
	user := r.Context().Value(userContextKey).(*database.User)

	canManage, err := app.userCanManage(r.Context(), user, department)
	if err != nil {
		log.Printf("show department: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}

	members, err := app.DB.GetDepartmentMembers(r.Context(), department.ID)
	if err != nil {
		log.Printf("show department: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}
	memberList := []map[string]any{}
	for _, member := range members {
		entry := map[string]any{"id": member.ID, "name": member.Name, "role": member.Role}
		if canManage {
			entry["email"] = member.Email
		}
		memberList = append(memberList, entry)
	}

	response := departmentResponse(department)
	response["member_count"] = len(memberList)
	response["members"] = memberList
	response["can_manage"] = canManage
	if canManage {
		invitations, err := app.DB.GetDepartmentInvitations(r.Context(), department.ID)
		if err != nil {
			log.Printf("show department: %v", err)
			writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
			return
		}
		invitationList := []map[string]any{}
		for _, invitation := range invitations {
			invitationList = append(invitationList, map[string]any{"id": invitation.ID, "email": invitation.Email})
		}
		response["invitations"] = invitationList
		response["access_code"] = app.accessCodeOf(r, department.ID)
	}
	writeJSON(w, http.StatusOK, map[string]any{"data": response})
}

// accessCodeOf reads the department's access code from the copy the website keeps for admins. It
// is nil when there is none (a department made before the copy existed, or no APP_KEY set here).
func (app *App) accessCodeOf(r *http.Request, departmentID int64) *string {
	if app.Config.AppKey == "" {
		return nil
	}
	encrypted, err := app.DB.GetEncryptedAccessCode(r.Context(), departmentID)
	if err != nil || encrypted == nil {
		return nil
	}
	code, err := laravelcrypt.Decrypt(*encrypted, app.Config.AppKey)
	if err != nil {
		log.Printf("access code of department %d cannot be read: %v", departmentID, err)
		return nil
	}
	return &code
}

// leaveDepartment: DELETE /api/v1/departments/{department}/membership
func (app *App) leaveDepartment(w http.ResponseWriter, r *http.Request) {
	department, ok := app.memberDepartment(w, r)
	if !ok {
		return
	}
	user := r.Context().Value(userContextKey).(*database.User)

	if err := app.DB.LeaveDepartment(r.Context(), user.ID, department.ID); err != nil {
		log.Printf("leave department: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"message": "You left the department."})
}

// userCanManage says whether the user is the department's organization owner, or an admin or
// lead of the department itself: the people who see its access code and can share and manage it.
func (app *App) userCanManage(ctx context.Context, user *database.User, department *database.MemberDepartment) (bool, error) {
	if department.Role == "admin" || department.Role == "lead" {
		return true, nil
	}
	return app.DB.IsOrganizationOwner(ctx, user.ID, department.OrganizationID)
}

// shareDepartment: POST /api/v1/departments/{department}/invitations  {"email"}
// Only people who manage the department may share it. An existing, verified account is given
// access straight away; anyone else is emailed an invitation that becomes access once they sign
// up and confirm that address. Matches the website's "Share" action exactly, so this and it
// behave the same whichever one is used.
func (app *App) shareDepartment(w http.ResponseWriter, r *http.Request) {
	department, ok := app.memberDepartment(w, r)
	if !ok {
		return
	}
	user := r.Context().Value(userContextKey).(*database.User)

	canManage, err := app.userCanManage(r.Context(), user, department)
	if err != nil {
		log.Printf("share department: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}
	if !canManage {
		writeJSON(w, http.StatusNotFound, map[string]any{"message": "Not found."})
		return
	}

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

	// Sharing a department by email is throttled per user, the same 30-an-hour limit as the
	// website, so it cannot be used to spam addresses.
	shareKey := strconv.FormatInt(user.ID, 10)
	if !app.ShareAttempts.allow(shareKey, shareAttemptsPerHour) {
		writeJSON(w, http.StatusTooManyRequests, map[string]any{"message": "Too many invitations sent. Try again in a while."})
		return
	}

	candidate, err := app.DB.FindUserByEmail(r.Context(), email)
	if err != nil {
		log.Printf("share department: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}
	if candidate != nil {
		locked, err := app.DB.IsLockedOutOfDepartment(r.Context(), candidate.ID, department.ID)
		if err != nil {
			log.Printf("share department: %v", err)
			writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
			return
		}
		if locked {
			writeInvalid(w, "email", "That person is locked out of this department, so it cannot be shared with them.")
			return
		}
		isMember, err := app.DB.IsMemberOfDepartment(r.Context(), department.ID, candidate.ID)
		if err != nil {
			log.Printf("share department: %v", err)
			writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
			return
		}
		if isMember {
			writeInvalid(w, "email", "That person already has access to this department.")
			return
		}
	}

	hasAccess := candidate != nil && candidate.Verified
	added, err := app.DB.ShareDepartment(r.Context(), department.ID, user.ID, candidate, email, func() error {
		return mail.Send(app.mailConfig(), email, shareEmailSubject(user.Name, department.Name), shareEmailBody(app.Config.WebsiteURL, user.Name, department, email, hasAccess))
	})
	if err != nil {
		log.Printf("share department: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "We could not send the email right now. Please try again in a moment."})
		return
	}

	message := fmt.Sprintf("We emailed %s. They will get access to %s as soon as they sign up and confirm that address.", email, department.Name)
	if added {
		message = fmt.Sprintf("%s now has access to %s and has been emailed.", email, department.Name)
	}
	writeJSON(w, http.StatusOK, map[string]any{"message": message, "data": map[string]any{"added": added}})
}

func shareEmailSubject(inviterName, departmentName string) string {
	return fmt.Sprintf("%s shared %s with you", inviterName, departmentName)
}

func shareEmailBody(websiteURL, inviterName string, department *database.MemberDepartment, email string, hasAccess bool) string {
	intro := fmt.Sprintf("Hello!\n\n%s has given you access to the %s department of %s.",
		inviterName, department.Name, department.OrganizationName)
	if hasAccess {
		return fmt.Sprintf("%s\n\nIt is already in your account. Log in to see its files:\n%s/departments/%s\n",
			intro, websiteURL, department.Slug)
	}
	return fmt.Sprintf("%s\n\nSign up with this address (%s) and confirm it, and the department appears on your dashboard. You do not need an access code:\n%s/register\n",
		intro, email, websiteURL)
}

// withdrawInvitation: DELETE /api/v1/departments/{department}/invitations/{invitation}
// Only people who manage the department may withdraw one of its invitations.
func (app *App) withdrawInvitation(w http.ResponseWriter, r *http.Request) {
	department, ok := app.memberDepartment(w, r)
	if !ok {
		return
	}
	user := r.Context().Value(userContextKey).(*database.User)

	canManage, err := app.userCanManage(r.Context(), user, department)
	if err != nil {
		log.Printf("withdraw invitation: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}
	if !canManage {
		writeJSON(w, http.StatusNotFound, map[string]any{"message": "Not found."})
		return
	}

	invitationID, err := strconv.ParseInt(chi.URLParam(r, "invitation"), 10, 64)
	if err != nil {
		writeJSON(w, http.StatusNotFound, map[string]any{"message": "Not found."})
		return
	}

	email, found, err := app.DB.DeleteInvitation(r.Context(), department.ID, invitationID)
	if err != nil {
		log.Printf("withdraw invitation: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}
	if !found {
		writeJSON(w, http.StatusNotFound, map[string]any{"message": "Not found."})
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"message": fmt.Sprintf("The invitation for %s was withdrawn.", email)})
}

// accessCodeAlphabet and accessCodeLength match Department::ACCESS_CODE_ALPHABET and
// ACCESS_CODE_LENGTH on the website exactly, so a code made here looks like one made there. The
// letters and digits that are easy to confuse when read aloud or copied by hand (0/O, 1/I/L, ...)
// are left out on purpose.
const accessCodeAlphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

const accessCodeLength = 10

func generateAccessCode() (string, error) {
	letters := make([]byte, accessCodeLength)
	for i := range letters {
		n, err := rand.Int(rand.Reader, big.NewInt(int64(len(accessCodeAlphabet))))
		if err != nil {
			return "", err
		}
		letters[i] = accessCodeAlphabet[n.Int64()]
	}
	return string(letters), nil
}

// regenerateAccessCode: POST /api/v1/departments/{department}/access-code
// Only people who manage the department may do this. The old code stops working straight away;
// existing members keep their access — matches Department::regenerateAccessCode on the website,
// so a code made from the app is no different from one made there.
func (app *App) regenerateAccessCode(w http.ResponseWriter, r *http.Request) {
	department, ok := app.memberDepartment(w, r)
	if !ok {
		return
	}
	user := r.Context().Value(userContextKey).(*database.User)

	canManage, err := app.userCanManage(r.Context(), user, department)
	if err != nil {
		log.Printf("regenerate access code: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}
	if !canManage {
		writeJSON(w, http.StatusNotFound, map[string]any{"message": "Not found."})
		return
	}

	code, err := generateAccessCode()
	if err != nil {
		log.Printf("regenerate access code: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}
	hash := hashAccessCode(code)

	var encrypted *string
	if app.Config.AppKey != "" {
		value, err := laravelcrypt.Encrypt(code, app.Config.AppKey)
		if err != nil {
			log.Printf("regenerate access code: %v", err)
			writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
			return
		}
		encrypted = &value
	}

	if err := app.DB.SetAccessCode(r.Context(), department.ID, hash, encrypted); err != nil {
		log.Printf("regenerate access code: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"data": map[string]any{"access_code": code}})
}
