package handlers

import (
	"encoding/json"
	"errors"
	"fmt"
	"log"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"time"

	"github.com/go-chi/chi/v5"

	"ctos_api/internal/database"
	"ctos_api/internal/realtime"
	"ctos_api/internal/storage"
)

// The files of a department. Every member may read and write them. A path is always given in the
// query (?path=reports/summary.md), so it needs no escaping in the URL.

// memberDepartment finds the department in the URL and checks that the user can open it. Someone
// who cannot gets "not found": they have no business knowing the department exists.
func (app *App) memberDepartment(w http.ResponseWriter, r *http.Request) (*database.MemberDepartment, bool) {
	user := r.Context().Value(userContextKey).(*database.User)

	department, err := app.DB.GetDepartmentOfUserBySlug(r.Context(), user.ID, chi.URLParam(r, "department"))
	if err != nil {
		log.Printf("find department: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return nil, false
	}
	if department == nil {
		writeJSON(w, http.StatusNotFound, map[string]any{"message": "Not found."})
		return nil, false
	}
	return department, true
}

// nodeResponse is how a file or folder goes out in JSON. modified_at is milliseconds since 1970.
func nodeResponse(node *database.Node) map[string]any {
	var modifiedAt any
	if node.ModifiedAt != nil {
		modifiedAt = node.ModifiedAt.UnixMilli()
	}
	return map[string]any{
		"id":          node.ID,
		"path":        node.Path,
		"type":        node.Type,
		"size":        node.Size,
		"checksum":    node.Checksum,
		"version":     node.Version,
		"modified_at": modifiedAt,
		"deleted":     node.DeletedAt != nil,
	}
}

// announce tells every device listening to the department that it changed. The message is only a
// nudge: each device then asks what changed since its own cursor.
func (app *App) announce(departmentID int64, cursor int64) {
	app.Hub.SendToDepartment(departmentID, realtime.Message{
		Type: "department.changed",
		Data: map[string]any{"department_id": departmentID, "cursor": cursor},
	})
}

// optionalVersion reads ?base_version=. ok is false (and the answer already written) when it is not a whole number.
func optionalVersion(w http.ResponseWriter, r *http.Request) (version *int64, ok bool) {
	text := r.URL.Query().Get("base_version")
	if text == "" {
		return nil, true
	}
	number, err := strconv.ParseInt(text, 10, 64)
	if err != nil || number < 0 {
		writeInvalid(w, "base_version", "The base version field must be a whole number of at least 0.")
		return nil, false
	}
	return &number, true
}

func writeConflict(w http.ResponseWriter, message string, current *database.Node) {
	body := map[string]any{"message": message, "current": nil}
	if current != nil {
		body["current"] = nodeResponse(current)
	}
	writeJSON(w, http.StatusConflict, body)
}

// listChanges: GET /api/v1/departments/{department}/changes?cursor=0&limit=500
// What changed after the cursor. A device starts at cursor 0 (everything), then sends back the
// cursor it was given. When "more" is true it should ask again straight away.
func (app *App) listChanges(w http.ResponseWriter, r *http.Request) {
	department, ok := app.memberDepartment(w, r)
	if !ok {
		return
	}

	cursor, err := strconv.ParseInt(r.URL.Query().Get("cursor"), 10, 64)
	if r.URL.Query().Get("cursor") == "" {
		cursor, err = 0, nil
	}
	if err != nil || cursor < 0 {
		writeInvalid(w, "cursor", "The cursor field must be a whole number of at least 0.")
		return
	}
	limit := 500
	if text := r.URL.Query().Get("limit"); text != "" {
		if limit, err = strconv.Atoi(text); err != nil || limit < 1 || limit > 1000 {
			writeInvalid(w, "limit", "The limit field must be between 1 and 1000.")
			return
		}
	}

	changed, next, more, err := app.DB.GetChanges(r.Context(), department.ID, cursor, limit)
	if err != nil {
		log.Printf("list changes: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}

	nodes := []map[string]any{}
	for i := range changed {
		nodes = append(nodes, nodeResponse(&changed[i].Node))
	}
	writeJSON(w, http.StatusOK, map[string]any{"data": map[string]any{"cursor": next, "more": more, "nodes": nodes}})
}

// uploadFile: PUT /api/v1/departments/{department}/files?path=...&base_version=N
// The body is the file itself. base_version is 0 to create a file and the version the caller last
// saw to replace one; a file that changed in the meantime answers 409 with its current state.
// X-Content-SHA256 (optional) makes the server check the file arrived whole; X-Modified-At
// (optional, milliseconds) is when the file was last changed on the device.
//
// A caller may instead ask to upgrade the connection (Upgrade: ctos-upload/1, Connection: Upgrade,
// a Content-Length header, no chunked encoding) and send the content as raw bytes once the server
// agrees; see uploadProtocol in rawprotocol.go. The website's browser uploads cannot do this (a
// page has no way to speak a protocol like it), so they simply do not ask, and this behaves
// exactly as it always has; the KMM app does ask, for its own uploads.
func (app *App) uploadFile(w http.ResponseWriter, r *http.Request) {
	department, ok := app.memberDepartment(w, r)
	if !ok {
		return
	}
	user := r.Context().Value(userContextKey).(*database.User)

	path := r.URL.Query().Get("path")
	if message := validateNodePath(path); message != "" {
		writeInvalid(w, "path", message)
		return
	}
	baseVersion, ok := optionalVersion(w, r)
	if !ok {
		return
	}
	modifiedAt := time.Now().UTC()
	if text := r.Header.Get("X-Modified-At"); text != "" {
		millis, err := strconv.ParseInt(text, 10, 64)
		if err != nil || millis < 0 {
			writeInvalid(w, "modified_at", "The X-Modified-At header must be milliseconds since 1970.")
			return
		}
		modifiedAt = time.UnixMilli(millis).UTC()
	}

	if r.Header.Get("Upgrade") == uploadProtocol {
		app.uploadFileRaw(w, r, department, user, path, baseVersion, modifiedAt)
		return
	}

	limit := app.Config.MaxUploadBytes
	saved, err := app.Files.Save(department.ID, http.MaxBytesReader(w, r.Body, limit), limit)
	var tooBig *http.MaxBytesError
	if errors.Is(err, storage.ErrTooLarge) || errors.As(err, &tooBig) {
		writeJSON(w, http.StatusRequestEntityTooLarge, map[string]any{"message": fmt.Sprintf("The file is larger than the limit of %d MB.", limit/(1024*1024))})
		return
	}
	if err != nil {
		log.Printf("upload file: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}
	if want := r.Header.Get("X-Content-SHA256"); want != "" && !strings.EqualFold(want, saved.SHA256) {
		app.Files.Remove(saved.Key)
		writeInvalid(w, "file", "The file was damaged on its way here. Please try again.")
		return
	}

	result, err := app.DB.SaveFile(r.Context(), database.SaveFileInput{
		DepartmentID: department.ID, UserID: user.ID, Path: path, BaseVersion: baseVersion,
		Size: saved.Size, Checksum: saved.SHA256, StorageKey: saved.Key, ModifiedAt: modifiedAt,
	})
	var conflict *database.ConflictError
	switch {
	case errors.As(err, &conflict):
		app.Files.Remove(saved.Key)
		writeConflict(w, "This file changed on the server since you last saw it.", conflict.Current)
		return
	case errors.Is(err, database.ErrIsFolder):
		app.Files.Remove(saved.Key)
		writeInvalid(w, "path", "A folder already has this name.")
		return
	case errors.Is(err, database.ErrParentIsFile):
		app.Files.Remove(saved.Key)
		writeInvalid(w, "path", "A file has the name of one of the folders on this path.")
		return
	case err != nil:
		app.Files.Remove(saved.Key)
		log.Printf("save file: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}

	if result.Unchanged {
		app.Files.Remove(saved.Key) // the file already holds exactly this content
	} else {
		if result.ReplacedKey != nil {
			app.Files.Remove(*result.ReplacedKey)
		}
		app.announce(department.ID, result.ChangeID)
	}

	status := http.StatusOK
	if result.Created {
		status = http.StatusCreated
	}
	writeJSON(w, status, map[string]any{"data": nodeResponse(result.Node)})
}

// uploadFileRaw is uploadFile once the caller has asked to upgrade the connection: the path,
// base_version and X-Modified-At have already been checked the ordinary way; what is left is the
// Content-Length, the upgrade itself, and then exactly the same save-and-answer logic as the
// plain upload above, just carried over the raw connection instead of an HTTP response.
func (app *App) uploadFileRaw(w http.ResponseWriter, r *http.Request, department *database.MemberDepartment, user *database.User, path string, baseVersion *int64, modifiedAt time.Time) {
	if !strings.Contains(strings.ToLower(r.Header.Get("Connection")), "upgrade") {
		writeInvalid(w, "connection", `An upgrade also needs a "Connection: Upgrade" header.`)
		return
	}
	if r.Header.Get("Content-Length") == "" {
		writeInvalid(w, "content", "A Content-Length header is required to upload this way.")
		return
	}
	size := r.ContentLength
	limit := app.Config.MaxUploadBytes
	if size < 0 {
		writeInvalid(w, "content", "Chunked encoding cannot be used to upload this way.")
		return
	}
	if size > limit {
		writeJSON(w, http.StatusRequestEntityTooLarge, map[string]any{"message": fmt.Sprintf("The file is larger than the limit of %d MB.", limit/(1024*1024))})
		return
	}

	hijacker, ok := w.(http.Hijacker)
	if !ok {
		log.Printf("upload file: this connection cannot be upgraded")
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}
	conn, rw, err := hijacker.Hijack()
	if err != nil {
		log.Printf("upload file: hijack: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}
	defer conn.Close()
	if _, err := rw.Writer.WriteString("HTTP/1.1 101 Switching Protocols\r\nUpgrade: " + uploadProtocol + "\r\nConnection: Upgrade\r\n\r\n"); err != nil {
		return
	}
	if rw.Writer.Flush() != nil {
		return
	}

	// From here on the connection is not HTTP any more: every answer, success or failure, goes
	// through writeRawAnswer instead of writeJSON, over the same connection, and then it closes.
	saved, err := app.Files.SaveExactly(department.ID, rw.Reader, size)
	if err != nil {
		if !errors.Is(err, storage.ErrTruncated) {
			log.Printf("upload file: %v", err)
		}
		return // the content never fully arrived: there is nothing to tell a client that already knows
	}
	if want := r.Header.Get("X-Content-SHA256"); want != "" && !strings.EqualFold(want, saved.SHA256) {
		app.Files.Remove(saved.Key)
		writeRawInvalid(rw.Writer, http.StatusUnprocessableEntity, "file", "The file was damaged on its way here. Please try again.")
		return
	}

	result, err := app.DB.SaveFile(r.Context(), database.SaveFileInput{
		DepartmentID: department.ID, UserID: user.ID, Path: path, BaseVersion: baseVersion,
		Size: saved.Size, Checksum: saved.SHA256, StorageKey: saved.Key, ModifiedAt: modifiedAt,
	})
	var conflict *database.ConflictError
	switch {
	case errors.As(err, &conflict):
		app.Files.Remove(saved.Key)
		body := map[string]any{"message": "This file changed on the server since you last saw it.", "current": nil}
		if conflict.Current != nil {
			body["current"] = nodeResponse(conflict.Current)
		}
		writeRawAnswer(rw.Writer, http.StatusConflict, body)
		return
	case errors.Is(err, database.ErrIsFolder):
		app.Files.Remove(saved.Key)
		writeRawInvalid(rw.Writer, http.StatusUnprocessableEntity, "path", "A folder already has this name.")
		return
	case errors.Is(err, database.ErrParentIsFile):
		app.Files.Remove(saved.Key)
		writeRawInvalid(rw.Writer, http.StatusUnprocessableEntity, "path", "A file has the name of one of the folders on this path.")
		return
	case err != nil:
		app.Files.Remove(saved.Key)
		log.Printf("save file: %v", err)
		writeRawAnswer(rw.Writer, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}

	if result.Unchanged {
		app.Files.Remove(saved.Key)
	} else {
		if result.ReplacedKey != nil {
			app.Files.Remove(*result.ReplacedKey)
		}
		app.announce(department.ID, result.ChangeID)
	}

	status := http.StatusOK
	if result.Created {
		status = http.StatusCreated
	}
	writeRawAnswer(rw.Writer, status, map[string]any{"data": nodeResponse(result.Node)})
}

// downloadFile: GET /api/v1/departments/{department}/files?path=...
// The file itself. Range requests work, so a big download can be resumed.
func (app *App) downloadFile(w http.ResponseWriter, r *http.Request) {
	department, ok := app.memberDepartment(w, r)
	if !ok {
		return
	}

	path := r.URL.Query().Get("path")
	if message := validateNodePath(path); message != "" {
		writeInvalid(w, "path", message)
		return
	}
	node, err := app.DB.GetLiveNode(r.Context(), department.ID, path)
	if err != nil {
		log.Printf("download file: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}
	if node == nil || node.Type != database.NodeFile || node.StorageKey == nil {
		writeJSON(w, http.StatusNotFound, map[string]any{"message": "Not found."})
		return
	}
	file, err := app.Files.Open(*node.StorageKey)
	if err != nil {
		log.Printf("download file %d: %v", node.ID, err)
		writeJSON(w, http.StatusNotFound, map[string]any{"message": "The content of this file is missing."})
		return
	}
	defer file.Close()

	modified := time.Now()
	if node.ModifiedAt != nil {
		modified = *node.ModifiedAt
	}
	if node.Checksum != nil {
		w.Header().Set("ETag", `"`+*node.Checksum+`"`)
	}
	w.Header().Set("X-Version", strconv.FormatInt(node.Version, 10))
	w.Header().Set("X-Modified-At", strconv.FormatInt(modified.UnixMilli(), 10))
	w.Header().Set("Content-Type", "application/octet-stream")
	w.Header().Set("Content-Disposition", "attachment; filename*=UTF-8''"+url.PathEscape(node.Name))
	http.ServeContent(w, r, node.Name, modified, file)
}

// createFolder: POST /api/v1/departments/{department}/folders  {"path": "reports/2026"}
func (app *App) createFolder(w http.ResponseWriter, r *http.Request) {
	department, ok := app.memberDepartment(w, r)
	if !ok {
		return
	}
	user := r.Context().Value(userContextKey).(*database.User)

	var input struct {
		Path string `json:"path"`
	}
	if err := json.NewDecoder(r.Body).Decode(&input); err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]any{"message": "Request body must be valid JSON."})
		return
	}
	if message := validateNodePath(input.Path); message != "" {
		writeInvalid(w, "path", message)
		return
	}

	folder, changeID, err := app.DB.CreateFolder(r.Context(), department.ID, user.ID, input.Path)
	switch {
	case errors.Is(err, database.ErrIsFile):
		writeInvalid(w, "path", "A file already has this name.")
		return
	case errors.Is(err, database.ErrParentIsFile):
		writeInvalid(w, "path", "A file has the name of one of the folders on this path.")
		return
	case err != nil:
		log.Printf("create folder: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}

	status := http.StatusOK
	if changeID != 0 {
		status = http.StatusCreated
		app.announce(department.ID, changeID)
	}
	writeJSON(w, status, map[string]any{"data": nodeResponse(folder)})
}

// deleteNode: DELETE /api/v1/departments/{department}/nodes?path=...&base_version=N
// Deletes a file, or a folder with everything in it. A path that is already gone answers 404, which
// a device can treat as "done".
func (app *App) deleteNode(w http.ResponseWriter, r *http.Request) {
	department, ok := app.memberDepartment(w, r)
	if !ok {
		return
	}
	user := r.Context().Value(userContextKey).(*database.User)

	path := r.URL.Query().Get("path")
	if message := validateNodePath(path); message != "" {
		writeInvalid(w, "path", message)
		return
	}
	baseVersion, ok := optionalVersion(w, r)
	if !ok {
		return
	}

	keys, changeID, err := app.DB.DeleteNode(r.Context(), department.ID, user.ID, path, baseVersion)
	var conflict *database.ConflictError
	switch {
	case errors.Is(err, database.ErrNotFound):
		writeJSON(w, http.StatusNotFound, map[string]any{"message": "Not found."})
		return
	case errors.As(err, &conflict):
		writeConflict(w, "This file changed on the server since you last saw it.", conflict.Current)
		return
	case err != nil:
		log.Printf("delete node: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}

	for _, key := range keys {
		app.Files.Remove(key)
	}
	app.announce(department.ID, changeID)
	writeJSON(w, http.StatusOK, map[string]any{"data": map[string]any{"deleted": len(keys)}})
}
