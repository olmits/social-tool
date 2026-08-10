// Package adapter defines the common surface every social platform implements. Adding a
// platform is a new sub-package here plus one entry in the publisher's registry — no change
// to the publish loop.
//
// This mirrors the Java PlatformAdapter interface, but deliberately covers only what the
// workers need. Publishing lives here; the core API keeps its own adapters for reads and
// validation.
package adapter

import "context"

// Account carries everything an adapter needs to act as one account. The secret has already
// been resolved from the credential store by the time it reaches an adapter.
type Account struct {
	// Handle is the bare account handle (Bluesky uses it as the session identifier).
	Handle string
	// Instance is the server host for federated platforms such as Mastodon; empty otherwise.
	Instance string
	// Secret is the app password or access token.
	Secret string
}

// Adapter is bound to a single account and publishes on its behalf.
type Adapter interface {
	// Post publishes a top-level post and returns the platform's identifier for it —
	// for Bluesky, the at:// URI.
	Post(ctx context.Context, text string) (string, error)
}

// Factory opens an authenticated adapter for an account. Session setup (Bluesky's
// createSession, for instance) happens here rather than on every post.
type Factory interface {
	ForAccount(ctx context.Context, account Account) (Adapter, error)
}
