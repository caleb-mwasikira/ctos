package handlers

import (
	"testing"

	"golang.org/x/crypto/bcrypt"
)

func TestNormalizeAccessCode(t *testing.T) {
	for typed, want := range map[string]string{
		"ABCDE23456":    "ABCDE23456",
		"  abcde23456 ": "ABCDE23456",
		"AbCdE23456\n":  "ABCDE23456",
	} {
		if got := normalizeAccessCode(typed); got != want {
			t.Errorf("normalizeAccessCode(%q) = %q, want %q", typed, got, want)
		}
	}
}

// The website (PHP) writes the access code hash; this API has to accept it.
// This hash is PHP's password_hash("ABCDE23456", PASSWORD_BCRYPT): the "$2y$" flavour.
func TestAccessCodeHashFromLaravelIsAccepted(t *testing.T) {
	const laravelHash = "$2y$10$MW/Eh/pLWxW5jOhE9sTD3OhM8NIRyRVHB/5Sgh3ZiEkKUrZJ/9RH6"

	if bcrypt.CompareHashAndPassword([]byte(laravelHash), []byte(normalizeAccessCode(" abcde23456 "))) != nil {
		t.Error("the right code, typed in lower case, should match the hash")
	}
	if bcrypt.CompareHashAndPassword([]byte(laravelHash), []byte("WRONGWRONG")) == nil {
		t.Error("a wrong code must not match")
	}
}
