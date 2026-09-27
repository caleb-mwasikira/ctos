// Package config loads runtime settings from the environment. Variable
// names deliberately mirror the Laravel app's .env (DB_*) so both services
// can be pointed at the same database by copying values across.
package config

import (
	"bufio"
	"fmt"
	"net/url"
	"os"
	"strconv"
	"strings"
)

type Config struct {
	AppName string
	Host    string // address to listen on; empty means all addresses
	Port    string

	DatabaseURL string

	// Where uploaded files are kept. It is the Laravel website's "local" disk
	// (storage/app/private), so the website can serve the files it lists.
	FilesDir string
	// The largest file that can be uploaded, in bytes.
	MaxUploadBytes int64

	// The website's APP_KEY. The website stores each department's access code encrypted with it,
	// so this API needs it to show the code to the people who manage the department.
	// Empty means the code is not shown.
	AppKey string

	// The website's own address, for links a department-share email sends people to (its
	// department page, or the sign-up page). Not where this API itself listens.
	WebsiteURL string

	// Same SMTP server as the website's MAIL_* .env values, so a department-share invite is
	// emailed the same way the website's other mail is. An empty MailHost just skips sending,
	// the same way an empty AppKey just hides the access code: a convenience for development.
	MailHost        string
	MailPort        string
	MailUsername    string
	MailPassword    string
	MailFromAddress string
	MailFromName    string

	CORSAllowedOrigins []string
}

// Load reads an optional .env file (real environment variables win), then
// builds the Config.
func Load() (*Config, error) {
	loadDotEnv(".env")

	c := &Config{
		AppName: get("APP_NAME", "CTOS"),
		Host:    get("APP_HOST", ""),
		Port:    get("APP_PORT", "9090"),

		FilesDir:   get("FILES_DIR", "../storage/app/private"),
		AppKey:     get("APP_KEY", ""),
		WebsiteURL: strings.TrimRight(get("WEBSITE_URL", "http://localhost:8000"), "/"),

		MailHost:        get("MAIL_HOST", ""),
		MailPort:        get("MAIL_PORT", "587"),
		MailUsername:    get("MAIL_USERNAME", ""),
		MailPassword:    get("MAIL_PASSWORD", ""),
		MailFromAddress: get("MAIL_FROM_ADDRESS", "support@ctos.com"),
		MailFromName:    get("MAIL_FROM_NAME", "CTOS"),
	}

	maxUploadMB, err := strconv.ParseInt(get("MAX_UPLOAD_MB", "512"), 10, 64)
	if err != nil || maxUploadMB < 1 {
		return nil, fmt.Errorf("MAX_UPLOAD_MB must be a whole number of at least 1")
	}
	c.MaxUploadBytes = maxUploadMB * 1024 * 1024

	for _, o := range strings.Split(get("CORS_ALLOWED_ORIGINS", "*"), ",") {
		if o = strings.TrimSpace(o); o != "" {
			c.CORSAllowedOrigins = append(c.CORSAllowedOrigins, o)
		}
	}

	c.DatabaseURL = get("DATABASE_URL", "")
	if c.DatabaseURL == "" {
		u := url.URL{
			Scheme: "postgres",
			User:   url.UserPassword(get("DB_USERNAME", "postgres"), get("DB_PASSWORD", "")),
			Host:   fmt.Sprintf("%s:%s", get("DB_HOST", "127.0.0.1"), get("DB_PORT", "5432")),
			Path:   get("DB_DATABASE", "ctos"),
		}
		q := url.Values{}
		q.Set("sslmode", get("DB_SSLMODE", "disable"))
		u.RawQuery = q.Encode()
		c.DatabaseURL = u.String()
	}

	return c, nil
}

func get(key, def string) string {
	if v, ok := os.LookupEnv(key); ok && v != "" {
		return v
	}
	return def
}

func loadDotEnv(path string) {
	f, err := os.Open(path)
	if err != nil {
		return
	}
	defer f.Close()

	sc := bufio.NewScanner(f)
	for sc.Scan() {
		line := strings.TrimSpace(sc.Text())
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		k, v, ok := strings.Cut(line, "=")
		if !ok {
			continue
		}
		k = strings.TrimSpace(k)
		v = strings.TrimSpace(v)
		if len(v) >= 2 && (v[0] == '"' && v[len(v)-1] == '"' || v[0] == '\'' && v[len(v)-1] == '\'') {
			v = v[1 : len(v)-1]
		}
		if _, exists := os.LookupEnv(k); !exists {
			os.Setenv(k, v)
		}
	}
}
