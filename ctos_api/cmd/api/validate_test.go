package main

import "testing"

func TestValidateHost(t *testing.T) {
	valid := []string{"", "127.0.0.1", "0.0.0.0", "::1", "localhost", "api.example.com", "my-host-1", "999.1.1.1x"}
	for _, host := range valid {
		if message := validateHost(host); message != "" {
			t.Errorf("host %q should be valid, got %q", host, message)
		}
	}

	invalid := []string{"-bad", "bad-", "has space", "under_score", "a..b", "http://localhost", "localhost:9090", "999.1.1.1", "1.2.3"}
	for _, host := range invalid {
		if message := validateHost(host); message == "" {
			t.Errorf("host %q should be invalid", host)
		}
	}
}

func TestValidatePort(t *testing.T) {
	valid := []string{"1", "80", "9090", "65535"}
	for _, port := range valid {
		if message := validatePort(port); message != "" {
			t.Errorf("port %q should be valid, got %q", port, message)
		}
	}

	invalid := []string{"", "0", "-1", "65536", "abc", "90.5", "9090 "}
	for _, port := range invalid {
		if message := validatePort(port); message == "" {
			t.Errorf("port %q should be invalid", port)
		}
	}
}

func TestValidateNoExtraArguments(t *testing.T) {
	if message := validateNoExtraArguments(nil); message != "" {
		t.Errorf("no arguments should be fine, got %q", message)
	}
	if message := validateNoExtraArguments([]string{"port", "8080"}); message == "" {
		t.Error("a leftover argument should be rejected")
	}
}
