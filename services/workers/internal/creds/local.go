package creds

import (
	"context"
	"fmt"
	"os"
	"path/filepath"
	"regexp"
	"strings"
)

const (
	// envRefPrefix marks a reference that resolves from an environment variable rather
	// than from a file.
	envRefPrefix = "env:"
	fileSuffix   = ".secret"
)

// unsafeRefChars matches everything the Java LocalCredentialStore replaces with "_" when
// turning a reference into a filename. Keep this identical to its
// ref.replaceAll("[^a-zA-Z0-9.-]", "_") or the two stores will disagree on paths.
var unsafeRefChars = regexp.MustCompile(`[^a-zA-Z0-9.-]`)

// LocalStore reads credentials from the same directory the API's LocalCredentialStore
// writes them to, so a locally connected account works for both services without being
// re-entered. It is for development only; production uses Secrets Manager.
type LocalStore struct {
	dir string
}

// NewLocalStore returns a store reading from dir — the value of the API's
// social-api.local.credentials-dir (SOCIAL_API_LOCAL_CREDENTIALS_DIR under compose).
func NewLocalStore(dir string) *LocalStore {
	return &LocalStore{dir: dir}
}

// Resolve reads the secret for ref.
//
// Two reference forms exist, matching the Java store: "env:NAME" reads environment
// variable NAME, and anything else (in practice "local/accounts/<uuid>") is a file in the
// credentials directory.
func (s *LocalStore) Resolve(_ context.Context, ref string) (string, error) {
	if ref == "" {
		return "", fmt.Errorf("credential reference is empty")
	}

	if envVar, found := strings.CutPrefix(ref, envRefPrefix); found {
		value, ok := os.LookupEnv(envVar)
		if !ok || value == "" {
			return "", fmt.Errorf("credential env var %s is not set", envVar)
		}
		return value, nil
	}

	path := s.fileFor(ref)
	// The Java store writes the value with no trailing newline and reads it back without
	// trimming, so this must not trim either — the secret is the file's exact bytes.
	value, err := os.ReadFile(path)
	if err != nil {
		return "", fmt.Errorf("read local credential %s: %w", ref, err)
	}
	return string(value), nil
}

func (s *LocalStore) fileFor(ref string) string {
	return filepath.Join(s.dir, unsafeRefChars.ReplaceAllString(ref, "_")+fileSuffix)
}
