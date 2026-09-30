package handlers

import (
	"fmt"
	"regexp"
	"strings"
	"unicode/utf8"
)

// Every check of what a client sent lives in this file, as plain functions.
//
// Each validate function looks at one value and returns a message describing
// what is wrong with it, or "" when it is fine. A handler uses them like this:
//
//	message := validateEmail(input.Email)
//	if message != "" {
//	    writeInvalid(w, "email", message)
//	    return
//	}
//
// Checks that need the database (does this department exist?) are done in
// the handler, right next to the query that answers them.

// validateRequiredText: the text must not be empty and at most maxLength characters.
func validateRequiredText(label string, text string, maxLength int) string {
	if strings.TrimSpace(text) == "" {
		return fmt.Sprintf("The %s field is required.", label)
	}
	if utf8.RuneCountInString(text) > maxLength {
		return fmt.Sprintf("The %s field must not be greater than %d characters.", label, maxLength)
	}
	return ""
}

var emailPattern = regexp.MustCompile(`^[^@\s]+@[^@\s]+\.[^@\s]+$`)

// validateEmail: required, looks like an email address, at most 255 characters.
func validateEmail(email string) string {
	if strings.TrimSpace(email) == "" {
		return "The email field is required."
	}
	if !emailPattern.MatchString(email) {
		return "The email field must be a valid email address."
	}
	if utf8.RuneCountInString(email) > 255 {
		return "The email field must not be greater than 255 characters."
	}
	return ""
}

// validateLoginPassword: only checks that something was typed. Whether it is
// the right password is decided by comparing with the stored hash.
func validateLoginPassword(password string) string {
	if password == "" {
		return "The password field is required."
	}
	return ""
}

// validateNewPassword: at least 8 characters and matches its confirmation, the same rule
// Password::defaults() applies on the website (it is not customized there).
func validateNewPassword(password, confirmation string) string {
	if password == "" {
		return "The password field is required."
	}
	if utf8.RuneCountInString(password) < 8 {
		return "The password field must be at least 8 characters."
	}
	if password != confirmation {
		return "The password field confirmation does not match."
	}
	return ""
}

// validateVerificationCode: the app only ever sends back exactly what it was emailed, so this
// just checks something was typed — whether it is the right code is decided next, by comparing
// with the stored hash.
func validateVerificationCode(code string) string {
	if strings.TrimSpace(code) == "" {
		return "The code field is required."
	}
	return ""
}

// validateNodePath: the path of a file or folder inside a department, like "reports/summary.md".
// It must be relative, with no empty, "." or ".." parts, no backslashes and no control characters
// (the website applies the same rules).
func validateNodePath(path string) string {
	if path == "" {
		return "The path field is required."
	}
	if len(path) > 1024 {
		return "The path field must not be greater than 1024 characters."
	}
	if !utf8.ValidString(path) || strings.ContainsRune(path, '\\') || strings.IndexFunc(path, func(r rune) bool { return r < 0x20 || r == 0x7f }) >= 0 {
		return "The path field contains characters that are not allowed."
	}
	for _, segment := range strings.Split(path, "/") {
		if segment == "" || segment == "." || segment == ".." {
			return "The path field must be a relative path with no empty, \".\" or \"..\" parts."
		}
		if len(segment) > 255 {
			return "Each part of the path field must not be greater than 255 bytes."
		}
	}
	return ""
}
