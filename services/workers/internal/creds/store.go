// Package creds resolves the credential references stored on accounts into the actual
// secret values (a Bluesky app password, a Mastodon access token, ...).
//
// The core API never returns a credential over HTTP — accounts carry only a reference, and
// each caller resolves it through the store it has access to. This mirrors the Java side's
// CredentialStore split: a file-backed store for local development, Secrets Manager in
// production.
package creds

import "context"

// Store resolves a credential reference to its secret value.
type Store interface {
	// Resolve returns the secret behind ref. Implementations must not log the value.
	Resolve(ctx context.Context, ref string) (string, error)
}
