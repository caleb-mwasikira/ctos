// Package realtime pushes small "something changed" messages to connected
// desktop and Android apps over WebSockets. A message never carries file
// data: the app reacts to it by asking the API what changed.
package realtime

import (
	"context"
	"encoding/json"
	"net/http"
	"sync"
	"time"

	"github.com/coder/websocket"
)

// Message is one frame sent down a WebSocket. Type says what happened
// ("department.changed"); Data holds the details (department_id, cursor).
type Message struct {
	Type string         `json:"type"`
	Data map[string]any `json:"data,omitempty"`
}

// Hub keeps track of who is connected. A connection listens to every
// department its user belonged to when it connected; a user who joins a
// department later gets its messages after reconnecting.
type Hub struct {
	lock        sync.Mutex
	connections map[int64]map[chan []byte]bool // department id -> the outbox of every open connection
}

func NewHub() *Hub {
	return &Hub{connections: map[int64]map[chan []byte]bool{}}
}

// SendToDepartment delivers the message to every open connection listening to
// the department. A connection that is too slow to keep up simply misses the
// message: the app catches up the next time it asks what changed.
func (hub *Hub) SendToDepartment(departmentID int64, message Message) {
	messageJSON, err := json.Marshal(message)
	if err != nil {
		return
	}

	hub.lock.Lock()
	defer hub.lock.Unlock()
	for outbox := range hub.connections[departmentID] {
		select {
		case outbox <- messageJSON:
		default:
		}
	}
}

// Serve turns the HTTP request into a WebSocket and keeps it open, sending
// whatever arrives for the given departments, until the client disconnects.
func (hub *Hub) Serve(w http.ResponseWriter, r *http.Request, departmentIDs []int64) {
	// Native apps do not send an Origin header and log in with a Bearer token,
	// not a cookie, so there is no cross-site risk to guard against here.
	socket, err := websocket.Accept(w, r, &websocket.AcceptOptions{InsecureSkipVerify: true})
	if err != nil {
		return
	}
	defer socket.CloseNow()

	// outbox is where SendToDepartment drops messages for this connection.
	outbox := make(chan []byte, 32)
	hub.lock.Lock()
	for _, departmentID := range departmentIDs {
		if hub.connections[departmentID] == nil {
			hub.connections[departmentID] = map[chan []byte]bool{}
		}
		hub.connections[departmentID][outbox] = true
	}
	hub.lock.Unlock()

	// When this connection ends, take its outbox out of the hub again.
	defer func() {
		hub.lock.Lock()
		defer hub.lock.Unlock()
		for _, departmentID := range departmentIDs {
			delete(hub.connections[departmentID], outbox)
			if len(hub.connections[departmentID]) == 0 {
				delete(hub.connections, departmentID)
			}
		}
	}()

	// disconnected is cancelled when the client closes the connection.
	disconnected := socket.CloseRead(r.Context())
	keepAlive := time.NewTicker(30 * time.Second)
	defer keepAlive.Stop()

	for {
		select {
		case messageJSON := <-outbox:
			writeContext, cancel := context.WithTimeout(disconnected, 10*time.Second)
			err := socket.Write(writeContext, websocket.MessageText, messageJSON)
			cancel()
			if err != nil {
				return
			}
		case <-keepAlive.C:
			pingContext, cancel := context.WithTimeout(disconnected, 10*time.Second)
			err := socket.Ping(pingContext)
			cancel()
			if err != nil {
				return
			}
		case <-disconnected.Done():
			return
		}
	}
}
