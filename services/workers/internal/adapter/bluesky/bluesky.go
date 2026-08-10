// Package bluesky publishes to Bluesky over the AT Protocol's XRPC endpoints.
//
// Two calls make a post: com.atproto.server.createSession exchanges a handle and app
// password for a bearer token and the account's DID, then com.atproto.repo.createRecord
// writes an app.bsky.feed.post record. The wire shapes match the Java BlueskyAdapter so the
// two services agree on what a post looks like.
package bluesky

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"time"

	"github.com/olmits/social-tool/services/workers/internal/adapter"
)

const (
	createSessionPath = "/xrpc/com.atproto.server.createSession"
	createRecordPath  = "/xrpc/com.atproto.repo.createRecord"

	// postType is the AT Protocol record type (its "$type") and the collection posts are
	// written into.
	postType = "app.bsky.feed.post"

	maxResponseBytes = 1 << 20
)

// Factory opens sessions against one Bluesky PDS host.
type Factory struct {
	baseURL    string
	httpClient *http.Client
}

// NewFactory returns a Factory for the PDS at baseURL (https://bsky.social for the main
// network; override it for a self-hosted PDS or a test double).
func NewFactory(baseURL string, timeout time.Duration) *Factory {
	return &Factory{
		baseURL:    baseURL,
		httpClient: &http.Client{Timeout: timeout},
	}
}

// ForAccount authenticates as the account and returns an adapter bound to that session.
func (f *Factory) ForAccount(ctx context.Context, account adapter.Account) (adapter.Adapter, error) {
	request := createSessionRequest{Identifier: account.Handle, Password: account.Secret}

	var session createSessionResponse
	if err := f.do(ctx, createSessionPath, "", request, &session); err != nil {
		return nil, fmt.Errorf("bluesky createSession for %s: %w", account.Handle, err)
	}
	if session.AccessJWT == "" || session.DID == "" {
		return nil, fmt.Errorf("bluesky createSession for %s returned an incomplete session", account.Handle)
	}

	return &Adapter{factory: f, session: session}, nil
}

// Adapter publishes as one authenticated Bluesky account.
type Adapter struct {
	factory *Factory
	session createSessionResponse
}

// Post writes an app.bsky.feed.post record and returns its at:// URI.
func (a *Adapter) Post(ctx context.Context, text string) (string, error) {
	request := createRecordRequest{
		Repo:       a.session.DID,
		Collection: postType,
		Record: feedPost{
			Type: postType,
			Text: text,
			// Bluesky orders timelines by this value, so it is the post's own timestamp
			// rather than the time it was scheduled.
			CreatedAt: time.Now().UTC().Format(time.RFC3339),
		},
	}

	var response createRecordResponse
	if err := a.factory.do(ctx, createRecordPath, a.session.AccessJWT, request, &response); err != nil {
		return "", fmt.Errorf("bluesky createRecord: %w", err)
	}
	if response.URI == "" {
		return "", fmt.Errorf("bluesky createRecord returned no uri")
	}
	return response.URI, nil
}

// do posts body to path, optionally bearer-authenticated, and decodes the response.
func (f *Factory) do(ctx context.Context, path, bearer string, body, out any) error {
	encoded, err := json.Marshal(body)
	if err != nil {
		return fmt.Errorf("encode request: %w", err)
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, f.baseURL+path, bytes.NewReader(encoded))
	if err != nil {
		return fmt.Errorf("build request: %w", err)
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Accept", "application/json")
	if bearer != "" {
		req.Header.Set("Authorization", "Bearer "+bearer)
	}

	resp, err := f.httpClient.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	received, err := io.ReadAll(io.LimitReader(resp.Body, maxResponseBytes))
	if err != nil {
		return fmt.Errorf("read response: %w", err)
	}

	if resp.StatusCode < 200 || resp.StatusCode > 299 {
		return newAPIError(resp.StatusCode, received)
	}
	if err := json.Unmarshal(received, out); err != nil {
		return fmt.Errorf("decode response: %w", err)
	}
	return nil
}

type createSessionRequest struct {
	Identifier string `json:"identifier"`
	Password   string `json:"password"`
}

type createSessionResponse struct {
	DID        string `json:"did"`
	AccessJWT  string `json:"accessJwt"`
	RefreshJWT string `json:"refreshJwt"`
	Handle     string `json:"handle"`
}

type createRecordRequest struct {
	Repo       string   `json:"repo"`
	Collection string   `json:"collection"`
	Record     feedPost `json:"record"`
}

type feedPost struct {
	Type      string `json:"$type"`
	Text      string `json:"text"`
	CreatedAt string `json:"createdAt"`
}

type createRecordResponse struct {
	URI string `json:"uri"`
	CID string `json:"cid"`
}
