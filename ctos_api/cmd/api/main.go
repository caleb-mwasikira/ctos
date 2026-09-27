// Command api is the CTOS API: the desktop and Android apps talk to it. It
// shares its database with the Laravel website.
package main

import (
	"context"
	"flag"
	"fmt"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"ctos_api/internal/config"
	"ctos_api/internal/database"
	"ctos_api/internal/handlers"
	"ctos_api/internal/realtime"
	"ctos_api/internal/storage"
)

func main() {
	settings, err := config.Load()
	if err != nil {
		log.Fatalf("config: %v", err)
	}

	// --host and --port win over APP_HOST and APP_PORT from the environment or .env.
	var (
		host, port string
	)
	flag.StringVar(&host, "host", settings.Host, "address to listen on, for example 127.0.0.1 (empty means all addresses)")
	flag.StringVar(&port, "port", settings.Port, "port to listen on")
	flag.Usage = func() {
		fmt.Fprint(os.Stderr, `CTOS API: the desktop and Android apps.

Usage:
  api [--host ADDRESS] [--port PORT]
  api --help

Options:
`)
		flag.PrintDefaults()
		fmt.Fprint(os.Stderr, `
Everything else is configured with environment variables, or a .env file in the
directory the program is started from (see .env.example): DB_* and
CORS_ALLOWED_ORIGINS. Real environment variables win over .env.

Examples:
  api                          listen on every address, port 9090 (or APP_PORT)
  api --port 8080
  api --host 127.0.0.1 --port 9090
`)
	}
	flag.Parse()

	message := validateNoExtraArguments(flag.Args())
	if message != "" {
		fmt.Fprintf(os.Stderr, "error: %s\nRun with --help for usage.\n", message)
		os.Exit(2)
	}
	message = validateHost(host)
	if message != "" {
		fmt.Fprintf(os.Stderr, "error: %s\nRun with --help for usage.\n", message)
		os.Exit(2)
	}
	message = validatePort(port)
	if message != "" {
		fmt.Fprintf(os.Stderr, "error: %s\nRun with --help for usage.\n", message)
		os.Exit(2)
	}

	settings.Host = host
	settings.Port = port

	db, err := database.Connect(context.Background(), settings.DatabaseURL)
	if err != nil {
		log.Fatalf("database: %v", err)
	}
	defer db.Close()

	files, err := storage.New(settings.FilesDir)
	if err != nil {
		log.Fatalf("files folder %s: %v", settings.FilesDir, err)
	}

	app := &handlers.App{
		DB:     db,
		Config: settings,
		Hub:    realtime.NewHub(),
		Files:  files,
	}

	// Stop cleanly on Ctrl+C or when the process manager sends SIGTERM.
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	// No WriteTimeout: it would cut off the long-lived WebSocket connections.
	server := &http.Server{
		Addr:              settings.Host + ":" + settings.Port,
		Handler:           app.Routes(),
		ReadHeaderTimeout: 10 * time.Second,
	}
	go func() {
		log.Printf("CTOS API listening on %s", server.Addr)
		if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Fatalf("server: %v", err)
		}
	}()

	<-ctx.Done()
	log.Println("shutting down")
	shutdownContext, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	server.Shutdown(shutdownContext)
}
