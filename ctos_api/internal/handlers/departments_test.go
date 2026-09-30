package handlers

import "testing"

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

// The website (PHP) writes the access code hash; this API has to produce the exact same digest
// for the same code, or a code made on one and typed on the other would not be found.
// This hash is PHP's hash('sha256', 'ABCDE23456').
func TestAccessCodeHashMatchesLaravels(t *testing.T) {
	const laravelHash = "82a15347a056ccbf451fbd1c865eed21ec190069eedd32b0c9dc6452887811cc"

	if got := hashAccessCode(" abcde23456 "); got != laravelHash {
		t.Errorf("hashAccessCode(%q) = %q, want %q", " abcde23456 ", got, laravelHash)
	}
	if hashAccessCode("WRONGWRONG") == laravelHash {
		t.Error("a wrong code must not match")
	}
}
