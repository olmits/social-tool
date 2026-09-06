// Package github surfaces trending repositories through the GitHub search API.
//
// GitHub publishes no trending API — github.com/trending is an HTML page with no JSON
// equivalent. Rather than scrape markup that changes without notice, this queries the
// search API for recently-created repositories ranked by stars, which approximates the same
// thing from a documented, stable endpoint.
//
// Unauthenticated search is rate-limited to 10 requests per minute. One run makes a single
// request, so no token is required; set one only if the radar's interval ever drops below
// that. A token would also lift the limit to 30/minute.
package github

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"time"

	"github.com/olmits/social-tool/services/workers/internal/source"
)

const (
	// Name matches the SignalSource enum in the core API.
	Name = "GITHUB_TRENDING"

	defaultBaseURL = "https://api.github.com"

	defaultLimit = 50

	// defaultWindowDays scopes the query to repositories created in the last N days.
	// A week is wide enough that a project has had time to accumulate stars, and narrow
	// enough that long-established repositories cannot dominate the ranking.
	defaultWindowDays = 7

	// apiVersionHeader pins the REST API version, so a future default does not silently
	// change the response shape.
	apiVersionHeader = "X-GitHub-Api-Version"
	apiVersion       = "2022-11-28"
)

// Source polls GitHub for recently-created, highly-starred repositories.
type Source struct {
	baseURL    string
	limit      int
	windowDays int
	token      string
	httpClient *http.Client
	now        func() time.Time
}

// New returns a Source reading the top `limit` repositories created within the last
// `windowDays` days. The token is optional; an empty one makes unauthenticated requests.
// Non-positive values fall back to defaults; an empty baseURL uses the public API.
func New(httpClient *http.Client, baseURL string, limit, windowDays int, token string) *Source {
	if baseURL == "" {
		baseURL = defaultBaseURL
	}
	if limit <= 0 {
		limit = defaultLimit
	}
	if windowDays <= 0 {
		windowDays = defaultWindowDays
	}
	return &Source{
		baseURL:    baseURL,
		limit:      limit,
		windowDays: windowDays,
		token:      token,
		httpClient: httpClient,
		now:        time.Now,
	}
}

func (s *Source) Name() string { return Name }

// Fetch runs one search query.
func (s *Source) Fetch(ctx context.Context) ([]source.Item, error) {
	since := s.now().UTC().AddDate(0, 0, -s.windowDays).Format(time.DateOnly)

	query := url.Values{}
	query.Set("q", "created:>"+since)
	query.Set("sort", "stars")
	query.Set("order", "desc")
	query.Set("per_page", strconv.Itoa(s.limit))
	endpoint := s.baseURL + "/search/repositories?" + query.Encode()

	var result searchResult
	if err := s.get(ctx, endpoint, &result); err != nil {
		return nil, fmt.Errorf("github trending search: %w", err)
	}

	items := make([]source.Item, 0, len(result.Items))
	for _, payload := range result.Items {
		var repo repository
		if err := json.Unmarshal(payload, &repo); err != nil {
			// One malformed entry should not cost the rest of the page.
			continue
		}
		if repo.FullName == "" || repo.HTMLURL == "" {
			continue
		}
		items = append(items, source.Item{
			ExternalID:  repo.FullName,
			Topic:       topic(repo),
			URL:         repo.HTMLURL,
			NativeScore: repo.StargazersCount,
			RawPayload:  payload,
		})
	}
	return items, nil
}

// get issues the search request. It does not reuse source.GetJSON because GitHub needs its
// own Accept value, an API-version header, and optional bearer auth.
func (s *Source) get(ctx context.Context, endpoint string, out any) error {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, endpoint, nil)
	if err != nil {
		return fmt.Errorf("build GET %s: %w", endpoint, err)
	}
	req.Header.Set("Accept", "application/vnd.github+json")
	req.Header.Set("User-Agent", source.UserAgent)
	req.Header.Set(apiVersionHeader, apiVersion)
	if s.token != "" {
		req.Header.Set("Authorization", "Bearer "+s.token)
	}

	resp, err := s.httpClient.Do(req)
	if err != nil {
		return fmt.Errorf("GET %s: %w", endpoint, err)
	}
	defer resp.Body.Close()

	if resp.StatusCode == http.StatusForbidden || resp.StatusCode == http.StatusTooManyRequests {
		// GitHub reports rate limiting as 403 as well as 429; naming it here saves the next
		// person correlating a bare 403 against the docs.
		return fmt.Errorf("GET %s: status %d (rate limited; remaining=%s, resets at %s)",
			endpoint, resp.StatusCode,
			resp.Header.Get("X-RateLimit-Remaining"), resp.Header.Get("X-RateLimit-Reset"))
	}
	if resp.StatusCode < 200 || resp.StatusCode > 299 {
		return fmt.Errorf("GET %s: status %d", endpoint, resp.StatusCode)
	}

	return json.NewDecoder(resp.Body).Decode(out)
}

// topic renders a repository as a headline. The name alone ("owner/repo") is too terse to
// draft from, so the description is appended when there is one.
func topic(repo repository) string {
	description := strings.TrimSpace(repo.Description)
	if description == "" {
		return repo.FullName
	}
	return repo.FullName + " — " + description
}

type searchResult struct {
	Items []json.RawMessage `json:"items"`
}

// repository is the subset of the search result shape the radar reads.
type repository struct {
	FullName        string `json:"full_name"`
	Description     string `json:"description"`
	HTMLURL         string `json:"html_url"`
	StargazersCount int    `json:"stargazers_count"`
}
