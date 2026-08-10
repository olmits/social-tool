package bluesky

import (
	"encoding/json"
	"fmt"
	"net/http"
	"strings"
)

// APIError is a non-2xx response from Bluesky. XRPC errors carry a machine-readable "error"
// tag ("InvalidRequest", "ExpiredToken", "RateLimitExceeded", ...) alongside a human message.
type APIError struct {
	StatusCode int
	// Kind is the XRPC error tag, or "" when the body did not carry one.
	Kind    string
	Message string
}

func (e *APIError) Error() string {
	parts := []string{fmt.Sprintf("%d %s", e.StatusCode, http.StatusText(e.StatusCode))}
	if e.Kind != "" {
		parts = append(parts, e.Kind)
	}
	if e.Message != "" {
		parts = append(parts, e.Message)
	}
	return strings.Join(parts, ": ")
}

// Retryable reports whether the failure is worth another attempt later. Rate limits and
// server-side faults are transient; a rejected password or a malformed post is not.
func (e *APIError) Retryable() bool {
	return e.StatusCode == http.StatusTooManyRequests || e.StatusCode >= 500
}

func newAPIError(statusCode int, body []byte) *APIError {
	apiErr := &APIError{StatusCode: statusCode}

	var payload struct {
		Error   string `json:"error"`
		Message string `json:"message"`
	}
	if err := json.Unmarshal(body, &payload); err == nil {
		apiErr.Kind = payload.Error
		apiErr.Message = payload.Message
	}
	if apiErr.Kind == "" && apiErr.Message == "" {
		apiErr.Message = truncate(strings.TrimSpace(string(body)), 256)
	}
	return apiErr
}

func truncate(s string, limit int) string {
	if len(s) <= limit {
		return s
	}
	return s[:limit] + "…"
}
