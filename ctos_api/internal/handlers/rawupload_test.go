package handlers

import (
	"bufio"
	"bytes"
	"encoding/binary"
	"encoding/json"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/http/httptest"
	"net/textproto"
	"net/url"
	"strconv"
	"strings"
	"testing"
)

// Tests of the raw-upload protocol (see rawprotocol.go). httptest.NewRecorder cannot hijack a
// connection, so these need a real listening server and a real socket, unlike the rest of the
// file tests.

// rawAnswer is what one raw-protocol upload got back, whichever way it was answered: as the raw
// frame (once upgraded) or, when the request was refused before the upgrade, as an ordinary HTTP
// response. Either way the caller sees one status and one decoded JSON body.
type rawAnswer struct {
	status int
	body   map[string]any
}

// rawUpload performs one raw-protocol upload against a real listening server. err is only set
// when the connection or the protocol itself broke (not for a refused upload, which is a normal
// [rawAnswer] with a 4xx/5xx status).
func rawUpload(t *testing.T, server *httptest.Server, token string, departmentSlug string, path string, content []byte, baseVersion *int64, extraHeaders map[string]string) (*rawAnswer, error) {
	t.Helper()
	address := strings.TrimPrefix(server.URL, "http://")

	conn, err := net.Dial("tcp", address)
	if err != nil {
		return nil, err
	}
	defer conn.Close()

	target := fmt.Sprintf("http://%s/api/v1/departments/%s/files?path=%s", address, departmentSlug, url.QueryEscape(path))
	if baseVersion != nil {
		target += "&base_version=" + strconv.FormatInt(*baseVersion, 10)
	}
	request, err := http.NewRequest(http.MethodPut, target, bytes.NewReader(content))
	if err != nil {
		return nil, err
	}
	request.ContentLength = int64(len(content))
	request.Header.Set("Authorization", "Bearer "+token)
	request.Header.Set("Upgrade", uploadProtocol)
	request.Header.Set("Connection", "Upgrade")
	for key, value := range extraHeaders {
		request.Header.Set(key, value)
	}
	if err := request.Write(conn); err != nil {
		return nil, fmt.Errorf("write request: %w", err)
	}

	reader := bufio.NewReader(conn)
	protocolReader := textproto.NewReader(reader)
	statusLine, err := protocolReader.ReadLine()
	if err != nil {
		return nil, fmt.Errorf("read status line: %w", err)
	}
	header, err := protocolReader.ReadMIMEHeader()
	if err != nil {
		return nil, fmt.Errorf("read response headers: %w", err)
	}

	parts := strings.SplitN(statusLine, " ", 3)
	if len(parts) < 2 {
		return nil, fmt.Errorf("unexpected status line %q", statusLine)
	}
	status, err := strconv.Atoi(parts[1])
	if err != nil {
		return nil, fmt.Errorf("unexpected status line %q", statusLine)
	}

	if status == http.StatusSwitchingProtocols {
		var frameHeader [8]byte
		if _, err := io.ReadFull(reader, frameHeader[:]); err != nil {
			return nil, fmt.Errorf("read answer header: %w", err)
		}
		answerStatus := int(binary.BigEndian.Uint32(frameHeader[0:4]))
		length := binary.BigEndian.Uint32(frameHeader[4:8])
		payload := make([]byte, length)
		if _, err := io.ReadFull(reader, payload); err != nil {
			return nil, fmt.Errorf("read answer body: %w", err)
		}
		var body map[string]any
		if len(payload) > 0 {
			if err := json.Unmarshal(payload, &body); err != nil {
				return nil, fmt.Errorf("decode answer: %w", err)
			}
		}
		return &rawAnswer{status: answerStatus, body: body}, nil
	}

	// refused before the upgrade: an ordinary HTTP response, framed the ordinary way
	var body map[string]any
	if length := header.Get("Content-Length"); length != "" {
		n, _ := strconv.Atoi(length)
		payload := make([]byte, n)
		if _, err := io.ReadFull(reader, payload); err != nil {
			return nil, fmt.Errorf("read plain response body: %w", err)
		}
		if len(payload) > 0 {
			json.Unmarshal(payload, &body)
		}
	}
	return &rawAnswer{status: status, body: body}, nil
}

func TestRawUploadCreatesAFileThatCanBeDownloaded(t *testing.T) {
	api := newTestAPI(t)
	department, alice, bob := api.setup()
	server := httptest.NewServer(api.routes)
	defer server.Close()

	answer, err := rawUpload(t, server, alice, department.Slug, "notes.txt", []byte("hello world"), version(0), nil)

	if err != nil {
		t.Fatal(err)
	}
	if answer.status != http.StatusCreated {
		t.Fatalf("gave %d: %v", answer.status, answer.body)
	}
	node := answer.body["data"].(map[string]any)
	if node["path"] != "notes.txt" || node["version"] != float64(1) || node["checksum"] != sha("hello world") {
		t.Errorf("unexpected node: %v", node)
	}

	download := api.request("GET", filesURL(department, "files", "notes.txt", nil), bob, "", nil)
	if download.Code != http.StatusOK || download.Body.String() != "hello world" {
		t.Errorf("download gave %d %q", download.Code, download.Body.String())
	}
}

func TestRawUploadIntoAFolderCreatesTheFoldersOnTheWay(t *testing.T) {
	api := newTestAPI(t)
	department, alice, _ := api.setup()
	server := httptest.NewServer(api.routes)
	defer server.Close()

	answer, err := rawUpload(t, server, alice, department.Slug, "reports/2026/q3.xlsx", []byte("numbers"), version(0), nil)

	if err != nil || answer.status != http.StatusCreated {
		t.Fatalf("gave %v (err %v)", answer, err)
	}
	nodes, _, _ := api.changesSince(alice, department, 0, 100)
	if nodes["reports"]["type"] != "folder" || nodes["reports/2026"]["type"] != "folder" || nodes["reports/2026/q3.xlsx"]["type"] != "file" {
		t.Errorf("want two folders and a file, got %v", nodes)
	}
}

func TestRawUploadReplacingAFileNeedsTheRightVersion(t *testing.T) {
	api := newTestAPI(t)
	department, alice, bob := api.setup()
	server := httptest.NewServer(api.routes)
	defer server.Close()
	rawUpload(t, server, alice, department.Slug, "plan.md", []byte("v1"), version(0), nil)

	stale, err := rawUpload(t, server, bob, department.Slug, "plan.md", []byte("stale"), version(0), nil)
	if err != nil {
		t.Fatal(err)
	}
	if stale.status != http.StatusConflict {
		t.Fatalf("gave %d, want 409: %v", stale.status, stale.body)
	}
	current := stale.body["current"].(map[string]any)
	if current["version"] != float64(1) || current["checksum"] != sha("v1") {
		t.Errorf("the answer should describe the current file, got %v", current)
	}

	ok, err := rawUpload(t, server, bob, department.Slug, "plan.md", []byte("v2"), version(1), nil)
	if err != nil {
		t.Fatal(err)
	}
	if ok.status != http.StatusOK || ok.body["data"].(map[string]any)["version"] != float64(2) {
		t.Fatalf("gave %d: %v", ok.status, ok.body)
	}
}

func TestRawUploadWithAWrongChecksumIsRefusedAndKeepsNothing(t *testing.T) {
	api := newTestAPI(t)
	department, alice, _ := api.setup()
	server := httptest.NewServer(api.routes)
	defer server.Close()

	answer, err := rawUpload(t, server, alice, department.Slug, "a.txt", []byte("content"), version(0), map[string]string{"X-Content-SHA256": sha("something else")})

	if err != nil {
		t.Fatal(err)
	}
	if answer.status != http.StatusUnprocessableEntity {
		t.Fatalf("gave %d, want 422: %v", answer.status, answer.body)
	}
	if nodes, _, _ := api.changesSince(alice, department, 0, 100); len(nodes) != 0 {
		t.Errorf("nothing should have been saved, got %v", nodes)
	}
}

func TestRawUploadRespectsXModifiedAt(t *testing.T) {
	api := newTestAPI(t)
	department, alice, _ := api.setup()
	server := httptest.NewServer(api.routes)
	defer server.Close()

	answer, err := rawUpload(t, server, alice, department.Slug, "old.txt", []byte("x"), version(0), map[string]string{"X-Modified-At": "1700000000000"})

	if err != nil || answer.status != http.StatusCreated {
		t.Fatalf("gave %v (err %v)", answer, err)
	}
	if answer.body["data"].(map[string]any)["modified_at"] != float64(1700000000000) {
		t.Errorf("modified_at = %v", answer.body["data"].(map[string]any)["modified_at"])
	}
}

func TestRawUploadTooLargeIsRefusedWithoutUpgrading(t *testing.T) {
	api := newTestAPI(t)
	api.app.Config.MaxUploadBytes = 5
	department, alice, _ := api.setup()
	server := httptest.NewServer(api.routes)
	defer server.Close()

	answer, err := rawUpload(t, server, alice, department.Slug, "big.txt", []byte("too many bytes"), version(0), nil)

	if err != nil {
		t.Fatal(err)
	}
	if answer.status != http.StatusRequestEntityTooLarge {
		t.Errorf("gave %d, want 413: %v", answer.status, answer.body)
	}
}

func TestRawUploadOnlyWorksForActiveMembers(t *testing.T) {
	api := newTestAPI(t)
	department, alice, _ := api.setup()
	server := httptest.NewServer(api.routes)
	defer server.Close()
	outsiderID, outsiderEmail := api.user("outsider", true)
	_ = outsiderID
	outsider := api.token(outsiderEmail)

	answer, err := rawUpload(t, server, outsider, department.Slug, "a.txt", []byte("x"), version(0), nil)

	if err != nil {
		t.Fatal(err)
	}
	if answer.status != http.StatusNotFound {
		t.Errorf("gave %d, want 404: %v", answer.status, answer.body)
	}
	if nodes, _, _ := api.changesSince(alice, department, 0, 100); len(nodes) != 0 {
		t.Errorf("nothing should have been saved, got %v", nodes)
	}
}

func TestRawUploadNudgesOtherMembers(t *testing.T) {
	api := newTestAPI(t)
	department, alice, bob := api.setup()
	server := httptest.NewServer(api.routes)
	defer server.Close()
	_, cursor, _ := api.changesSince(bob, department, 0, 100)

	answer, err := rawUpload(t, server, alice, department.Slug, "news.txt", []byte("hello"), version(0), nil)

	if err != nil || answer.status != http.StatusCreated {
		t.Fatalf("gave %v (err %v)", answer, err)
	}
	nodes, _, _ := api.changesSince(bob, department, cursor, 100)
	if nodes["news.txt"] == nil {
		t.Errorf("the other member should see the change, got %v", nodes)
	}
}

func TestPlainUploadStillWorksWithoutTheUpgradeHeader(t *testing.T) {
	// The website's browser uploads never send "Upgrade", so uploadFile must still behave exactly
	// as it did before the raw protocol existed.
	api := newTestAPI(t)
	department, alice, _ := api.setup()

	response := api.upload(alice, department, "plain.txt", "hello", version(0))

	if response.Code != http.StatusCreated {
		t.Fatalf("gave %d: %s", response.Code, response.Body.String())
	}
}
