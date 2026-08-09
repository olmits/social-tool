package coreapi_test

import (
	"errors"
	"io"
	"net/http"
	"strings"
	"testing"

	"github.com/olmits/social-tool/services/workers/internal/coreapi"
)

const conflictDraftID = "6f2a1c3d-0b8e-4a17-9c55-2d1f7e4a8b90"

// TestErrorResponseShapes covers every body shape the core API can return on a failure.
// The shapes differ by error path, and two of them are not JSON at all, so the client has
// to survive all of them without panicking.
func TestErrorResponseShapes(t *testing.T) {
	tests := []struct {
		name        string
		status      int
		contentType string
		body        string
		wantMessage string
		wantIs      func(error) bool
	}{
		{
			// GlobalExceptionHandler maps InvalidStateTransitionException to 409.
			name:        "handled exception returns ErrorResponse",
			status:      http.StatusConflict,
			contentType: "application/json",
			body:        `{"message":"Cannot transition draft ` + conflictDraftID + ` from PUBLISHED to PUBLISHED"}`,
			wantMessage: "Cannot transition draft " + conflictDraftID + " from PUBLISHED to PUBLISHED",
			wantIs:      coreapi.IsConflict,
		},
		{
			name:        "unknown draft returns 404 ErrorResponse",
			status:      http.StatusNotFound,
			contentType: "application/json",
			body:        `{"message":"No draft found with id ` + conflictDraftID + `"}`,
			wantMessage: "No draft found with id " + conflictDraftID,
			wantIs:      coreapi.IsNotFound,
		},
		{
			// A missing or wrong X-API-Key is rejected by HttpStatusEntryPoint, which
			// writes a bare status with no body at all.
			name:        "unauthorized returns an empty body",
			status:      http.StatusUnauthorized,
			contentType: "",
			body:        "",
			wantMessage: "",
			wantIs:      coreapi.IsUnauthorized,
		},
		{
			// Errors that never reach GlobalExceptionHandler — a malformed UUID path
			// variable, a bad enum query value — get Spring's default body instead.
			name:        "spring default body falls back to the error field",
			status:      http.StatusBadRequest,
			contentType: "application/json",
			body:        `{"timestamp":"2026-08-09T12:00:00.000+00:00","status":400,"error":"Bad Request","path":"/drafts"}`,
			wantMessage: "Bad Request",
		},
		{
			name:        "non-JSON body leaves the message empty",
			status:      http.StatusInternalServerError,
			contentType: "text/html",
			body:        "<html><body>Internal Server Error</body></html>",
			wantMessage: "",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			client := newTestClient(t, func(w http.ResponseWriter, r *http.Request) {
				if tt.contentType != "" {
					w.Header().Set("Content-Type", tt.contentType)
				}
				w.WriteHeader(tt.status)
				if _, err := io.WriteString(w, tt.body); err != nil {
					t.Errorf("write response: %v", err)
				}
			})

			_, err := client.GetDraft(t.Context(), conflictDraftID)
			if err == nil {
				t.Fatalf("GetDraft returned nil error for a %d", tt.status)
			}

			var apiErr *coreapi.APIError
			if !errors.As(err, &apiErr) {
				t.Fatalf("error %v is not an *APIError", err)
			}
			if apiErr.StatusCode != tt.status {
				t.Errorf("StatusCode = %d, want %d", apiErr.StatusCode, tt.status)
			}
			if apiErr.Message != tt.wantMessage {
				t.Errorf("Message = %q, want %q", apiErr.Message, tt.wantMessage)
			}
			if apiErr.Body != strings.TrimSpace(tt.body) {
				t.Errorf("Body = %q, want %q", apiErr.Body, tt.body)
			}
			if tt.wantIs != nil && !tt.wantIs(err) {
				t.Errorf("status predicate did not match error %v", err)
			}
		})
	}
}

// A retried MarkPublished on a draft that already published answers 409. Callers must be
// able to tell that apart from a genuine failure, because the post did go out.
func TestMarkPublishedConflictIsDistinguishable(t *testing.T) {
	client := newTestClient(t, func(w http.ResponseWriter, r *http.Request) {
		respondJSON(t, w, http.StatusConflict,
			`{"message":"Cannot transition draft `+conflictDraftID+` from PUBLISHED to PUBLISHED"}`)
	})

	_, err := client.MarkPublished(t.Context(), conflictDraftID, "at://did:plc:abc/post/1")
	if err == nil {
		t.Fatal("MarkPublished returned nil error for a 409")
	}
	if !coreapi.IsConflict(err) {
		t.Errorf("IsConflict(%v) = false, want true", err)
	}
	if coreapi.IsNotFound(err) || coreapi.IsUnauthorized(err) {
		t.Errorf("a 409 matched the wrong status predicate: %v", err)
	}
	if !strings.Contains(err.Error(), "from PUBLISHED to PUBLISHED") {
		t.Errorf("error %q does not carry the API message", err)
	}
}

func TestStatusPredicatesIgnoreNonAPIErrors(t *testing.T) {
	err := errors.New("dial tcp: connection refused")
	if coreapi.IsNotFound(err) || coreapi.IsConflict(err) || coreapi.IsUnauthorized(err) {
		t.Error("a plain error matched a status predicate")
	}
}
