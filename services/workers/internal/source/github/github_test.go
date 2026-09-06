package github_test

import (
	"context"
	"fmt"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/olmits/social-tool/services/workers/internal/source/github"
)

type capturedRequest struct {
	query   string
	headers http.Header
}

// stubAPI serves the search endpoint, capturing what the source sent.
func stubAPI(t *testing.T, body string) (*httptest.Server, *capturedRequest) {
	t.Helper()

	var captured capturedRequest
	mux := http.NewServeMux()
	mux.HandleFunc("/search/repositories", func(w http.ResponseWriter, r *http.Request) {
		captured = capturedRequest{query: r.URL.RawQuery, headers: r.Header.Clone()}
		fmt.Fprint(w, body)
	})

	server := httptest.NewServer(mux)
	t.Cleanup(server.Close)
	return server, &captured
}

func newSource(server *httptest.Server, limit, windowDays int, token string) *github.Source {
	return github.New(&http.Client{Timeout: 5 * time.Second}, server.URL, limit, windowDays, token)
}

func TestFetchReadsRepositories(t *testing.T) {
	server, _ := stubAPI(t, `{"items":[
		{"full_name":"acme/rocket","description":"A fast thing","html_url":"https://github.com/acme/rocket","stargazers_count":1200},
		{"full_name":"acme/plain","description":"","html_url":"https://github.com/acme/plain","stargazers_count":300}
	]}`)

	items, err := newSource(server, 50, 7, "").Fetch(context.Background())
	if err != nil {
		t.Fatalf("Fetch: %v", err)
	}

	if len(items) != 2 {
		t.Fatalf("expected 2 items, got %d", len(items))
	}
	if items[0].ExternalID != "acme/rocket" {
		t.Errorf("externalID = %q, want owner/repo", items[0].ExternalID)
	}
	if items[0].NativeScore != 1200 {
		t.Errorf("nativeScore = %d, want the star count", items[0].NativeScore)
	}
	// "owner/repo" alone is too terse to draft from, so the description is appended.
	if items[0].Topic != "acme/rocket — A fast thing" {
		t.Errorf("topic = %q, want name and description", items[0].Topic)
	}
	// ...but a repo without one still needs a usable topic.
	if items[1].Topic != "acme/plain" {
		t.Errorf("topic = %q, want the bare name when there is no description", items[1].Topic)
	}
}

func TestFetchSendsSearchParametersAndHeaders(t *testing.T) {
	server, captured := stubAPI(t, `{"items":[]}`)

	if _, err := newSource(server, 25, 7, "").Fetch(context.Background()); err != nil {
		t.Fatalf("Fetch: %v", err)
	}

	for _, want := range []string{"per_page=25", "sort=stars", "order=desc", "created"} {
		if !strings.Contains(captured.query, want) {
			t.Errorf("query %q missing %q", captured.query, want)
		}
	}
	if got := captured.headers.Get("X-GitHub-Api-Version"); got == "" {
		t.Error("expected the API version to be pinned so the response shape cannot drift")
	}
	if got := captured.headers.Get("User-Agent"); got == "" {
		t.Error("GitHub throttles requests without a User-Agent")
	}
}

func TestFetchSendsTokenWhenConfigured(t *testing.T) {
	server, captured := stubAPI(t, `{"items":[]}`)

	if _, err := newSource(server, 50, 7, "ghp_secret").Fetch(context.Background()); err != nil {
		t.Fatalf("Fetch: %v", err)
	}

	if got := captured.headers.Get("Authorization"); got != "Bearer ghp_secret" {
		t.Errorf("Authorization = %q, want the bearer token", got)
	}
}

func TestFetchOmitsAuthorizationWithoutToken(t *testing.T) {
	server, captured := stubAPI(t, `{"items":[]}`)

	if _, err := newSource(server, 50, 7, "").Fetch(context.Background()); err != nil {
		t.Fatalf("Fetch: %v", err)
	}

	if got := captured.headers.Get("Authorization"); got != "" {
		t.Errorf("Authorization = %q, want it absent when no token is configured", got)
	}
}

func TestFetchSkipsIncompleteRepositories(t *testing.T) {
	server, _ := stubAPI(t, `{"items":[
		{"full_name":"","html_url":"https://github.com/x","stargazers_count":10},
		{"full_name":"acme/nolink","html_url":"","stargazers_count":10},
		{"full_name":"acme/good","html_url":"https://github.com/acme/good","stargazers_count":10}
	]}`)

	items, err := newSource(server, 50, 7, "").Fetch(context.Background())
	if err != nil {
		t.Fatalf("Fetch: %v", err)
	}
	if len(items) != 1 || items[0].ExternalID != "acme/good" {
		t.Errorf("expected only the complete repository, got %+v", items)
	}
}

// GitHub reports search rate limiting as 403 as well as 429. Both should name the cause,
// since a bare 403 otherwise reads as an auth problem.
func TestFetchNamesRateLimiting(t *testing.T) {
	for _, status := range []int{http.StatusForbidden, http.StatusTooManyRequests} {
		t.Run(http.StatusText(status), func(t *testing.T) {
			mux := http.NewServeMux()
			mux.HandleFunc("/search/repositories", func(w http.ResponseWriter, _ *http.Request) {
				w.Header().Set("X-RateLimit-Remaining", "0")
				w.WriteHeader(status)
			})
			server := httptest.NewServer(mux)
			t.Cleanup(server.Close)

			_, err := newSource(server, 50, 7, "").Fetch(context.Background())
			if err == nil {
				t.Fatalf("expected an error for status %d", status)
			}
			if !strings.Contains(err.Error(), "rate limited") {
				t.Errorf("error %q should name rate limiting", err)
			}
		})
	}
}
