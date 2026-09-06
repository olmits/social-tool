package devto_test

import (
	"context"
	"fmt"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/olmits/social-tool/services/workers/internal/source/devto"
)

// stubAPI serves the articles listing, capturing the query the source sent.
func stubAPI(t *testing.T, body string) (*httptest.Server, *string) {
	t.Helper()

	var rawQuery string
	mux := http.NewServeMux()
	mux.HandleFunc("/articles", func(w http.ResponseWriter, r *http.Request) {
		rawQuery = r.URL.RawQuery
		fmt.Fprint(w, body)
	})

	server := httptest.NewServer(mux)
	t.Cleanup(server.Close)
	return server, &rawQuery
}

func newSource(server *httptest.Server, limit, topDays int) *devto.Source {
	return devto.New(&http.Client{Timeout: 5 * time.Second}, server.URL, limit, topDays)
}

func TestFetchReadsArticles(t *testing.T) {
	server, _ := stubAPI(t, `[
		{"id":101,"title":"Shipping Go services","url":"https://dev.to/a/go","public_reactions_count":140},
		{"id":102,"title":"Postgres indexes","url":"https://dev.to/a/pg","public_reactions_count":90}
	]`)

	items, err := newSource(server, 50, 1).Fetch(context.Background())
	if err != nil {
		t.Fatalf("Fetch: %v", err)
	}

	if len(items) != 2 {
		t.Fatalf("expected 2 items, got %d", len(items))
	}
	if items[0].ExternalID != "101" {
		t.Errorf("externalID = %q, want the dev.to article id", items[0].ExternalID)
	}
	if items[0].Topic != "Shipping Go services" {
		t.Errorf("topic = %q", items[0].Topic)
	}
	if items[0].NativeScore != 140 {
		t.Errorf("nativeScore = %d, want the public reaction count", items[0].NativeScore)
	}
	if string(items[0].RawPayload) == "" {
		t.Error("rawPayload should carry the article's original JSON")
	}
}

func TestFetchSendsLimitAndWindow(t *testing.T) {
	server, query := stubAPI(t, `[]`)

	if _, err := newSource(server, 25, 3).Fetch(context.Background()); err != nil {
		t.Fatalf("Fetch: %v", err)
	}

	if *query != "per_page=25&top=3" {
		t.Errorf("query = %q, want per_page and top to reflect the configuration", *query)
	}
}

func TestFetchSkipsIncompleteArticles(t *testing.T) {
	// An article with no title or no url cannot become a usable signal; the rest of the
	// page should still come through.
	server, _ := stubAPI(t, `[
		{"id":1,"title":"","url":"https://dev.to/a/1","public_reactions_count":10},
		{"id":2,"title":"No link","url":"","public_reactions_count":10},
		{"id":0,"title":"No id","url":"https://dev.to/a/3","public_reactions_count":10},
		{"id":4,"title":"Good","url":"https://dev.to/a/4","public_reactions_count":10}
	]`)

	items, err := newSource(server, 50, 1).Fetch(context.Background())
	if err != nil {
		t.Fatalf("Fetch: %v", err)
	}

	if len(items) != 1 || items[0].Topic != "Good" {
		t.Errorf("expected only the complete article, got %+v", items)
	}
}

func TestFetchSkipsMalformedEntries(t *testing.T) {
	// A single entry of the wrong shape should not cost the whole page.
	server, _ := stubAPI(t, `[
		"not an object",
		{"id":7,"title":"Good","url":"https://dev.to/a/7","public_reactions_count":3}
	]`)

	items, err := newSource(server, 50, 1).Fetch(context.Background())
	if err != nil {
		t.Fatalf("Fetch: %v", err)
	}
	if len(items) != 1 || items[0].ExternalID != "7" {
		t.Errorf("expected the well-formed article to survive, got %+v", items)
	}
}

func TestFetchFailsOnUpstreamError(t *testing.T) {
	mux := http.NewServeMux()
	mux.HandleFunc("/articles", func(w http.ResponseWriter, _ *http.Request) {
		http.Error(w, "rate limited", http.StatusTooManyRequests)
	})
	server := httptest.NewServer(mux)
	t.Cleanup(server.Close)

	if _, err := newSource(server, 50, 1).Fetch(context.Background()); err == nil {
		t.Fatal("expected an error for a non-2xx response")
	}
}
