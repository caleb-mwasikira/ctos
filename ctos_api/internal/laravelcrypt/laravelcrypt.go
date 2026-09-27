// Package laravelcrypt reads values the Laravel website encrypted with its APP_KEY.
package laravelcrypt

import (
	"bytes"
	"crypto/aes"
	"crypto/cipher"
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"strings"
)

func parseKey(appKey string) ([]byte, error) {
	key, err := base64.StdEncoding.DecodeString(strings.TrimPrefix(appKey, "base64:"))
	if err != nil || len(key) != 32 {
		return nil, errors.New("APP_KEY must be a base64 32 byte key")
	}
	return key, nil
}

type payload struct {
	IV    string `json:"iv"`
	Value string `json:"value"`
	MAC   string `json:"mac"`
	Tag   string `json:"tag"`
}

func macOf(key []byte, iv string, value string) string {
	mac := hmac.New(sha256.New, key)
	mac.Write([]byte(iv + value))
	return hex.EncodeToString(mac.Sum(nil))
}

// Decrypt reads a value Laravel encrypted with Crypt::encryptString (which is what an `encrypted`
// model cast or attribute does), using the website's APP_KEY, for example "base64:rFJMR6Zq...".
// Laravel's format is base64 of {"iv", "value", "mac"}: AES-256-CBC, with an HMAC-SHA256 over iv+value.
func Decrypt(encrypted string, appKey string) (string, error) {
	key, err := parseKey(appKey)
	if err != nil {
		return "", err
	}

	payloadJSON, err := base64.StdEncoding.DecodeString(encrypted)
	if err != nil {
		return "", errors.New("the value is not a Laravel payload")
	}
	var parts payload
	if err := json.Unmarshal(payloadJSON, &parts); err != nil {
		return "", errors.New("the value is not a Laravel payload")
	}

	// The mac proves the payload was made with this key and was not changed.
	if !hmac.Equal([]byte(macOf(key, parts.IV, parts.Value)), []byte(parts.MAC)) {
		return "", errors.New("the MAC is invalid (wrong APP_KEY?)")
	}

	iv, err := base64.StdEncoding.DecodeString(parts.IV)
	if err != nil || len(iv) != aes.BlockSize {
		return "", errors.New("the iv is invalid")
	}
	ciphertext, err := base64.StdEncoding.DecodeString(parts.Value)
	if err != nil || len(ciphertext) == 0 || len(ciphertext)%aes.BlockSize != 0 {
		return "", errors.New("the value is invalid")
	}

	block, err := aes.NewCipher(key)
	if err != nil {
		return "", err
	}
	plain := make([]byte, len(ciphertext))
	cipher.NewCBCDecrypter(block, iv).CryptBlocks(plain, ciphertext)

	// Remove the PKCS#7 padding.
	padding := int(plain[len(plain)-1])
	if padding < 1 || padding > aes.BlockSize || padding > len(plain) {
		return "", errors.New("the padding is invalid")
	}
	return string(plain[:len(plain)-padding]), nil
}

// Encrypt makes a value the website can decrypt with Crypt::decryptString. The API does not need
// it itself; the tests use it to make the values the website would have stored.
func Encrypt(plain string, appKey string) (string, error) {
	key, err := parseKey(appKey)
	if err != nil {
		return "", err
	}
	block, err := aes.NewCipher(key)
	if err != nil {
		return "", err
	}

	iv := make([]byte, aes.BlockSize)
	if _, err := rand.Read(iv); err != nil {
		return "", err
	}
	padding := aes.BlockSize - len(plain)%aes.BlockSize
	padded := append([]byte(plain), bytes.Repeat([]byte{byte(padding)}, padding)...)
	ciphertext := make([]byte, len(padded))
	cipher.NewCBCEncrypter(block, iv).CryptBlocks(ciphertext, padded)

	ivText := base64.StdEncoding.EncodeToString(iv)
	valueText := base64.StdEncoding.EncodeToString(ciphertext)
	out, err := json.Marshal(payload{IV: ivText, Value: valueText, MAC: macOf(key, ivText, valueText)})
	if err != nil {
		return "", err
	}
	return base64.StdEncoding.EncodeToString(out), nil
}
