package laravelcrypt

import (
	"strings"
	"testing"
)

// Made by Laravel itself: Crypt::encryptString("ABCDE23456") with a throwaway APP_KEY.
const (
	laravelKey     = "base64:s6vDWPFguM12fwZXelMk4+MPfwnXm6BkKpnyCu55JLE="
	laravelPayload = "eyJpdiI6IjdEVDVNcXIxKzJ2UlBuR0VrdWcxSXc9PSIsInZhbHVlIjoid0NuTHZTWmRIQllxcnFLN0xWZndaUT09IiwibWFjIjoiZTA0ZjZkMmI5ZmVlZDI3YmUyY2U3YmExODczZDM3NjJjMzY5MDBhZDdmNjc2NmM3MzhkMDBjMDIxOGUwNzJmOCIsInRhZyI6IiJ9"
)

func TestDecryptReadsWhatLaravelWrote(t *testing.T) {
	plain, err := Decrypt(laravelPayload, laravelKey)
	if err != nil || plain != "ABCDE23456" {
		t.Fatalf("Decrypt = %q, %v; want ABCDE23456", plain, err)
	}
}

func TestEncryptedValuesRoundTripForAnyLength(t *testing.T) {
	for _, text := range []string{"", "a", "ABCDE23456", "exactly16bytes!!", strings.Repeat("x", 100), "ünïcode ✓"} {
		encrypted, err := Encrypt(text, laravelKey)
		if err != nil {
			t.Fatalf("Encrypt(%q): %v", text, err)
		}
		if got, err := Decrypt(encrypted, laravelKey); err != nil || got != text {
			t.Errorf("round trip of %q gave %q, %v", text, got, err)
		}
	}
}

func TestEncryptingTwiceGivesDifferentPayloads(t *testing.T) {
	first, _ := Encrypt("same", laravelKey)
	second, _ := Encrypt("same", laravelKey)
	if first == second {
		t.Error("each payload needs its own iv")
	}
}

func TestDecryptRefusesWhatIsWrong(t *testing.T) {
	otherKey := "base64:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
	for name, attempt := range map[string]func() error{
		"another key":   func() error { _, err := Decrypt(laravelPayload, otherKey); return err },
		"a short key":   func() error { _, err := Decrypt(laravelPayload, "base64:c2hvcnQ="); return err },
		"not base64":    func() error { _, err := Decrypt("%%%", laravelKey); return err },
		"not a payload": func() error { _, err := Decrypt("bm90IGpzb24=", laravelKey); return err },
		"a changed payload": func() error {
			_, err := Decrypt(laravelPayload[:len(laravelPayload)-8]+"AAAAAAAA", laravelKey)
			return err
		},
		"empty": func() error { _, err := Decrypt("", laravelKey); return err },
	} {
		if attempt() == nil {
			t.Errorf("%s should be refused", name)
		}
	}
}
