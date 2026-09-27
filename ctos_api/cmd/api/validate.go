package main

import (
	"fmt"
	"net"
	"regexp"
	"strconv"
	"strings"
)

// The checks of the command line arguments. Each function returns a message
// describing what is wrong, or "" when the value is fine.

// validateNoExtraArguments: the program only takes --host and --port, so
// anything left over (a typo like "port 8080" without the dashes) is a mistake.
func validateNoExtraArguments(arguments []string) string {
	if len(arguments) > 0 {
		return fmt.Sprintf("unexpected argument %q (options start with --, for example --port 8080)", arguments[0])
	}
	return ""
}

// hostnameLabel is one dot-separated part of a hostname: letters, digits and
// hyphens, not starting or ending with a hyphen, at most 63 characters.
var hostnameLabel = regexp.MustCompile(`^[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?$`)

// validateHost: empty means "all addresses". Otherwise it must be an IP
// address (127.0.0.1, ::1) or a hostname (localhost, api.example.com).
func validateHost(host string) string {
	if host == "" {
		return ""
	}
	if net.ParseIP(host) != nil {
		return ""
	}
	if len(host) > 253 {
		return fmt.Sprintf("--host %q is too long", host)
	}
	labels := strings.Split(host, ".")
	for _, label := range labels {
		if !hostnameLabel.MatchString(label) {
			return fmt.Sprintf("--host %q is not an IP address or a hostname", host)
		}
	}
	// A hostname never ends in a number, so 999.1.1.1 is a mistyped IP address, not a hostname.
	if _, err := strconv.Atoi(labels[len(labels)-1]); err == nil {
		return fmt.Sprintf("--host %q looks like an IP address but is not a valid one", host)
	}
	return ""
}

// validatePort: a whole number from 1 to 65535.
func validatePort(port string) string {
	number, err := strconv.Atoi(port)
	if err != nil {
		return fmt.Sprintf("--port %q is not a number", port)
	}
	if number < 1 || number > 65535 {
		return fmt.Sprintf("--port %d is out of range (use 1 to 65535)", number)
	}
	return ""
}
