package creds_test

import (
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/olmits/social-tool/services/workers/internal/creds"
)

// The Java LocalCredentialStore derives a filename by replacing every character outside
// [a-zA-Z0-9.-] with "_" and appending ".secret". If this drifts, the worker silently fails
// to find credentials the API wrote.
func TestLocalStoreReadsTheFileTheApiWrote(t *testing.T) {
	dir := t.TempDir()

	const ref = "local/accounts/3f8c1a2e-1234-4abc-9def-0123456789ab"
	const secret = "abcd-efgh-ijkl-mnop"
	// Slashes become underscores; the UUID's hyphens survive.
	const wantFile = "local_accounts_3f8c1a2e-1234-4abc-9def-0123456789ab.secret"

	if err := os.WriteFile(filepath.Join(dir, wantFile), []byte(secret), 0o600); err != nil {
		t.Fatalf("write secret: %v", err)
	}

	got, err := creds.NewLocalStore(dir).Resolve(t.Context(), ref)
	if err != nil {
		t.Fatalf("Resolve: %v", err)
	}
	if got != secret {
		t.Errorf("secret = %q, want %q", got, secret)
	}
}

// The Java store writes the value with no trailing newline and reads it back untrimmed, so
// whitespace is part of the secret.
func TestLocalStorePreservesExactBytes(t *testing.T) {
	dir := t.TempDir()
	const secret = "  padded-secret\n"

	if err := os.WriteFile(filepath.Join(dir, "local_accounts_x.secret"), []byte(secret), 0o600); err != nil {
		t.Fatalf("write secret: %v", err)
	}

	got, err := creds.NewLocalStore(dir).Resolve(t.Context(), "local/accounts/x")
	if err != nil {
		t.Fatalf("Resolve: %v", err)
	}
	if got != secret {
		t.Errorf("secret = %q, want %q", got, secret)
	}
}

func TestLocalStoreResolvesEnvReferences(t *testing.T) {
	t.Setenv("BLUESKY_APP_PASSWORD", "from-env")

	got, err := creds.NewLocalStore(t.TempDir()).Resolve(t.Context(), "env:BLUESKY_APP_PASSWORD")
	if err != nil {
		t.Fatalf("Resolve: %v", err)
	}
	if got != "from-env" {
		t.Errorf("secret = %q, want from-env", got)
	}
}

func TestLocalStoreErrors(t *testing.T) {
	tests := []struct {
		name        string
		ref         string
		wantMessage string
	}{
		{"empty reference", "", "empty"},
		{"unset env var", "env:DEFINITELY_NOT_SET_12345", "not set"},
		{"missing file", "local/accounts/nope", "read local credential"},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			_, err := creds.NewLocalStore(t.TempDir()).Resolve(t.Context(), tt.ref)
			if err == nil {
				t.Fatalf("Resolve(%q) returned nil error", tt.ref)
			}
			if !strings.Contains(err.Error(), tt.wantMessage) {
				t.Errorf("error %q does not contain %q", err, tt.wantMessage)
			}
		})
	}
}

// A leaked secret in an error message would end up in the logs.
func TestLocalStoreErrorsDoNotLeakSecrets(t *testing.T) {
	dir := t.TempDir()
	const secret = "super-secret-app-password"
	if err := os.WriteFile(filepath.Join(dir, "local_accounts_y.secret"), []byte(secret), 0o000); err != nil {
		t.Fatalf("write secret: %v", err)
	}

	_, err := creds.NewLocalStore(dir).Resolve(t.Context(), "local/accounts/y")
	if err == nil {
		t.Skip("filesystem allowed the read despite mode 000")
	}
	if strings.Contains(err.Error(), secret) {
		t.Errorf("error message leaked the secret: %v", err)
	}
}
