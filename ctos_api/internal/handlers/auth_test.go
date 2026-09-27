package handlers

import "testing"

func TestParseBearerToken(t *testing.T) {
	tokenID, secret, ok := parseBearerToken("Bearer 12|abcdef")
	if !ok || tokenID != 12 || secret != "abcdef" {
		t.Errorf(`"Bearer 12|abcdef" gave %d, %q, %v`, tokenID, secret, ok)
	}

	tokenID, secret, ok = parseBearerToken("Bearer abcdef")
	if !ok || tokenID != 0 || secret != "abcdef" {
		t.Errorf("a token without an id should give id 0, got %d, %q, %v", tokenID, secret, ok)
	}

	tokenID, secret, ok = parseBearerToken("Bearer x|abcdef")
	if !ok || tokenID != 0 || secret != "x|abcdef" {
		t.Errorf("a non-numeric id should be treated as part of the secret, got %d, %q, %v", tokenID, secret, ok)
	}

	for _, header := range []string{"", "Bearer ", "Basic 12|abc", "bearer 12|abc", "12|abc"} {
		if _, _, ok := parseBearerToken(header); ok {
			t.Errorf("%q should not be accepted", header)
		}
	}
}

func TestFailureCounterLocksAfterTooManyWrongAttempts(t *testing.T) {
	var counter failureCounter

	for attempt := 1; attempt < maxFailures; attempt++ {
		counter.add("a@example.com")
		if counter.isLocked("a@example.com") {
			t.Fatalf("should not be locked after %d wrong attempts", attempt)
		}
	}
	counter.add("a@example.com")
	if !counter.isLocked("a@example.com") {
		t.Error("should be locked after the maximum number of wrong attempts")
	}
	if counter.isLocked("b@example.com") {
		t.Error("another key must not be locked")
	}

	counter.clear("a@example.com")
	if counter.isLocked("a@example.com") {
		t.Error("a right attempt clears the count")
	}
}
