package coreapi_test

import (
	"encoding/json"
	"errors"
	"net/http"
	"strings"
	"testing"
	"time"

	"github.com/olmits/social-tool/services/workers/internal/coreapi"
)

var fetchedAt = time.Date(2026, 8, 22, 9, 0, 0, 0, time.UTC)

func TestIngestSignalsPostsBatch(t *testing.T) {
	var gotMethod, gotPath, gotKey, gotContentType string
	var gotBody struct {
		Signals []coreapi.Signal `json:"signals"`
	}

	client := newTestClient(t, func(w http.ResponseWriter, r *http.Request) {
		gotMethod, gotPath = r.Method, r.URL.Path
		gotKey, gotContentType = r.Header.Get("X-API-Key"), r.Header.Get("Content-Type")
		if err := json.NewDecoder(r.Body).Decode(&gotBody); err != nil {
			t.Errorf("decode request body: %v", err)
		}
		respondJSON(t, w, http.StatusOK, `{"received":2,"created":1,"updated":1}`)
	})

	result, err := client.IngestSignals(t.Context(), []coreapi.Signal{
		{
			Source:      coreapi.SourceHackerNews,
			ExternalID:  "42",
			Title:       "Something interesting",
			URL:         "https://example.test/42",
			Score:       88,
			NativeScore: 842,
			RawPayload:  json.RawMessage(`{"id":42}`),
			FetchedAt:   fetchedAt,
		},
		{
			Source:      coreapi.SourceDevto,
			ExternalID:  "101",
			Title:       "Another thing",
			URL:         "https://dev.to/a/101",
			Score:       40,
			NativeScore: 456,
			RawPayload:  json.RawMessage(`{"id":101}`),
			FetchedAt:   fetchedAt,
		},
	})
	if err != nil {
		t.Fatalf("IngestSignals: %v", err)
	}

	if gotMethod != http.MethodPost || gotPath != "/signals" {
		t.Errorf("request = %s %s, want POST /signals", gotMethod, gotPath)
	}
	if gotKey != testAPIKey {
		t.Errorf("X-API-Key = %q, want %q", gotKey, testAPIKey)
	}
	if gotContentType != "application/json" {
		t.Errorf("Content-Type = %q, want application/json", gotContentType)
	}
	if len(gotBody.Signals) != 2 {
		t.Fatalf("server received %d signals, want 2", len(gotBody.Signals))
	}
	if gotBody.Signals[0].Source != coreapi.SourceHackerNews {
		t.Errorf("source = %q, want the uppercase enum name the core API expects",
			gotBody.Signals[0].Source)
	}
	// rawPayload must reach the API as embedded JSON, not as a quoted string — the column
	// is jsonb and would reject a string.
	if string(gotBody.Signals[0].RawPayload) != `{"id":42}` {
		t.Errorf("rawPayload = %s, want the object inline", gotBody.Signals[0].RawPayload)
	}
	if !gotBody.Signals[0].FetchedAt.Equal(fetchedAt) {
		t.Errorf("fetchedAt = %s, want %s", gotBody.Signals[0].FetchedAt, fetchedAt)
	}
	// The source's own count travels alongside the rescaled score; the API stores both.
	if gotBody.Signals[0].NativeScore != 842 {
		t.Errorf("nativeScore = %d, want the source's own count", gotBody.Signals[0].NativeScore)
	}
	// An unscoped poll names no topic, and the field must be absent rather than "".
	if gotBody.Signals[0].TopicID != nil {
		t.Errorf("topicId = %v, want it omitted when the poll is not topic-scoped",
			*gotBody.Signals[0].TopicID)
	}

	if result.Received != 2 || result.Created != 1 || result.Updated != 1 {
		t.Errorf("result = %+v, want the counts the API reported", result)
	}
}

func TestIngestSignalsSurfacesAPIErrors(t *testing.T) {
	client := newTestClient(t, func(w http.ResponseWriter, _ *http.Request) {
		respondJSON(t, w, http.StatusBadRequest, `{"message":"score must be between 0 and 100 (got 140)"}`)
	})

	_, err := client.IngestSignals(t.Context(), []coreapi.Signal{{Source: coreapi.SourceDevto}})
	if err == nil {
		t.Fatal("expected an error for a 400 response")
	}

	// Validation rejections are the ingest endpoint's likely failure, so the reason the
	// core API gave has to survive into the radar's logs.
	var apiErr *coreapi.APIError
	if !errors.As(err, &apiErr) {
		t.Fatalf("error %v should be an *APIError", err)
	}
	if apiErr.StatusCode != http.StatusBadRequest {
		t.Errorf("status = %d, want 400", apiErr.StatusCode)
	}
	if !strings.Contains(apiErr.Message, "score must be between 0 and 100") {
		t.Errorf("message = %q, want the core API's explanation", apiErr.Message)
	}
}

func TestListSignalsFiltersBySource(t *testing.T) {
	var gotPath, gotQuery string

	client := newTestClient(t, func(w http.ResponseWriter, r *http.Request) {
		gotPath, gotQuery = r.URL.Path, r.URL.RawQuery
		respondJSON(t, w, http.StatusOK, `[{
			"id":"6f2a1c3d-0b8e-4a17-9c55-2d1f7e4a8b90",
			"source":"HACKER_NEWS","externalId":"42","title":"Something","url":"https://example.test/42",
			"topicId":"b1e8c4a2-7d35-4f16-8a90-3c2e5d7b1f04","topicName":"Local-first",
			"score":88,"nativeScore":842,"rawPayload":{"id":42},
			"fetchedAt":"2026-08-22T09:00:00Z","createdAt":"2026-08-22T09:00:00Z"
		}]`)
	})

	signals, err := client.ListSignals(t.Context(), coreapi.SourceHackerNews)
	if err != nil {
		t.Fatalf("ListSignals: %v", err)
	}

	if gotPath != "/signals" || gotQuery != "source=HACKER_NEWS" {
		t.Errorf("request = %s?%s, want /signals?source=HACKER_NEWS", gotPath, gotQuery)
	}
	if len(signals) != 1 {
		t.Fatalf("expected 1 signal, got %d", len(signals))
	}
	if signals[0].Score != 88 || signals[0].ExternalID != "42" {
		t.Errorf("decoded signal = %+v", signals[0])
	}
	if signals[0].Title != "Something" {
		t.Errorf("title = %q, want the renamed field to decode", signals[0].Title)
	}
	if signals[0].NativeScore != 842 {
		t.Errorf("nativeScore = %d, want 842", signals[0].NativeScore)
	}
	if signals[0].TopicName == nil || *signals[0].TopicName != "Local-first" {
		t.Errorf("topicName = %v, want the name the API resolved", signals[0].TopicName)
	}
}

func TestListSignalsWithoutFilter(t *testing.T) {
	var gotQuery string

	client := newTestClient(t, func(w http.ResponseWriter, r *http.Request) {
		gotQuery = r.URL.RawQuery
		respondJSON(t, w, http.StatusOK, `[]`)
	})

	if _, err := client.ListSignals(t.Context(), ""); err != nil {
		t.Fatalf("ListSignals: %v", err)
	}
	if gotQuery != "" {
		t.Errorf("query = %q, want no filter when no source is given", gotQuery)
	}
}
