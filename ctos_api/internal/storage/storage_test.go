package storage

import (
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"io"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func newStore(t *testing.T) (*Store, string) {
	t.Helper()
	root := t.TempDir()
	store, err := New(root)
	if err != nil {
		t.Fatal(err)
	}
	return store, root
}

func TestSaveKeepsTheContentAndReportsItsSizeAndChecksum(t *testing.T) {
	store, _ := newStore(t)

	saved, err := store.Save(7, strings.NewReader("hello world"), 1000)
	if err != nil {
		t.Fatal(err)
	}

	sum := sha256.Sum256([]byte("hello world"))
	if saved.Size != 11 || saved.SHA256 != hex.EncodeToString(sum[:]) {
		t.Errorf("unexpected result %+v", saved)
	}
	if !strings.HasPrefix(saved.Key, "nodes/7/") {
		t.Errorf("the key should sit under the department: %q", saved.Key)
	}
	file, err := store.Open(saved.Key)
	if err != nil {
		t.Fatal(err)
	}
	defer file.Close()
	if content, _ := io.ReadAll(file); string(content) != "hello world" {
		t.Errorf("content = %q", content)
	}
}

func TestSaveGivesEveryUploadItsOwnKey(t *testing.T) {
	store, _ := newStore(t)
	first, _ := store.Save(1, strings.NewReader("same"), 100)
	second, _ := store.Save(1, strings.NewReader("same"), 100)

	if first.Key == second.Key {
		t.Error("two uploads must not share a key")
	}
}

func TestAnEmptyFileCanBeSaved(t *testing.T) {
	store, _ := newStore(t)

	saved, err := store.Save(1, strings.NewReader(""), 100)

	if err != nil || saved.Size != 0 {
		t.Errorf("Save = %+v, %v", saved, err)
	}
}

func TestTheLimitItselfIsAllowedButOneByteMoreIsNot(t *testing.T) {
	store, root := newStore(t)

	if _, err := store.Save(1, strings.NewReader("12345"), 5); err != nil {
		t.Errorf("exactly the limit should be fine: %v", err)
	}
	_, err := store.Save(1, strings.NewReader("123456"), 5)
	if !errors.Is(err, ErrTooLarge) {
		t.Errorf("over the limit gave %v, want ErrTooLarge", err)
	}

	// Only the one accepted file is on disk: the refused upload left nothing behind.
	entries, _ := os.ReadDir(filepath.Join(root, "nodes", "1"))
	if len(entries) != 1 {
		names := []string{}
		for _, entry := range entries {
			names = append(names, entry.Name())
		}
		t.Errorf("want 1 file on disk, got %v", names)
	}
}

func TestSaveExactlyKeepsContentThatMatchesTheDeclaredSize(t *testing.T) {
	store, _ := newStore(t)

	saved, err := store.SaveExactly(1, strings.NewReader("hello world"), 11)

	if err != nil {
		t.Fatal(err)
	}
	sum := sha256.Sum256([]byte("hello world"))
	if saved.Size != 11 || saved.SHA256 != hex.EncodeToString(sum[:]) {
		t.Errorf("unexpected result %+v", saved)
	}
}

func TestSaveExactlyRefusesFewerBytesThanPromised(t *testing.T) {
	store, root := newStore(t)

	_, err := store.SaveExactly(1, strings.NewReader("short"), 100)

	if !errors.Is(err, ErrTruncated) {
		t.Errorf("gave %v, want ErrTruncated", err)
	}
	entries, _ := os.ReadDir(filepath.Join(root, "nodes", "1"))
	if len(entries) != 0 {
		t.Errorf("a truncated upload left %d file(s)", len(entries))
	}
}

func TestSaveExactlyIgnoresContentBeyondTheDeclaredSize(t *testing.T) {
	store, _ := newStore(t)

	// A caller who already checked the declared size fits the limit and is only reading that many
	// bytes off a live connection: anything the sender adds afterwards (by mistake or otherwise)
	// is simply never read, not an error.
	saved, err := store.SaveExactly(1, strings.NewReader("hello worldEXTRA"), 11)

	if err != nil {
		t.Fatal(err)
	}
	if saved.Size != 11 || saved.SHA256 != mustHash("hello world") {
		t.Errorf("unexpected result %+v", saved)
	}
}

func mustHash(content string) string {
	sum := sha256.Sum256([]byte(content))
	return hex.EncodeToString(sum[:])
}

func TestAFailedReadLeavesNothingBehind(t *testing.T) {
	store, root := newStore(t)

	_, err := store.Save(1, io.MultiReader(strings.NewReader("part"), failingReader{}), 100)

	if err == nil {
		t.Fatal("the read error should be returned")
	}
	entries, _ := os.ReadDir(filepath.Join(root, "nodes", "1"))
	if len(entries) != 0 {
		t.Errorf("a failed upload left %d file(s)", len(entries))
	}
}

type failingReader struct{}

func (failingReader) Read([]byte) (int, error) { return 0, errors.New("connection lost") }

func TestSavedFilesCanBeReadByOtherUsers(t *testing.T) {
	store, root := newStore(t)
	saved, _ := store.Save(1, strings.NewReader("x"), 10)

	info, err := os.Stat(filepath.Join(root, saved.Key))
	if err != nil {
		t.Fatal(err)
	}
	if info.Mode().Perm()&0o044 != 0o044 {
		t.Errorf("the website's web server must be able to read the file, mode is %v", info.Mode().Perm())
	}
}

func TestRemoveDeletesTheContentAndIgnoresWhatIsGone(t *testing.T) {
	store, _ := newStore(t)
	saved, _ := store.Save(1, strings.NewReader("x"), 10)

	store.Remove(saved.Key)
	store.Remove(saved.Key) // already gone: no panic, no error

	if _, err := store.Open(saved.Key); err == nil {
		t.Error("the content should be gone")
	}
}

func TestKeysThatLeaveTheStoreAreRefused(t *testing.T) {
	store, root := newStore(t)
	outside := filepath.Join(filepath.Dir(root), "outside.txt")
	os.WriteFile(outside, []byte("secret"), 0o644)
	defer os.Remove(outside)

	for _, key := range []string{"../outside.txt", "nodes/../../outside.txt", "/etc/passwd", "other/1/x", ""} {
		if _, err := store.Open(key); err == nil {
			t.Errorf("Open(%q) must be refused", key)
		}
		store.Remove(key) // must not delete anything outside either
	}
	if _, err := os.Stat(outside); err != nil {
		t.Error("Remove deleted a file outside the store")
	}
}
