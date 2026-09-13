package coreapi_test

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/olmits/social-tool/services/workers/internal/coreapi"
)

const testAPIKey = "test-api-key"

// newTestClient starts a stub core API served by handler and returns a client pointed at it.
func newTestClient(t *testing.T, handler http.HandlerFunc) *coreapi.Client {
	t.Helper()
	server := httptest.NewServer(handler)
	t.Cleanup(server.Close)
	return coreapi.New(server.URL, testAPIKey, 5*time.Second, nil)
}

// respondJSON writes body as a JSON response with the given status.
func respondJSON(t *testing.T, w http.ResponseWriter, status int, body string) {
	t.Helper()
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	if _, err := io.WriteString(w, body); err != nil {
		t.Errorf("write response: %v", err)
	}
}

func TestListDraftsSendsApiKeyAndStatusFilter(t *testing.T) {
	var gotMethod, gotPath, gotQuery, gotKey, gotAccept string

	client := newTestClient(t, func(w http.ResponseWriter, r *http.Request) {
		gotMethod, gotPath, gotQuery = r.Method, r.URL.Path, r.URL.RawQuery
		gotKey, gotAccept = r.Header.Get("X-API-Key"), r.Header.Get("Accept")
		respondJSON(t, w, http.StatusOK, `[]`)
	})

	if _, err := client.ListDrafts(t.Context(), coreapi.ListDraftsParams{Status: coreapi.StatusScheduled}); err != nil {
		t.Fatalf("ListDrafts: %v", err)
	}

	if gotMethod != http.MethodGet {
		t.Errorf("method = %q, want GET", gotMethod)
	}
	if gotPath != "/drafts" {
		t.Errorf("path = %q, want /drafts", gotPath)
	}
	// The core API matches the status enum case-sensitively.
	if gotQuery != "status=SCHEDULED" {
		t.Errorf("query = %q, want status=SCHEDULED", gotQuery)
	}
	if gotKey != testAPIKey {
		t.Errorf("X-API-Key = %q, want %q", gotKey, testAPIKey)
	}
	if gotAccept != "application/json" {
		t.Errorf("Accept = %q, want application/json", gotAccept)
	}
}

func TestListDraftsSendsBothFiltersAndOmitsEmptyOnes(t *testing.T) {
	accountID := "8f14e45f-ceea-467a-9df7-4f4c9b1e5d10"

	tests := []struct {
		name      string
		params    coreapi.ListDraftsParams
		wantQuery string
	}{
		{"no filters", coreapi.ListDraftsParams{}, ""},
		{"account only", coreapi.ListDraftsParams{AccountID: accountID}, "accountId=" + accountID},
		{"status only", coreapi.ListDraftsParams{Status: coreapi.StatusPublished}, "status=PUBLISHED"},
		{
			"both",
			coreapi.ListDraftsParams{AccountID: accountID, Status: coreapi.StatusScheduled},
			"accountId=" + accountID + "&status=SCHEDULED",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			var gotQuery string
			client := newTestClient(t, func(w http.ResponseWriter, r *http.Request) {
				gotQuery = r.URL.RawQuery
				respondJSON(t, w, http.StatusOK, `[]`)
			})

			if _, err := client.ListDrafts(t.Context(), tt.params); err != nil {
				t.Fatalf("ListDrafts: %v", err)
			}
			if gotQuery != tt.wantQuery {
				t.Errorf("query = %q, want %q", gotQuery, tt.wantQuery)
			}
		})
	}
}

func TestListDraftsDecodesBareArrayWithNullFields(t *testing.T) {
	// A SCHEDULED draft as the core API actually serializes it: a bare array, camelCase
	// fields, ISO-8601 instants, and null for every unset nullable column.
	const body = `[
	  {
	    "id": "6f2a1c3d-0b8e-4a17-9c55-2d1f7e4a8b90",
	    "accountId": "91bc4f27-6a3e-4c8d-b1f0-5e2a9d3c7481",
	    "signalId": null,
	    "platform": "BLUESKY",
	    "content": "hello world",
	    "affiliateLinks": null,
	    "status": "SCHEDULED",
	    "aiGenerated": false,
	    "disclosureIncluded": false,
	    "scheduledAt": "2026-08-09T12:30:00Z",
	    "remoteId": null,
	    "failureReason": null,
	    "createdAt": "2026-08-01T09:00:00Z",
	    "updatedAt": "2026-08-02T10:15:00Z"
	  },
	  {
	    "id": "1a2b3c4d-5e6f-4071-8293-a4b5c6d7e8f9",
	    "accountId": "91bc4f27-6a3e-4c8d-b1f0-5e2a9d3c7481",
	    "signalId": null,
	    "platform": "MASTODON",
	    "content": "no schedule yet",
	    "affiliateLinks": null,
	    "status": "APPROVED",
	    "aiGenerated": true,
	    "disclosureIncluded": true,
	    "scheduledAt": null,
	    "remoteId": null,
	    "failureReason": null,
	    "createdAt": "2026-08-01T09:00:00Z",
	    "updatedAt": "2026-08-02T10:15:00Z"
	  }
	]`

	client := newTestClient(t, func(w http.ResponseWriter, r *http.Request) {
		respondJSON(t, w, http.StatusOK, body)
	})

	drafts, err := client.ListDrafts(t.Context(), coreapi.ListDraftsParams{})
	if err != nil {
		t.Fatalf("ListDrafts: %v", err)
	}
	if len(drafts) != 2 {
		t.Fatalf("got %d drafts, want 2", len(drafts))
	}

	first := drafts[0]
	if first.Platform != coreapi.PlatformBluesky {
		t.Errorf("platform = %q, want BLUESKY", first.Platform)
	}
	if first.Status != coreapi.StatusScheduled {
		t.Errorf("status = %q, want SCHEDULED", first.Status)
	}
	if first.ScheduledAt == nil {
		t.Fatal("scheduledAt = nil, want a time")
	}
	if want := time.Date(2026, 8, 9, 12, 30, 0, 0, time.UTC); !first.ScheduledAt.Equal(want) {
		t.Errorf("scheduledAt = %s, want %s", first.ScheduledAt, want)
	}
	// Nullable columns must stay distinguishable from their zero values.
	if first.RemoteID != nil {
		t.Errorf("remoteId = %v, want nil", *first.RemoteID)
	}
	if first.SignalID != nil {
		t.Errorf("signalId = %v, want nil", *first.SignalID)
	}
	if drafts[1].ScheduledAt != nil {
		t.Errorf("second draft scheduledAt = %v, want nil", drafts[1].ScheduledAt)
	}
}

func TestMarkPublishedSendsPatchWithRemoteId(t *testing.T) {
	const draftID = "6f2a1c3d-0b8e-4a17-9c55-2d1f7e4a8b90"
	const remoteID = "at://did:plc:abc123/app.bsky.feed.post/3kxyz"

	var gotMethod, gotPath, gotContentType string
	var gotBody map[string]any

	client := newTestClient(t, func(w http.ResponseWriter, r *http.Request) {
		gotMethod, gotPath = r.Method, r.URL.Path
		gotContentType = r.Header.Get("Content-Type")
		if err := json.NewDecoder(r.Body).Decode(&gotBody); err != nil {
			t.Errorf("decode request body: %v", err)
		}
		respondJSON(t, w, http.StatusOK, `{"id":"`+draftID+`","status":"PUBLISHED","remoteId":"`+remoteID+`"}`)
	})

	draft, err := client.MarkPublished(t.Context(), draftID, remoteID)
	if err != nil {
		t.Fatalf("MarkPublished: %v", err)
	}

	if gotMethod != http.MethodPatch {
		t.Errorf("method = %q, want PATCH", gotMethod)
	}
	if want := "/drafts/" + draftID + "/published"; gotPath != want {
		t.Errorf("path = %q, want %q", gotPath, want)
	}
	if gotContentType != "application/json" {
		t.Errorf("Content-Type = %q, want application/json", gotContentType)
	}
	if gotBody["remoteId"] != remoteID {
		t.Errorf("body remoteId = %v, want %q", gotBody["remoteId"], remoteID)
	}
	if draft.Status != coreapi.StatusPublished {
		t.Errorf("returned status = %q, want PUBLISHED", draft.Status)
	}
}

func TestMarkFailedAlwaysSendsJsonObject(t *testing.T) {
	const draftID = "6f2a1c3d-0b8e-4a17-9c55-2d1f7e4a8b90"

	tests := []struct {
		name       string
		reason     string
		wantReason any
	}{
		// An absent body would trip Spring's HttpMessageNotReadableException, so an empty
		// reason still has to travel as an explicit null.
		{"empty reason becomes null", "", nil},
		{"reason is sent verbatim", "bluesky returned 502", "bluesky returned 502"},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			var raw []byte
			var gotPath string
			var gotBody map[string]any

			client := newTestClient(t, func(w http.ResponseWriter, r *http.Request) {
				gotPath = r.URL.Path
				raw, _ = io.ReadAll(r.Body)
				if err := json.Unmarshal(raw, &gotBody); err != nil {
					t.Errorf("decode request body %q: %v", raw, err)
				}
				respondJSON(t, w, http.StatusOK, `{"id":"`+draftID+`","status":"FAILED"}`)
			})

			if _, err := client.MarkFailed(t.Context(), draftID, tt.reason); err != nil {
				t.Fatalf("MarkFailed: %v", err)
			}

			if want := "/drafts/" + draftID + "/failed"; gotPath != want {
				t.Errorf("path = %q, want %q", gotPath, want)
			}
			if len(raw) == 0 {
				t.Fatal("request body was empty, want a JSON object")
			}
			if _, ok := gotBody["reason"]; !ok {
				t.Errorf("body = %s, want a reason field", raw)
			}
			if gotBody["reason"] != tt.wantReason {
				t.Errorf("body reason = %v, want %v", gotBody["reason"], tt.wantReason)
			}
		})
	}
}

func TestHealthParsesPayload(t *testing.T) {
	client := newTestClient(t, func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/health" {
			t.Errorf("path = %q, want /health", r.URL.Path)
		}
		respondJSON(t, w, http.StatusOK, `{"status":"UP","checks":{"api":"UP","database":"UP"}}`)
	})

	health, err := client.Health(t.Context())
	if err != nil {
		t.Fatalf("Health: %v", err)
	}
	if health.Status != "UP" {
		t.Errorf("status = %q, want UP", health.Status)
	}
	if health.Checks["database"] != "UP" {
		t.Errorf("checks[database] = %q, want UP", health.Checks["database"])
	}
}

func TestHealthTreatsServiceUnavailableAsError(t *testing.T) {
	client := newTestClient(t, func(w http.ResponseWriter, r *http.Request) {
		respondJSON(t, w, http.StatusServiceUnavailable, `{"status":"DOWN","checks":{"database":"DOWN"}}`)
	})

	if _, err := client.Health(t.Context()); err == nil {
		t.Fatal("Health returned nil error for a 503")
	}
}

func TestContextCancellationPropagates(t *testing.T) {
	client := newTestClient(t, func(w http.ResponseWriter, r *http.Request) {
		<-r.Context().Done()
	})

	ctx, cancel := context.WithCancel(t.Context())
	cancel()

	_, err := client.ListDrafts(ctx, coreapi.ListDraftsParams{})
	if !errors.Is(err, context.Canceled) {
		t.Fatalf("error = %v, want context.Canceled", err)
	}
}
