package coreapi

import (
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"strings"
)

// maxErrorBodyBytes bounds how much of a failed response we keep for diagnostics.
const maxErrorBodyBytes = 512

// APIError is a non-2xx response from the core API.
//
// The body shape varies by error path, so treat Message as best-effort. Exceptions handled
// by GlobalExceptionHandler return {"message":"..."}; unhandled ones (a malformed UUID path
// variable, an unparseable request body, a bad enum query value) return Spring's default
// {timestamp,status,error,path}; and an authentication failure returns 401 with no body at
// all. StatusCode is always meaningful — prefer it, and the Is* helpers, over Message.
type APIError struct {
	StatusCode int
	// Message is the extracted human-readable message, or "" when the response carried none.
	Message string
	// Body is the raw response body, truncated, for logging.
	Body string
}

func (e *APIError) Error() string {
	if e.Message != "" {
		return fmt.Sprintf("core api: %d %s: %s", e.StatusCode, http.StatusText(e.StatusCode), e.Message)
	}
	return fmt.Sprintf("core api: %d %s", e.StatusCode, http.StatusText(e.StatusCode))
}

// newAPIError builds an APIError, extracting a message from whichever body shape arrived.
func newAPIError(statusCode int, body []byte) *APIError {
	apiErr := &APIError{
		StatusCode: statusCode,
		Body:       truncate(strings.TrimSpace(string(body)), maxErrorBodyBytes),
	}

	// Both the ErrorResponse record and Spring's default body are flat JSON objects; try
	// the fields each of them uses, in order of specificity. A non-JSON or empty body
	// simply leaves Message empty.
	var payload struct {
		Message string `json:"message"`
		Error   string `json:"error"`
	}
	if err := json.Unmarshal(body, &payload); err == nil {
		switch {
		case payload.Message != "":
			apiErr.Message = payload.Message
		case payload.Error != "":
			apiErr.Message = payload.Error
		}
	}

	return apiErr
}

func statusIs(err error, statusCode int) bool {
	var apiErr *APIError
	return errors.As(err, &apiErr) && apiErr.StatusCode == statusCode
}

// IsNotFound reports whether err is a 404 — typically an unknown draft or account id.
func IsNotFound(err error) bool { return statusIs(err, http.StatusNotFound) }

// IsConflict reports whether err is a 409, which the core API uses for every rejected
// state transition. See MarkPublished for why this is not always a failure.
func IsConflict(err error) bool { return statusIs(err, http.StatusConflict) }

// IsUnauthorized reports whether err is a 401 — a missing or wrong X-API-Key.
func IsUnauthorized(err error) bool { return statusIs(err, http.StatusUnauthorized) }

func truncate(s string, limit int) string {
	if len(s) <= limit {
		return s
	}
	return s[:limit] + "…"
}
