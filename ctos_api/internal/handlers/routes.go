package handlers

import (
	"net/http"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
	"github.com/go-chi/cors"
)

// Routes is the list of every URL of the API and the handler that answers it.
func (app *App) Routes() http.Handler {
	router := chi.NewRouter()

	router.Use(middleware.RealIP)
	router.Use(middleware.Logger)
	router.Use(middleware.Recoverer)
	router.Use(cors.Handler(cors.Options{
		AllowedOrigins: app.Config.CORSAllowedOrigins,
		AllowedMethods: []string{"GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"},
		AllowedHeaders: []string{"Authorization", "Content-Type", "Accept"},
		MaxAge:         300,
	}))

	router.Get("/up", func(w http.ResponseWriter, r *http.Request) {
		w.Write([]byte("OK"))
	})

	router.Route("/api/v1", func(api chi.Router) {
		api.Post("/auth/login", app.login)
		api.Post("/auth/register", app.register)
		api.Post("/auth/verify-email", app.verifyEmail)
		api.Post("/auth/verify-email/resend", app.resendVerificationCode)
		api.Post("/auth/forgot-password", app.forgotPassword)
		api.Post("/auth/reset-password", app.resetPassword)

		api.Group(func(api chi.Router) {
			api.Use(app.requireLogin)

			api.Post("/auth/logout", app.logout)
			api.Get("/ws", app.openWebSocket)

			api.Get("/organizations", app.listOrganizations)
			api.Post("/organizations", app.createOrganization)

			api.Get("/departments", app.listDepartments)
			api.Post("/departments/join", app.joinDepartment)

			api.Get("/departments/{department}", app.showDepartment)
			api.Delete("/departments/{department}/membership", app.leaveDepartment)
			api.Post("/departments/{department}/invitations", app.shareDepartment)
			api.Delete("/departments/{department}/invitations/{invitation}", app.withdrawInvitation)
			api.Post("/departments/{department}/access-code", app.regenerateAccessCode)

			// Secure Drop: end-to-end encrypted files between department members (docs/secure-drop.md).
			api.Post("/encryption-keys/challenge", app.encryptionKeyChallenge)
			api.Post("/encryption-keys", app.storeEncryptionKey)
			api.Get("/encryption-keys", app.showEncryptionKey)
			api.Get("/encryption-keys/download", app.downloadEncryptionKey)
			api.Delete("/encryption-keys", app.deleteEncryptionKey)

			api.Get("/secure-drops", app.listSecureDrops)
			api.Get("/departments/{department}/secure-drop-recipients", app.secureDropRecipients)
			api.Post("/departments/{department}/secure-drop", app.storeSecureDrop)
			api.Get("/secure-drops/{secureDrop}", app.showSecureDrop)
			api.Get("/secure-drops/{secureDrop}/download", app.downloadSecureDrop)
			api.Delete("/secure-drops/{secureDrop}", app.deleteSecureDrop)

			// The files of a department. A path is always in the query, never in the URL.
			api.Get("/departments/{department}/changes", app.listChanges)
			api.Get("/departments/{department}/files", app.downloadFile)
			api.Put("/departments/{department}/files", app.uploadFile)
			api.Post("/departments/{department}/folders", app.createFolder)
			api.Delete("/departments/{department}/nodes", app.deleteNode)
		})
	})

	return router
}
