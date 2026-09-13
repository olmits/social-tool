// Package devto polls dev.to through its public articles API.
//
// One request returns the whole listing already ranked, so unlike Hacker News there is no
// per-item fan-out. No credentials are required for public article reads.
//
// A topic's query here is a single dev.to tag — the platform's own taxonomy, which authors
// apply themselves. That makes it a narrower and more accurate filter than a text search
// would be, and it is why a topic's dev.to query is "go" rather than a phrase.
package devto

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"strconv"

	"github.com/olmits/social-tool/services/workers/internal/source"
)

const (
	// Name matches the SignalSource enum in the core API.
	Name = "DEVTO"

	defaultBaseURL = "https://dev.to/api"

	defaultLimit = 50

	// defaultTopDays scopes the listing to the most-reacted articles of the last N days.
	// One day tracks what is current; a longer window drowns new posts in the week's
	// established hits.
	defaultTopDays = 1
)

// Source polls the dev.to top articles listing.
type Source struct {
	baseURL    string
	limit      int
	topDays    int
	httpClient *http.Client
}

// New returns a Source reading the top `limit` articles of the last `topDays` days.
// Non-positive values fall back to defaults; an empty baseURL uses the public API.
func New(httpClient *http.Client, baseURL string, limit, topDays int) *Source {
	if baseURL == "" {
		baseURL = defaultBaseURL
	}
	if limit <= 0 {
		limit = defaultLimit
	}
	if topDays <= 0 {
		topDays = defaultTopDays
	}
	return &Source{baseURL: baseURL, limit: limit, topDays: topDays, httpClient: httpClient}
}

func (s *Source) Name() string { return Name }

// Fetch reads one page of the top articles carrying the given tag.
func (s *Source) Fetch(ctx context.Context, query string) ([]source.Item, error) {
	params := url.Values{}
	params.Set("tag", query)
	params.Set("per_page", strconv.Itoa(s.limit))
	params.Set("top", strconv.Itoa(s.topDays))
	endpoint := s.baseURL + "/articles?" + params.Encode()

	// Decoded twice: once into the fields the radar reads, once as raw JSON so each
	// article's original payload can be stored alongside the normalized item.
	var raw []json.RawMessage
	if err := source.GetJSON(ctx, s.httpClient, endpoint, &raw); err != nil {
		return nil, fmt.Errorf("dev.to articles for tag %q: %w", query, err)
	}

	items := make([]source.Item, 0, len(raw))
	for _, payload := range raw {
		var post article
		if err := json.Unmarshal(payload, &post); err != nil {
			// One malformed entry should not cost the rest of the page.
			continue
		}
		if post.ID == 0 || post.Title == "" || post.URL == "" {
			continue
		}
		items = append(items, source.Item{
			ExternalID:  strconv.Itoa(post.ID),
			Title:       post.Title,
			URL:         post.URL,
			NativeScore: post.PublicReactionsCount,
			RawPayload:  payload,
		})
	}
	return items, nil
}

// article is the subset of the dev.to article shape the radar reads.
type article struct {
	ID                   int    `json:"id"`
	Title                string `json:"title"`
	URL                  string `json:"url"`
	PublicReactionsCount int    `json:"public_reactions_count"`
}
