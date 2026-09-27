package realtime

import (
	"context"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/coder/websocket"
)

// serveDepartments starts a test server whose WebSocket listens to the given departments.
func serveDepartments(t *testing.T, hub *Hub, departmentIDs ...int64) *websocket.Conn {
	t.Helper()

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		hub.Serve(w, r, departmentIDs)
	}))
	t.Cleanup(server.Close)

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	socket, _, err := websocket.Dial(ctx, "ws"+strings.TrimPrefix(server.URL, "http"), nil)
	if err != nil {
		t.Fatalf("dial: %v", err)
	}
	t.Cleanup(func() { socket.CloseNow() })
	return socket
}

// waitForListeners waits until the hub has the expected number of open connections for a department.
func waitForListeners(t *testing.T, hub *Hub, departmentID int64, want int) {
	t.Helper()
	for i := 0; i < 100; i++ {
		hub.lock.Lock()
		got := len(hub.connections[departmentID])
		hub.lock.Unlock()
		if got == want {
			return
		}
		time.Sleep(20 * time.Millisecond)
	}
	t.Fatalf("department %d never reached %d listeners", departmentID, want)
}

func TestMessageReachesOnlyTheDepartmentsListeners(t *testing.T) {
	hub := NewHub()
	inDepartment7 := serveDepartments(t, hub, 7, 8)
	inDepartment9 := serveDepartments(t, hub, 9)
	waitForListeners(t, hub, 7, 1)
	waitForListeners(t, hub, 9, 1)

	hub.SendToDepartment(7, Message{Type: "department.changed", Data: map[string]any{"department_id": 7, "cursor": 42}})

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	_, received, err := inDepartment7.Read(ctx)
	if err != nil {
		t.Fatalf("read: %v", err)
	}
	if want := `{"type":"department.changed","data":{"cursor":42,"department_id":7}}`; string(received) != want {
		t.Errorf("received %s, want %s", received, want)
	}

	shortContext, cancelShort := context.WithTimeout(context.Background(), 200*time.Millisecond)
	defer cancelShort()
	if _, unexpected, err := inDepartment9.Read(shortContext); err == nil {
		t.Errorf("a listener of another department received %s", unexpected)
	}
}

func TestClosedConnectionsAreForgotten(t *testing.T) {
	hub := NewHub()
	socket := serveDepartments(t, hub, 7, 8)
	waitForListeners(t, hub, 7, 1)
	waitForListeners(t, hub, 8, 1)

	socket.Close(websocket.StatusNormalClosure, "bye")

	waitForListeners(t, hub, 7, 0)
	waitForListeners(t, hub, 8, 0)
	hub.lock.Lock()
	defer hub.lock.Unlock()
	if len(hub.connections) != 0 {
		t.Errorf("the hub still holds %d departments", len(hub.connections))
	}
}

func TestSendToDepartmentWithNobodyListeningIsFine(t *testing.T) {
	NewHub().SendToDepartment(1, Message{Type: "department.changed"})
}
