// Package storage keeps the content of uploaded files on disk, under the folder the Laravel
// website's "local" disk uses, so the website can serve what this API stores.
package storage

import (
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"io"
	"os"
	"path/filepath"
	"strconv"
	"strings"
)

// ErrTooLarge is returned by Save when the content is bigger than the limit.
var ErrTooLarge = errors.New("the file is too large")

// ErrTruncated is returned by SaveExactly when fewer bytes arrived than promised: the connection
// was lost partway through the upload.
var ErrTruncated = errors.New("fewer bytes arrived than promised")

type Store struct {
	root string
}

// Saved describes content that was written to disk.
type Saved struct {
	// Key is where the content is, relative to the root ("nodes/7/3f9a..."). It is what the
	// nodes table keeps in storage_path.
	Key    string
	Size   int64
	SHA256 string
}

// New makes the store, creating the folders it needs.
func New(root string) (*Store, error) {
	if err := os.MkdirAll(filepath.Join(root, "nodes"), 0o755); err != nil {
		return nil, err
	}
	return &Store{root: root}, nil
}

// Save reads the content to the end, at most maxBytes of it, and keeps it under a new random
// key. The content is written to a temporary file first, so a failed or oversized upload leaves
// nothing behind.
func (store *Store) Save(departmentID int64, content io.Reader, maxBytes int64) (Saved, error) {
	// One byte more than allowed, to tell "exactly the limit" from "over it".
	return store.save(departmentID, io.LimitReader(content, maxBytes+1), func(size int64) error {
		if size > maxBytes {
			return ErrTooLarge
		}
		return nil
	})
}

// SaveExactly reads exactly size bytes into storage, no more and no fewer: for a transport (the
// raw-upload protocol; see uploadFile) that already knows the content's length upfront, and has
// checked it fits the limit before the transfer even started. Fewer bytes than promised is
// ErrTruncated, not silently accepted as a shorter file.
func (store *Store) SaveExactly(departmentID int64, content io.Reader, size int64) (Saved, error) {
	return store.save(departmentID, io.LimitReader(content, size), func(read int64) error {
		if read != size {
			return ErrTruncated
		}
		return nil
	})
}

// save writes limited (already bounded to how much this call is willing to read) to a temporary
// file under a new random key, computing its checksum as it goes. checkSize sees how many bytes
// were actually read and can refuse the result (too large, or fewer than promised); either way,
// nothing is left behind on refusal or on any other error.
func (store *Store) save(departmentID int64, limited io.Reader, checkSize func(read int64) error) (Saved, error) {
	directory := filepath.Join(store.root, "nodes", strconv.FormatInt(departmentID, 10))
	if err := os.MkdirAll(directory, 0o755); err != nil {
		return Saved{}, err
	}

	temporary, err := os.CreateTemp(directory, ".upload-*")
	if err != nil {
		return Saved{}, err
	}
	defer os.Remove(temporary.Name()) // does nothing once it has been renamed

	hash := sha256.New()
	size, err := io.Copy(io.MultiWriter(temporary, hash), limited)
	if closeErr := temporary.Close(); err == nil {
		err = closeErr
	}
	if err != nil {
		return Saved{}, err
	}
	if err := checkSize(size); err != nil {
		return Saved{}, err
	}

	random := make([]byte, 16)
	if _, err := rand.Read(random); err != nil {
		return Saved{}, err
	}
	key := "nodes/" + strconv.FormatInt(departmentID, 10) + "/" + hex.EncodeToString(random)

	// The website's web server reads these files, and it may run as another user.
	if err := os.Chmod(temporary.Name(), 0o644); err != nil {
		return Saved{}, err
	}
	if err := os.Rename(temporary.Name(), filepath.Join(store.root, key)); err != nil {
		return Saved{}, err
	}
	return Saved{Key: key, Size: size, SHA256: hex.EncodeToString(hash.Sum(nil))}, nil
}

// Open opens stored content. A key that points outside the store is refused.
func (store *Store) Open(key string) (*os.File, error) {
	if !strings.HasPrefix(key, "nodes/") || strings.Contains(key, "..") {
		return nil, os.ErrNotExist
	}
	return os.Open(filepath.Join(store.root, filepath.FromSlash(key)))
}

// Remove deletes stored content. Content that is already gone is not an error.
func (store *Store) Remove(key string) {
	if !strings.HasPrefix(key, "nodes/") || strings.Contains(key, "..") {
		return
	}
	os.Remove(filepath.Join(store.root, filepath.FromSlash(key)))
}
