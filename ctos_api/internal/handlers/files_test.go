package handlers

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/http/httptest"
	"net/url"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"testing"
	"time"

	"github.com/coder/websocket"

	"ctos_api/internal/laravelcrypt"
)

// Database-backed tests of the file endpoints; see api_test.go for how to run them.

func sha(content string) string {
	sum := sha256.Sum256([]byte(content))
	return hex.EncodeToString(sum[:])
}

// request makes a request with a raw body and headers, and returns the whole answer.
func (api *testAPI) request(method string, path string, token string, body string, headers map[string]string) *httptest.ResponseRecorder {
	api.t.Helper()
	request := httptest.NewRequest(method, path, strings.NewReader(body))
	if token != "" {
		request.Header.Set("Authorization", "Bearer "+token)
	}
	for name, value := range headers {
		request.Header.Set(name, value)
	}
	response := httptest.NewRecorder()
	api.routes.ServeHTTP(response, request)
	return response
}

func decoded(response *httptest.ResponseRecorder) map[string]any {
	var body map[string]any
	json.Unmarshal(response.Body.Bytes(), &body)
	return body
}

func filesURL(department testDepartment, kind string, path string, base *int64) string {
	target := fmt.Sprintf("/api/v1/departments/%s/%s?path=%s", department.Slug, kind, url.QueryEscape(path))
	if base != nil {
		target += "&base_version=" + strconv.FormatInt(*base, 10)
	}
	return target
}

func version(n int64) *int64 { return &n }

// upload puts a file. A nil base means the caller did not say which version it saw.
func (api *testAPI) upload(token string, department testDepartment, path string, content string, base *int64) *httptest.ResponseRecorder {
	return api.request("PUT", filesURL(department, "files", path, base), token, content, nil)
}

func nodeOf(response *httptest.ResponseRecorder) map[string]any {
	return decoded(response)["data"].(map[string]any)
}

// changesSince returns the nodes of a changes answer, keyed by path, and the cursor.
func (api *testAPI) changesSince(token string, department testDepartment, cursor int64, limit int) (map[string]map[string]any, int64, bool) {
	api.t.Helper()
	response := api.request("GET", fmt.Sprintf("/api/v1/departments/%s/changes?cursor=%d&limit=%d", department.Slug, cursor, limit), token, "", nil)
	if response.Code != http.StatusOK {
		api.t.Fatalf("changes gave %d: %s", response.Code, response.Body.String())
	}
	data := decoded(response)["data"].(map[string]any)
	nodes := map[string]map[string]any{}
	for _, item := range data["nodes"].([]any) {
		node := item.(map[string]any)
		nodes[node["path"].(string)] = node
	}
	return nodes, int64(data["cursor"].(float64)), data["more"].(bool)
}

// setup makes a department with two members who each have a token.
func (api *testAPI) setup() (department testDepartment, alice string, bob string) {
	api.t.Helper()
	ownerID, _ := api.user("owner", true)
	department = api.department(api.organization(ownerID), "Engineering")
	aliceID, aliceEmail := api.user("alice", true)
	bobID, bobEmail := api.user("bob", true)
	api.member(department.ID, aliceID, "member", false)
	api.member(department.ID, bobID, "member", false)
	return department, api.token(aliceEmail), api.token(bobEmail)
}

func (api *testAPI) contentOnDisk(node map[string]any) (string, bool) {
	var key string
	err := api.pool.QueryRow(context.Background(), `select storage_path from nodes where id = $1`, int64(node["id"].(float64))).Scan(&key)
	if err != nil {
		return "", false
	}
	content, err := os.ReadFile(filepath.Join(api.filesDir, key))
	return string(content), err == nil
}

// ── uploading and downloading ──────────────────────────────────────────────

func TestUploadCreatesAFileThatCanBeDownloaded(t *testing.T) {
	api := newTestAPI(t)
	department, alice, bob := api.setup()

	response := api.upload(alice, department, "notes.txt", "hello world", version(0))

	if response.Code != http.StatusCreated {
		t.Fatalf("upload gave %d: %s", response.Code, response.Body.String())
	}
	node := nodeOf(response)
	if node["path"] != "notes.txt" || node["type"] != "file" || node["size"] != float64(11) || node["version"] != float64(1) || node["checksum"] != sha("hello world") {
		t.Errorf("unexpected node: %v", node)
	}

	// another member downloads it
	download := api.request("GET", filesURL(department, "files", "notes.txt", nil), bob, "", nil)
	if download.Code != http.StatusOK || download.Body.String() != "hello world" {
		t.Errorf("download gave %d %q", download.Code, download.Body.String())
	}
	if download.Header().Get("ETag") != `"`+sha("hello world")+`"` || download.Header().Get("X-Version") != "1" {
		t.Errorf("headers: %v", download.Header())
	}
	if !strings.Contains(download.Header().Get("Content-Disposition"), "notes.txt") {
		t.Errorf("Content-Disposition = %q", download.Header().Get("Content-Disposition"))
	}
}

func TestUploadKeepsTheModifiedTimeTheDeviceSent(t *testing.T) {
	api := newTestAPI(t)
	department, alice, _ := api.setup()

	response := api.request("PUT", filesURL(department, "files", "old.txt", version(0)), alice, "x", map[string]string{"X-Modified-At": "1700000000000"})

	if nodeOf(response)["modified_at"] != float64(1700000000000) {
		t.Errorf("modified_at = %v", nodeOf(response)["modified_at"])
	}
	download := api.request("GET", filesURL(department, "files", "old.txt", nil), alice, "", nil)
	if download.Header().Get("X-Modified-At") != "1700000000000" {
		t.Errorf("X-Modified-At = %q", download.Header().Get("X-Modified-At"))
	}
}

func TestUploadingIntoAFolderCreatesTheFoldersOnTheWay(t *testing.T) {
	api := newTestAPI(t)
	department, alice, _ := api.setup()

	api.upload(alice, department, "reports/2026/q3.xlsx", "numbers", version(0))

	nodes, _, _ := api.changesSince(alice, department, 0, 100)
	if nodes["reports"]["type"] != "folder" || nodes["reports/2026"]["type"] != "folder" || nodes["reports/2026/q3.xlsx"]["type"] != "file" {
		t.Errorf("want two folders and a file, got %v", nodes)
	}
}

func TestReplacingAFileNeedsTheVersionThatWasSeen(t *testing.T) {
	api := newTestAPI(t)
	department, alice, bob := api.setup()
	api.upload(alice, department, "plan.md", "v1", version(0))

	// no version, the wrong version, and "create" for a file that exists: all refused, and the answer says what is there now
	for name, base := range map[string]*int64{"no version": nil, "a wrong version": version(5), "create": version(0)} {
		response := api.upload(bob, department, "plan.md", "bob's edit", base)
		if response.Code != http.StatusConflict {
			t.Errorf("%s gave %d, want 409", name, response.Code)
			continue
		}
		current := decoded(response)["current"].(map[string]any)
		if current["version"] != float64(1) || current["checksum"] != sha("v1") {
			t.Errorf("%s: the answer should describe the current file, got %v", name, current)
		}
	}

	// with the right version it works, and the version goes up
	response := api.upload(bob, department, "plan.md", "v2", version(1))
	if response.Code != http.StatusOK || nodeOf(response)["version"] != float64(2) {
		t.Fatalf("replace gave %d: %s", response.Code, response.Body.String())
	}
	download := api.request("GET", filesURL(department, "files", "plan.md", nil), alice, "", nil)
	if download.Body.String() != "v2" {
		t.Errorf("download = %q, want v2", download.Body.String())
	}
}

func TestReplacingAFileDeletesTheOldContent(t *testing.T) {
	api := newTestAPI(t)
	department, alice, _ := api.setup()
	first := nodeOf(api.upload(alice, department, "a.txt", "one", version(0)))
	var oldKey string
	api.pool.QueryRow(context.Background(), `select storage_path from nodes where id = $1`, int64(first["id"].(float64))).Scan(&oldKey)

	api.upload(alice, department, "a.txt", "two", version(1))

	if oldKey == "" {
		t.Fatal("the first upload should have stored a file")
	}
	if _, err := os.Stat(filepath.Join(api.filesDir, oldKey)); err == nil {
		t.Error("the replaced content should be gone from disk")
	}
	if content, ok := api.contentOnDisk(first); !ok || content != "two" {
		t.Errorf("the new content should be on disk, got %q %v", content, ok)
	}
}

func TestSendingTheSameContentAgainChangesNothing(t *testing.T) {
	api := newTestAPI(t)
	department, alice, _ := api.setup()
	api.upload(alice, department, "a.txt", "same", version(0))
	_, cursor, _ := api.changesSince(alice, department, 0, 100)

	// a retry of the same upload, even without a version
	response := api.upload(alice, department, "a.txt", "same", nil)

	if response.Code != http.StatusOK || nodeOf(response)["version"] != float64(1) {
		t.Errorf("a repeat should be a quiet success at version 1, got %d %v", response.Code, decoded(response))
	}
	if nodes, _, _ := api.changesSince(alice, department, cursor, 100); len(nodes) != 0 {
		t.Errorf("a repeat must not log a change, got %v", nodes)
	}
}

func TestAFileThatWasDeletedCannotBeReplaced(t *testing.T) {
	api := newTestAPI(t)
	department, alice, _ := api.setup()

	response := api.upload(alice, department, "ghost.txt", "x", version(3))

	if response.Code != http.StatusConflict || decoded(response)["current"] != nil {
		t.Errorf("want 409 with no current node, got %d %v", response.Code, decoded(response))
	}
}

func TestPathsAreValidated(t *testing.T) {
	api := newTestAPI(t)
	department, alice, _ := api.setup()

	for _, path := range []string{"", "/abs", "a//b", "../up", "a/../b", "back\\slash", strings.Repeat("a", 300)} {
		response := api.upload(alice, department, path, "x", version(0))
		if response.Code != http.StatusUnprocessableEntity {
			t.Errorf("%q gave %d, want 422", path, response.Code)
		}
	}
}

func TestAFolderAndAFileCannotShareAName(t *testing.T) {
	api := newTestAPI(t)
	department, alice, _ := api.setup()
	api.upload(alice, department, "docs/readme.md", "x", version(0))
	api.upload(alice, department, "file.txt", "x", version(0))

	if response := api.upload(alice, department, "docs", "x", version(0)); response.Code != http.StatusUnprocessableEntity {
		t.Errorf("a file over a folder gave %d, want 422", response.Code)
	}
	if response := api.upload(alice, department, "file.txt/inside.txt", "x", version(0)); response.Code != http.StatusUnprocessableEntity {
		t.Errorf("a file inside a file gave %d, want 422", response.Code)
	}
}

func TestADamagedUploadIsRefusedAndKeepsNothing(t *testing.T) {
	api := newTestAPI(t)
	department, alice, _ := api.setup()

	response := api.request("PUT", filesURL(department, "files", "a.txt", version(0)), alice, "content", map[string]string{"X-Content-SHA256": sha("something else")})

	if response.Code != http.StatusUnprocessableEntity {
		t.Errorf("want 422, got %d", response.Code)
	}
	if nodes, _, _ := api.changesSince(alice, department, 0, 100); len(nodes) != 0 {
		t.Errorf("nothing should have been saved, got %v", nodes)
	}
	good := api.request("PUT", filesURL(department, "files", "a.txt", version(0)), alice, "content", map[string]string{"X-Content-SHA256": strings.ToUpper(sha("content"))})
	if good.Code != http.StatusCreated {
		t.Errorf("a right checksum (in any case) should be accepted, got %d", good.Code)
	}
}

func TestTooBigAFileIsRefused(t *testing.T) {
	api := newTestAPI(t)
	api.app.Config.MaxUploadBytes = 10
	department, alice, _ := api.setup()

	if response := api.upload(alice, department, "ten.txt", "0123456789", version(0)); response.Code != http.StatusCreated {
		t.Errorf("exactly the limit should be fine, got %d", response.Code)
	}
	response := api.upload(alice, department, "eleven.txt", "0123456789A", version(0))

	if response.Code != http.StatusRequestEntityTooLarge {
		t.Errorf("want 413, got %d", response.Code)
	}
	nodes, _, _ := api.changesSince(alice, department, 0, 100)
	if _, saved := nodes["eleven.txt"]; saved {
		t.Error("a refused file must not be saved")
	}
}

func TestAnEmptyFileCanBeUploaded(t *testing.T) {
	api := newTestAPI(t)
	department, alice, _ := api.setup()

	response := api.upload(alice, department, "empty.txt", "", version(0))

	if response.Code != http.StatusCreated || nodeOf(response)["size"] != float64(0) {
		t.Errorf("got %d %v", response.Code, decoded(response))
	}
	if download := api.request("GET", filesURL(department, "files", "empty.txt", nil), alice, "", nil); download.Code != http.StatusOK || download.Body.Len() != 0 {
		t.Errorf("download gave %d with %d bytes", download.Code, download.Body.Len())
	}
}

func TestADownloadCanBeResumedWithARange(t *testing.T) {
	api := newTestAPI(t)
	department, alice, _ := api.setup()
	api.upload(alice, department, "big.txt", "0123456789", version(0))

	response := api.request("GET", filesURL(department, "files", "big.txt", nil), alice, "", map[string]string{"Range": "bytes=4-"})

	if response.Code != http.StatusPartialContent {
		t.Fatalf("want 206, got %d", response.Code)
	}
	if body, _ := io.ReadAll(response.Body); string(body) != "456789" {
		t.Errorf("body = %q", body)
	}
}

func TestOnlyFilesCanBeDownloaded(t *testing.T) {
	api := newTestAPI(t)
	department, alice, _ := api.setup()
	api.upload(alice, department, "docs/a.txt", "x", version(0))
	api.request("DELETE", filesURL(department, "nodes", "docs/a.txt", nil), alice, "", nil)

	for _, path := range []string{"docs", "docs/a.txt", "nope.txt"} {
		if response := api.request("GET", filesURL(department, "files", path, nil), alice, "", nil); response.Code != http.StatusNotFound {
			t.Errorf("%q gave %d, want 404", path, response.Code)
		}
	}
}

// ── folders and deleting ───────────────────────────────────────────────────

func TestFoldersAreCreatedOnceAndIncludingTheOnesAbove(t *testing.T) {
	api := newTestAPI(t)
	department, alice, _ := api.setup()
	create := func(path string) *httptest.ResponseRecorder {
		return api.request("POST", fmt.Sprintf("/api/v1/departments/%s/folders", department.Slug), alice, fmt.Sprintf(`{"path": %q}`, path), nil)
	}

	first := create("a/b/c")
	again := create("a/b/c")

	if first.Code != http.StatusCreated || again.Code != http.StatusOK {
		t.Errorf("first %d, repeat %d; want 201 then 200", first.Code, again.Code)
	}
	nodes, _, _ := api.changesSince(alice, department, 0, 100)
	if len(nodes) != 3 || nodes["a"]["type"] != "folder" || nodes["a/b"]["type"] != "folder" || nodes["a/b/c"]["type"] != "folder" {
		t.Errorf("want a, a/b and a/b/c, got %v", nodes)
	}
	api.upload(alice, department, "file.txt", "x", version(0))
	if response := create("file.txt"); response.Code != http.StatusUnprocessableEntity {
		t.Errorf("a folder over a file gave %d, want 422", response.Code)
	}
	if response := create("../x"); response.Code != http.StatusUnprocessableEntity {
		t.Errorf("a bad path gave %d, want 422", response.Code)
	}
}

func TestDeletingAFileLeavesATombstoneAndRemovesTheContent(t *testing.T) {
	api := newTestAPI(t)
	department, alice, bob := api.setup()
	node := nodeOf(api.upload(alice, department, "a.txt", "x", version(0)))
	_, cursor, _ := api.changesSince(bob, department, 0, 100)

	response := api.request("DELETE", filesURL(department, "nodes", "a.txt", version(1)), alice, "", nil)

	if response.Code != http.StatusOK {
		t.Fatalf("delete gave %d: %s", response.Code, response.Body.String())
	}
	changes, _, _ := api.changesSince(bob, department, cursor, 100)
	if tombstone := changes["a.txt"]; tombstone == nil || tombstone["deleted"] != true || tombstone["version"] != float64(2) {
		t.Errorf("the other member should learn of the deletion, got %v", changes)
	}
	if _, present := api.contentOnDisk(node); present {
		t.Error("the content should be gone from disk")
	}
	if again := api.request("DELETE", filesURL(department, "nodes", "a.txt", nil), alice, "", nil); again.Code != http.StatusNotFound {
		t.Errorf("deleting what is gone gave %d, want 404", again.Code)
	}
}

func TestDeletingAFolderDeletesEverythingInIt(t *testing.T) {
	api := newTestAPI(t)
	department, alice, _ := api.setup()
	api.upload(alice, department, "docs/a.txt", "a", version(0))
	api.upload(alice, department, "docs/deep/b.txt", "b", version(0))
	api.upload(alice, department, "docs-other/c.txt", "c", version(0)) // shares a prefix, but is not inside docs
	_, cursor, _ := api.changesSince(alice, department, 0, 100)

	response := api.request("DELETE", filesURL(department, "nodes", "docs", nil), alice, "", nil)

	if response.Code != http.StatusOK || decoded(response)["data"].(map[string]any)["deleted"] != float64(2) {
		t.Errorf("want 2 files deleted, got %d %v", response.Code, decoded(response))
	}
	changes, _, _ := api.changesSince(alice, department, cursor, 100)
	for _, path := range []string{"docs", "docs/a.txt", "docs/deep", "docs/deep/b.txt"} {
		if changes[path] == nil || changes[path]["deleted"] != true {
			t.Errorf("%s should be a tombstone, got %v", path, changes[path])
		}
	}
	if changes["docs-other/c.txt"] != nil || changes["docs-other"] != nil {
		t.Error("a folder with a similar name must not be touched")
	}
}

func TestDeletingWithAnOutOfDateVersionIsAConflict(t *testing.T) {
	api := newTestAPI(t)
	department, alice, bob := api.setup()
	api.upload(alice, department, "a.txt", "one", version(0))
	api.upload(bob, department, "a.txt", "two", version(1))

	response := api.request("DELETE", filesURL(department, "nodes", "a.txt", version(1)), alice, "", nil)

	if response.Code != http.StatusConflict || decoded(response)["current"].(map[string]any)["version"] != float64(2) {
		t.Errorf("want 409 describing version 2, got %d %v", response.Code, decoded(response))
	}
	if download := api.request("GET", filesURL(department, "files", "a.txt", nil), alice, "", nil); download.Body.String() != "two" {
		t.Error("the file must still be there")
	}
}

func TestADeletedPathCanBeUsedAgain(t *testing.T) {
	api := newTestAPI(t)
	department, alice, _ := api.setup()
	api.upload(alice, department, "a.txt", "old", version(0))
	api.request("DELETE", filesURL(department, "nodes", "a.txt", nil), alice, "", nil)

	response := api.upload(alice, department, "a.txt", "new", version(0))

	if response.Code != http.StatusCreated || nodeOf(response)["version"] != float64(1) {
		t.Errorf("got %d %v", response.Code, decoded(response))
	}
}

// ── the change log ─────────────────────────────────────────────────────────

func TestChangesCanBeFollowedWithACursor(t *testing.T) {
	api := newTestAPI(t)
	department, alice, bob := api.setup()

	nodes, cursor, more := api.changesSince(bob, department, 0, 100)
	if len(nodes) != 0 || more || cursor != 0 {
		t.Errorf("an empty department: %v %d %v", nodes, cursor, more)
	}

	api.upload(alice, department, "one.txt", "1", version(0))
	nodes, cursor, _ = api.changesSince(bob, department, 0, 100)
	if len(nodes) != 1 || nodes["one.txt"] == nil {
		t.Fatalf("got %v", nodes)
	}

	api.upload(alice, department, "two.txt", "2", version(0))
	api.upload(alice, department, "one.txt", "1b", version(1))
	nodes, next, _ := api.changesSince(bob, department, cursor, 100)
	if len(nodes) != 2 || nodes["one.txt"]["version"] != float64(2) || nodes["two.txt"] == nil {
		t.Errorf("only what changed since the cursor should come back, each node once, got %v", nodes)
	}
	if nodes, _, _ := api.changesSince(bob, department, next, 100); len(nodes) != 0 {
		t.Errorf("nothing changed since the last cursor, got %v", nodes)
	}
}

func TestChangesArriveInPagesThatNeverSkipAnything(t *testing.T) {
	api := newTestAPI(t)
	department, alice, bob := api.setup()
	for i := 0; i < 5; i++ {
		api.upload(alice, department, fmt.Sprintf("file-%d.txt", i), "x", version(0))
	}

	seen := map[string]bool{}
	cursor := int64(0)
	for pages := 0; pages < 10; pages++ {
		nodes, next, more := api.changesSince(bob, department, cursor, 2)
		for path := range nodes {
			seen[path] = true
		}
		cursor = next
		if !more {
			break
		}
	}

	if len(seen) != 5 {
		t.Errorf("five files in pages of two should all arrive, got %v", seen)
	}
}

func TestChangesAreForOneDepartmentOnly(t *testing.T) {
	api := newTestAPI(t)
	department, alice, _ := api.setup()
	other := api.department(api.organization(func() int64 { id, _ := api.user("owner2", true); return id }()), "Other")
	otherMemberID, otherEmail := api.user("carol", true)
	api.member(other.ID, otherMemberID, "member", false)
	carol := api.token(otherEmail)

	api.upload(alice, department, "private.txt", "x", version(0))
	api.upload(carol, other, "theirs.txt", "y", version(0))

	nodes, _, _ := api.changesSince(carol, other, 0, 100)
	if nodes["private.txt"] != nil || nodes["theirs.txt"] == nil {
		t.Errorf("got %v", nodes)
	}
	// and a path from one department is not a file in the other
	if response := api.request("GET", filesURL(other, "files", "private.txt", nil), carol, "", nil); response.Code != http.StatusNotFound {
		t.Errorf("gave %d, want 404", response.Code)
	}
}

// ── who may do what ────────────────────────────────────────────────────────

func TestOnlyActiveMembersCanUseAnyFileEndpoint(t *testing.T) {
	api := newTestAPI(t)
	department, alice, _ := api.setup()
	api.upload(alice, department, "secret.txt", "classified", version(0))

	outsiderID, outsiderEmail := api.user("outsider", true)
	_ = outsiderID
	lockedID, lockedEmail := api.user("locked", true)
	api.member(department.ID, lockedID, "member", true)
	lockedOrgID, lockedOrgEmail := api.user("orglocked", true)
	api.member(department.ID, lockedOrgID, "member", false)
	var organizationID int64
	api.pool.QueryRow(context.Background(), `select organization_id from departments where id = $1`, department.ID).Scan(&organizationID)
	api.lockOutOfOrganization(organizationID, lockedOrgID)

	for name, email := range map[string]string{"an outsider": outsiderEmail, "a locked member": lockedEmail, "someone locked out of the organization": lockedOrgEmail} {
		token := api.token(email)
		calls := map[string]*httptest.ResponseRecorder{
			"changes":  api.request("GET", fmt.Sprintf("/api/v1/departments/%s/changes", department.Slug), token, "", nil),
			"download": api.request("GET", filesURL(department, "files", "secret.txt", nil), token, "", nil),
			"upload":   api.upload(token, department, "new.txt", "x", version(0)),
			"delete":   api.request("DELETE", filesURL(department, "nodes", "secret.txt", nil), token, "", nil),
			"folder":   api.request("POST", fmt.Sprintf("/api/v1/departments/%s/folders", department.Slug), token, `{"path":"f"}`, nil),
		}
		for call, response := range calls {
			if response.Code != http.StatusNotFound {
				t.Errorf("%s (%s) gave %d, want 404", name, call, response.Code)
			}
		}
	}

	// and nothing they tried had any effect
	nodes, _, _ := api.changesSince(alice, department, 0, 100)
	if len(nodes) != 1 || nodes["secret.txt"]["deleted"] != false {
		t.Errorf("the department must be untouched, got %v", nodes)
	}
}

func TestFileEndpointsNeedALogin(t *testing.T) {
	api := newTestAPI(t)
	department, _, _ := api.setup()

	for _, call := range [][2]string{
		{"GET", filesURL(department, "files", "a.txt", nil)},
		{"PUT", filesURL(department, "files", "a.txt", nil)},
		{"DELETE", filesURL(department, "nodes", "a.txt", nil)},
		{"POST", fmt.Sprintf("/api/v1/departments/%s/folders", department.Slug)},
		{"GET", fmt.Sprintf("/api/v1/departments/%s/changes", department.Slug)},
		{"GET", fmt.Sprintf("/api/v1/departments/%s", department.Slug)},
		{"DELETE", fmt.Sprintf("/api/v1/departments/%s/membership", department.Slug)},
	} {
		if response := api.request(call[0], call[1], "", "", nil); response.Code != http.StatusUnauthorized {
			t.Errorf("%s %s gave %d, want 401", call[0], call[1], response.Code)
		}
	}
}

func TestBadQueryValuesAreRefused(t *testing.T) {
	api := newTestAPI(t)
	department, alice, _ := api.setup()
	base := "/api/v1/departments/" + department.Slug

	for _, target := range []string{
		base + "/changes?cursor=abc", base + "/changes?cursor=-1", base + "/changes?limit=0", base + "/changes?limit=5000",
	} {
		if response := api.request("GET", target, alice, "", nil); response.Code != http.StatusUnprocessableEntity {
			t.Errorf("%s gave %d, want 422", target, response.Code)
		}
	}
	if response := api.request("PUT", base+"/files?path=a.txt&base_version=x", alice, "x", nil); response.Code != http.StatusUnprocessableEntity {
		t.Errorf("a bad base_version gave %d, want 422", response.Code)
	}
	if response := api.request("PUT", base+"/files?path=a.txt&base_version=0", alice, "x", map[string]string{"X-Modified-At": "yesterday"}); response.Code != http.StatusUnprocessableEntity {
		t.Errorf("a bad X-Modified-At gave %d, want 422", response.Code)
	}
}

// ── two people at once ─────────────────────────────────────────────────────

func TestTwoPeopleCreatingTheSameFileAtOnceGetOneWinnerAndOneConflict(t *testing.T) {
	api := newTestAPI(t)
	department, alice, bob := api.setup()

	results := make(chan int, 2)
	for i, token := range []string{alice, bob} {
		go func(i int, token string) {
			results <- api.upload(token, department, "race.txt", fmt.Sprintf("writer %d", i), version(0)).Code
		}(i, token)
	}
	codes := []int{<-results, <-results}

	created, conflicts := 0, 0
	for _, code := range codes {
		switch code {
		case http.StatusCreated:
			created++
		case http.StatusConflict:
			conflicts++
		}
	}
	if created != 1 || conflicts != 1 {
		t.Errorf("want one 201 and one 409, got %v", codes)
	}
}

// ── telling the other devices ──────────────────────────────────────────────

func TestOtherMembersAreToldWhenAFileChanges(t *testing.T) {
	api := newTestAPI(t)
	department, alice, bob := api.setup()
	server := httptest.NewServer(api.routes)
	defer server.Close()

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	socket, _, err := websocket.Dial(ctx, "ws"+strings.TrimPrefix(server.URL, "http")+"/api/v1/ws", &websocket.DialOptions{
		HTTPHeader: http.Header{"Authorization": []string{"Bearer " + bob}},
	})
	if err != nil {
		t.Fatalf("dial: %v", err)
	}
	defer socket.CloseNow()
	time.Sleep(200 * time.Millisecond) // the connection registers itself with the hub

	api.upload(alice, department, "news.txt", "hello", version(0))

	_, message, err := socket.Read(ctx)
	if err != nil {
		t.Fatalf("bob heard nothing: %v", err)
	}
	var event struct {
		Type string         `json:"type"`
		Data map[string]any `json:"data"`
	}
	json.Unmarshal(message, &event)
	if event.Type != "department.changed" || event.Data["department_id"] != float64(department.ID) || event.Data["cursor"].(float64) < 1 {
		t.Errorf("unexpected message: %s", message)
	}
}

// ── the department page for the app ────────────────────────────────────────

func TestADepartmentShowsItsMembersToEveryMember(t *testing.T) {
	api := newTestAPI(t)
	ownerID, _ := api.user("owner", true)
	organization := api.organization(ownerID)
	department := api.department(organization, "Engineering")
	aliceID, aliceEmail := api.user("alice", true)
	bobID, _ := api.user("bob", true)
	lockedID, _ := api.user("locked", true)
	api.member(department.ID, aliceID, "member", false)
	api.member(department.ID, bobID, "lead", false)
	api.member(department.ID, lockedID, "member", true)

	response := api.request("GET", "/api/v1/departments/"+department.Slug, api.token(aliceEmail), "", nil)

	if response.Code != http.StatusOK {
		t.Fatalf("gave %d: %s", response.Code, response.Body.String())
	}
	data := decoded(response)["data"].(map[string]any)
	members := data["members"].([]any)
	if len(members) != 2 || data["member_count"] != float64(2) {
		t.Errorf("only the two people who can open the department should be listed, got %v", members)
	}
	first := members[0].(map[string]any)
	if _, leaked := first["email"]; leaked {
		t.Error("an ordinary member must not see other people's email addresses")
	}
	if data["can_manage"] != false || data["access_code"] != nil || data["invitations"] != nil {
		t.Errorf("an ordinary member must not see the access code or the invitations: %v", data)
	}
	if data["role"] != "member" || data["organization"] == nil {
		t.Errorf("the usual department fields should be there: %v", data)
	}
}

func TestManagersSeeTheAccessCodeEmailsAndInvitations(t *testing.T) {
	api := newTestAPI(t)
	ownerID, _ := api.user("owner", true)
	department := api.department(api.organization(ownerID), "Engineering")
	leadID, leadEmail := api.user("lead", true)
	api.member(department.ID, leadID, "lead", false)
	encrypted, err := laravelcrypt.Encrypt("ABCDE23456", testAppKey)
	if err != nil {
		t.Fatal(err)
	}
	api.exec(`update departments set access_code_encrypted = $1 where id = $2`, encrypted, department.ID)
	api.exec(`insert into department_invitations (department_id, email, created_at, updated_at) values ($1, 'newhire@example.com', now(), now())`, department.ID)

	response := api.request("GET", "/api/v1/departments/"+department.Slug, api.token(leadEmail), "", nil)

	data := decoded(response)["data"].(map[string]any)
	if data["can_manage"] != true || data["access_code"] != "ABCDE23456" {
		t.Errorf("a lead should see the access code, got %v", data)
	}
	invitations := data["invitations"].([]any)
	if len(invitations) != 1 || invitations[0].(map[string]any)["email"] != "newhire@example.com" {
		t.Errorf("invitations = %v", invitations)
	}
	if data["members"].([]any)[0].(map[string]any)["email"] != leadEmail {
		t.Errorf("a manager should see email addresses, got %v", data["members"])
	}
}

func TestTheAccessCodeIsLeftOutWhenItCannotBeRead(t *testing.T) {
	api := newTestAPI(t)
	ownerID, _ := api.user("owner", true)
	department := api.department(api.organization(ownerID), "Engineering")
	adminID, adminEmail := api.user("admin", true)
	api.member(department.ID, adminID, "admin", false)
	token := api.token(adminEmail)
	show := func() map[string]any {
		return decoded(api.request("GET", "/api/v1/departments/"+department.Slug, token, "", nil))["data"].(map[string]any)
	}

	if show()["access_code"] != nil {
		t.Error("a department without a copyable code has no code to show")
	}

	api.exec(`update departments set access_code_encrypted = 'not-a-payload' where id = $1`, department.ID)
	if data := show(); data["access_code"] != nil || data["can_manage"] != true {
		t.Errorf("an unreadable code must be left out, not break the page: %v", data)
	}

	encrypted, _ := laravelcrypt.Encrypt("ABCDE23456", testAppKey)
	api.exec(`update departments set access_code_encrypted = $1 where id = $2`, encrypted, department.ID)
	api.app.Config.AppKey = ""
	if show()["access_code"] != nil {
		t.Error("without an APP_KEY the code cannot be shown")
	}
}

func TestLeavingADepartmentEndsTheMembership(t *testing.T) {
	api := newTestAPI(t)
	department, alice, bob := api.setup()

	response := api.request("DELETE", "/api/v1/departments/"+department.Slug+"/membership", alice, "", nil)

	if response.Code != http.StatusOK {
		t.Fatalf("gave %d", response.Code)
	}
	if api.request("GET", "/api/v1/departments/"+department.Slug, alice, "", nil).Code != http.StatusNotFound {
		t.Error("someone who left must no longer see the department")
	}
	if slugs := api.departmentSlugs(alice); contains(slugs, department.Slug) {
		t.Error("the department must not be listed any more")
	}
	if api.request("GET", "/api/v1/departments/"+department.Slug, bob, "", nil).Code != http.StatusOK {
		t.Error("the others must be unaffected")
	}
}
