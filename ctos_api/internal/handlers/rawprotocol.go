package handlers

import (
	"bufio"
	"encoding/binary"
	"encoding/json"
	"log"
)

// uploadFile answers PUT .../files with a small custom protocol instead of a plain HTTP body:
// the request line and headers are still ordinary HTTP (so authentication, the path, its base
// version and the rest of the usual checks are unchanged), but once those pass, the connection
// stops being HTTP. This spares a big upload the framing HTTP would otherwise add to the request
// body and to the answer, at the cost of needing its own tiny protocol for exactly this one call.
//
// A client asks for it the way an HTTP Upgrade is normally asked for:
//
//	PUT /api/v1/departments/{department}/files?path=...&base_version=N HTTP/1.1
//	Authorization: Bearer ...
//	Upgrade: ctos-upload/1
//	Connection: Upgrade
//	Content-Length: 8492            (the file's exact size; required, and no chunked encoding)
//	X-Content-SHA256: ...           (optional)
//	X-Modified-At: ...              (optional)
//
// A request that does not ask to upgrade, or is missing its Content-Length, or declares more than
// the server's upload limit, is refused the ordinary HTTP way (426, 422 or 413) and the connection
// stays plain HTTP. Otherwise the server answers, itself still one ordinary (if minimal) HTTP
// response:
//
//	HTTP/1.1 101 Switching Protocols
//	Upgrade: ctos-upload/1
//	Connection: Upgrade
//
// and from that point on the connection carries no HTTP at all: the client writes exactly
// Content-Length bytes of the file's content, and the server answers with one frame and closes
// the connection (this API asks for one file per connection: simpler to get right on both ends
// than sharing the raw connection between uploads, at the cost of a new connection each time):
//
//	4 bytes   big-endian uint32   status, the same codes writeJSON would have used (200, 201,
//	                              409, 422, 500, ...)
//	4 bytes   big-endian uint32   N, how many bytes of JSON follow
//	N bytes                       UTF-8 JSON, the same shape writeJSON would have sent
//
// A connection that closes before its answer frame arrives means the upload did not go through
// (the most common reason: the raw content itself never fully arrived, so there was nothing left
// to tell the client that it does not already know).
const uploadProtocol = "ctos-upload/1"

// writeRawAnswer sends one answer frame and nothing more: once a connection has switched out of
// HTTP (see uploadProtocol above) this is the only way left to answer it. The caller closes the
// connection afterwards.
func writeRawAnswer(w *bufio.Writer, status int, body any) {
	encoded, err := json.Marshal(body)
	if err != nil {
		log.Printf("raw upload: encode answer: %v", err)
		return
	}
	var header [8]byte
	binary.BigEndian.PutUint32(header[0:4], uint32(status))
	binary.BigEndian.PutUint32(header[4:8], uint32(len(encoded)))
	if _, err := w.Write(header[:]); err != nil {
		return
	}
	if _, err := w.Write(encoded); err != nil {
		return
	}
	w.Flush()
}

// writeRawInvalid is writeRawAnswer in the shape writeInvalid uses for a single field's error.
func writeRawInvalid(w *bufio.Writer, status int, field string, message string) {
	writeRawAnswer(w, status, map[string]any{
		"message": message,
		"errors":  map[string][]string{field: {message}},
	})
}
