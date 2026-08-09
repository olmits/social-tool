// Package coreapi is a typed client for the Java core API. Workers never touch Postgres
// directly: the draft state machine in DraftService is the single writer of drafts.status,
// so every read and every transition goes over HTTP through this package.
package coreapi

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"time"
)

const (
	apiKeyHeader = "X-API-Key"
	// maxResponseBytes caps how much of a response we will read into memory. The largest
	// realistic payload is an unpaginated GET /drafts.
	maxResponseBytes = 8 << 20
)

// Client talks to the core API. It is safe for concurrent use.
type Client struct {
	baseURL    string
	apiKey     string
	httpClient *http.Client
	logger     *slog.Logger
}

// New returns a Client for the core API at baseURL, which must not have a trailing slash.
// A nil logger is replaced with a discarding one.
func New(baseURL, apiKey string, timeout time.Duration, logger *slog.Logger) *Client {
	if logger == nil {
		logger = slog.New(slog.DiscardHandler)
	}
	return &Client{
		baseURL:    baseURL,
		apiKey:     apiKey,
		httpClient: &http.Client{Timeout: timeout},
		logger:     logger,
	}
}

// do performs a request against path, encoding body as JSON when non-nil and decoding the
// response into out when non-nil. Non-2xx responses become *APIError.
func (c *Client) do(ctx context.Context, method, path string, body, out any) error {
	var payload io.Reader
	if body != nil {
		encoded, err := json.Marshal(body)
		if err != nil {
			return fmt.Errorf("encode %s %s request: %w", method, path, err)
		}
		payload = bytes.NewReader(encoded)
	}

	req, err := http.NewRequestWithContext(ctx, method, c.baseURL+path, payload)
	if err != nil {
		return fmt.Errorf("build %s %s request: %w", method, path, err)
	}
	req.Header.Set(apiKeyHeader, c.apiKey)
	req.Header.Set("Accept", "application/json")
	if body != nil {
		req.Header.Set("Content-Type", "application/json")
	}

	start := time.Now()
	resp, err := c.httpClient.Do(req)
	if err != nil {
		return fmt.Errorf("%s %s: %w", method, path, err)
	}
	defer resp.Body.Close()

	received, err := io.ReadAll(io.LimitReader(resp.Body, maxResponseBytes))
	if err != nil {
		return fmt.Errorf("read %s %s response: %w", method, path, err)
	}

	c.logger.Debug("core api call",
		"method", method, "path", path, "status", resp.StatusCode, "duration", time.Since(start).String())

	if resp.StatusCode < 200 || resp.StatusCode > 299 {
		return newAPIError(resp.StatusCode, received)
	}
	if out == nil {
		return nil
	}
	if err := json.Unmarshal(received, out); err != nil {
		return fmt.Errorf("decode %s %s response: %w", method, path, err)
	}
	return nil
}
