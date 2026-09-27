// Package mail sends plain-text email over SMTP: the same server the Laravel website's MAIL_*
// .env values point at, so a message this API sends looks like it came from the same product.
package mail

import (
	"crypto/tls"
	"fmt"
	"net"
	"net/smtp"
)

// Config is where and how to send mail.
type Config struct {
	Host        string
	Port        string
	Username    string
	Password    string
	FromAddress string
	FromName    string
}

// Send delivers a plain-text email. A Config with no Host is not an error: it just does nothing,
// the same way this API treats other optional configuration (see Config.AppKey) — so it runs fine
// in development without a mail server set up.
func Send(cfg Config, to, subject, body string) error {
	if cfg.Host == "" {
		return nil
	}

	from := cfg.FromAddress
	if from == "" {
		from = "no-reply@localhost"
	}
	fromHeader := from
	if cfg.FromName != "" {
		fromHeader = fmt.Sprintf("%s <%s>", cfg.FromName, from)
	}
	message := "From: " + fromHeader + "\r\n" +
		"To: " + to + "\r\n" +
		"Subject: " + subject + "\r\n" +
		"Content-Type: text/plain; charset=utf-8\r\n" +
		"\r\n" + body + "\r\n"

	client, err := smtp.Dial(net.JoinHostPort(cfg.Host, cfg.Port))
	if err != nil {
		return fmt.Errorf("connect to mail server: %w", err)
	}
	defer client.Close()

	// A local development server (Mailpit and the like) speaks plain SMTP; a real one is asked to
	// upgrade the connection once we know it can, the same way the website's "smtp" mailer does.
	if ok, _ := client.Extension("STARTTLS"); ok {
		if err := client.StartTLS(&tls.Config{ServerName: cfg.Host}); err != nil {
			return fmt.Errorf("start tls: %w", err)
		}
	}
	if cfg.Username != "" {
		if ok, _ := client.Extension("AUTH"); ok {
			if err := client.Auth(smtp.PlainAuth("", cfg.Username, cfg.Password, cfg.Host)); err != nil {
				return fmt.Errorf("authenticate: %w", err)
			}
		}
	}

	if err := client.Mail(from); err != nil {
		return err
	}
	if err := client.Rcpt(to); err != nil {
		return err
	}
	writer, err := client.Data()
	if err != nil {
		return err
	}
	if _, err := writer.Write([]byte(message)); err != nil {
		writer.Close()
		return err
	}
	if err := writer.Close(); err != nil {
		return err
	}
	return client.Quit()
}
