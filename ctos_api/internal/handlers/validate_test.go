package handlers

import (
	"strings"
	"testing"
)

func TestValidateEmail(t *testing.T) {
	for _, email := range []string{"a@example.com", "first.last+tag@sub.example.co.ke"} {
		if message := validateEmail(email); message != "" {
			t.Errorf("%q should be valid, got %q", email, message)
		}
	}

	for _, email := range []string{"", "  ", "plain", "a@b", "a b@example.com", "@example.com", strings.Repeat("a", 250) + "@example.com"} {
		if message := validateEmail(email); message == "" {
			t.Errorf("%q should be invalid", email)
		}
	}
}

func TestValidateRequiredText(t *testing.T) {
	if message := validateRequiredText("name", "Alice", 10); message != "" {
		t.Errorf("should be valid, got %q", message)
	}
	if message := validateRequiredText("name", "   ", 10); message != "The name field is required." {
		t.Errorf("blank text should be required, got %q", message)
	}
	if message := validateRequiredText("name", "héllo wörld!", 10); message == "" {
		t.Error("12 characters should be too long for 10")
	}
	if message := validateRequiredText("name", "héllo wörld", 11); message != "" {
		t.Errorf("11 characters (counted as characters, not bytes) should fit 11, got %q", message)
	}
}

func TestValidateLoginPassword(t *testing.T) {
	if message := validateLoginPassword(""); message == "" {
		t.Error("an empty password should be rejected")
	}
	if message := validateLoginPassword("x"); message != "" {
		t.Errorf("any typed password passes this check, got %q", message)
	}
}

func TestValidateNodePath(t *testing.T) {
	for _, path := range []string{"a.txt", "reports/2026/q3.xlsx", "with space/and ünïcode.txt", "dots.in.name/file.tar.gz", ".hidden/file", strings.Repeat("a", 255)} {
		if message := validateNodePath(path); message != "" {
			t.Errorf("%q should be valid, got %q", path, message)
		}
	}

	invalid := []string{
		"", "/absolute", "trailing/", "a//b", "a/./b", "./a", "../up", "a/../b", "..",
		"back\\slash", "control\x01char", "del\x7fchar", "bad\xffutf8",
		strings.Repeat("a", 256), strings.Repeat("a/", 600),
	}
	for _, path := range invalid {
		if message := validateNodePath(path); message == "" {
			t.Errorf("%q should be invalid", path)
		}
	}
}
