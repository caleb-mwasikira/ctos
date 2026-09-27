package handlers

import (
	"encoding/json"
	"log"
	"net/http"

	"ctos_api/internal/database"
)

func organizationResponse(organization *database.OwnedOrganization) map[string]any {
	return map[string]any{
		"id":               organization.ID,
		"name":             organization.Name,
		"slug":             organization.Slug,
		"department_count": organization.DepartmentCount,
		"is_owner":         organization.IsOwner,
	}
}

// listOrganizations: GET /api/v1/organizations
// Every organization the user owns, or belongs to through a department, even ones that have no
// departments yet (a fresh one made with createOrganization). Compare with GET /departments,
// which only ever shows departments the user has actually joined.
func (app *App) listOrganizations(w http.ResponseWriter, r *http.Request) {
	user := r.Context().Value(userContextKey).(*database.User)

	organizations, err := app.DB.GetOrganizationsOfUser(r.Context(), user.ID)
	if err != nil {
		log.Printf("list organizations: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}

	data := []map[string]any{}
	for i := range organizations {
		data = append(data, organizationResponse(&organizations[i]))
	}
	writeJSON(w, http.StatusOK, map[string]any{"data": data})
}

// createOrganization: POST /api/v1/organizations  {"name"}
// The user becomes its owner, the only one who can add departments to it (until they add one and
// share it). Matches OrganizationController@store on the website.
func (app *App) createOrganization(w http.ResponseWriter, r *http.Request) {
	user := r.Context().Value(userContextKey).(*database.User)

	var input struct {
		Name string `json:"name"`
	}
	if err := json.NewDecoder(r.Body).Decode(&input); err != nil {
		writeJSON(w, http.StatusBadRequest, map[string]any{"message": "Request body must be valid JSON."})
		return
	}
	if message := validateRequiredText("name", input.Name, 100); message != "" {
		writeInvalid(w, "name", message)
		return
	}

	organization, err := app.DB.CreateOrganization(r.Context(), user.ID, input.Name)
	if err != nil {
		log.Printf("create organization: %v", err)
		writeJSON(w, http.StatusInternalServerError, map[string]any{"message": "Server error."})
		return
	}
	writeJSON(w, http.StatusCreated, map[string]any{"data": organizationResponse(organization)})
}
