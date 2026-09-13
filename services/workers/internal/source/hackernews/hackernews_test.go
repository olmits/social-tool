package hackernews_test

import (
	"context"
	"fmt"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strconv"
	"strings"
	"testing"
	"time"

	"github.com/olmits/social-tool/services/workers/internal/source/hackernews"
)

// stubAPI serves the Algolia search endpoint, capturing the query the source sent.
func stubAPI(t *testing.T, body string) (*httptest.Server, *string) {
	t.Helper()

	var rawQuery string
	mux := http.NewServeMux()
	mux.HandleFunc("/search", func(w http.ResponseWriter, r *http.Request) {
		rawQuery = r.URL.RawQuery
		fmt.Fprint(w, body)
	})

	server := httptest.NewServer(mux)
	t.Cleanup(server.Close)
	return server, &rawQuery
}

func hits(entries ...string) string {
	return `{"hits":[` + strings.Join(entries, ",") + `]}`
}

func story(id int, title, url string, points int) string {
	return fmt.Sprintf(`{"objectID":"%d","title":%q,"url":%q,"points":%d}`, id, title, url, points)
}

func newSource(t *testing.T, server *httptest.Server, limit int) *hackernews.Source {
	t.Helper()
	return hackernews.New(&http.Client{Timeout: 5 * time.Second}, server.URL, limit, 7)
}

func TestFetchReadsStories(t *testing.T) {
	server, _ := stubAPI(t, hits(
		story(10, "Rust async internals", "https://example.test/a", 300),
		story(20, "Tokio 2.0", "https://example.test/b", 200),
	))

	items, err := newSource(t, server, 50).Fetch(context.Background(), "rust async")
	if err != nil {
		t.Fatalf("Fetch: %v", err)
	}

	if len(items) != 2 {
		t.Fatalf("expected 2 items, got %d", len(items))
	}
	// Algolia's hit order is kept: it is relevance-ranked, and the radar rescales by
	// points separately rather than re-sorting here.
	if items[0].Title != "Rust async internals" {
		t.Errorf("title = %q, want the first hit", items[0].Title)
	}
	if items[0].ExternalID != "10" {
		t.Errorf("externalID = %q, want the HN item number", items[0].ExternalID)
	}
	if items[0].NativeScore != 300 {
		t.Errorf("nativeScore = %d, want the raw HN points", items[0].NativeScore)
	}
	if string(items[0].RawPayload) == "" {
		t.Error("rawPayload should carry the hit's original JSON")
	}
}

// The query is the whole reason this source moved off the Firebase API, so it has to reach
// Algolia — along with the filters that keep a trend radar from surfacing 2013 classics.
func TestFetchSendsTheTopicQueryAndFilters(t *testing.T) {
	server, raw := stubAPI(t, hits())

	if _, err := newSource(t, server, 25).Fetch(context.Background(), "rust async"); err != nil {
		t.Fatalf("Fetch: %v", err)
	}

	sent, err := url.ParseQuery(*raw)
	if err != nil {
		t.Fatalf("parse captured query: %v", err)
	}
	if got := sent.Get("query"); got != "rust async" {
		t.Errorf("query = %q, want the topic's terms", got)
	}
	if got := sent.Get("tags"); got != "story" {
		t.Errorf("tags = %q, want only submissions — comments are not drafting material", got)
	}
	if got := sent.Get("hitsPerPage"); got != "25" {
		t.Errorf("hitsPerPage = %q, want the configured limit", got)
	}

	// The window is relative to now, so assert its shape and that it is in the recent past
	// rather than pinning a timestamp.
	filter := sent.Get("numericFilters")
	cutoff, ok := strings.CutPrefix(filter, "created_at_i>")
	if !ok {
		t.Fatalf("numericFilters = %q, want a created_at_i lower bound", filter)
	}
	seconds, err := strconv.ParseInt(cutoff, 10, 64)
	if err != nil {
		t.Fatalf("numericFilters cutoff %q is not a unix timestamp: %v", cutoff, err)
	}
	if age := time.Since(time.Unix(seconds, 0)); age < 6*24*time.Hour || age > 8*24*time.Hour {
		t.Errorf("cutoff is %s old, want roughly the configured 7-day window", age)
	}
}

func TestFetchFallsBackToItemURLForTextPosts(t *testing.T) {
	// Ask HN and Show HN text posts carry no url; the signal still needs somewhere to point.
	server, _ := stubAPI(t, hits(`{"objectID":"42","title":"Ask HN: what are you building?","url":null,"points":80}`))

	items, err := newSource(t, server, 50).Fetch(context.Background(), "side projects")
	if err != nil {
		t.Fatalf("Fetch: %v", err)
	}
	if len(items) != 1 {
		t.Fatalf("expected 1 item, got %d", len(items))
	}
	if items[0].URL != "https://news.ycombinator.com/item?id=42" {
		t.Errorf("URL = %q, want the HN permalink", items[0].URL)
	}
}

func TestFetchSkipsUnusableHits(t *testing.T) {
	// A hit with no title cannot become a usable signal, and one of the wrong shape should
	// not cost the healthy story alongside it.
	server, _ := stubAPI(t, hits(
		`{"objectID":"1","title":"","url":"https://example.test/1","points":10}`,
		`{"objectID":"","title":"No id","url":"https://example.test/2","points":10}`,
		`"not an object"`,
		story(4, "healthy", "https://example.test/ok", 120),
	))

	items, err := newSource(t, server, 50).Fetch(context.Background(), "go")
	if err != nil {
		t.Fatalf("Fetch: %v", err)
	}

	if len(items) != 1 {
		t.Fatalf("expected only the usable story, got %d items", len(items))
	}
	if items[0].Title != "healthy" {
		t.Errorf("title = %q, want the one usable story", items[0].Title)
	}
}

func TestFetchReturnsNothingForAQueryWithNoMatches(t *testing.T) {
	// An ordinary outcome for a narrow topic, and not an error.
	server, _ := stubAPI(t, hits())

	items, err := newSource(t, server, 50).Fetch(context.Background(), "a topic nobody posts about")
	if err != nil {
		t.Fatalf("an empty result should not be an error: %v", err)
	}
	if len(items) != 0 {
		t.Errorf("expected no items, got %d", len(items))
	}
}

func TestFetchFailsOnUpstreamError(t *testing.T) {
	mux := http.NewServeMux()
	mux.HandleFunc("/search", func(w http.ResponseWriter, _ *http.Request) {
		http.Error(w, "down", http.StatusServiceUnavailable)
	})
	server := httptest.NewServer(mux)
	t.Cleanup(server.Close)

	if _, err := newSource(t, server, 50).Fetch(context.Background(), "go"); err == nil {
		t.Fatal("expected an error when the search endpoint is unavailable")
	}
}

func TestFetchReportsCancellation(t *testing.T) {
	server, _ := stubAPI(t, hits(story(1, "one", "https://example.test/1", 10)))

	ctx, cancel := context.WithCancel(context.Background())
	cancel()

	if _, err := newSource(t, server, 50).Fetch(ctx, "go"); err == nil {
		t.Fatal("expected a cancelled context to surface as an error")
	}
}
