package handlers

import (
	"log"
	"net/http"

	"ctos_api/internal/database"
)

// openWebSocket: GET /api/v1/ws
// Keeps a connection open and tells the app when a department it belongs to
// changes, so the app can fetch the changes without polling.
func (app *App) openWebSocket(w http.ResponseWriter, r *http.Request) {
	user := r.Context().Value(userContextKey).(*database.User)

	departments, err := app.DB.GetDepartmentsOfUser(r.Context(), user.ID)
	if err != nil {
		log.Printf("websocket: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}
	departmentIDs := []int64{}
	for _, department := range departments {
		departmentIDs = append(departmentIDs, department.ID)
	}

	app.Hub.Serve(w, r, departmentIDs)
}
