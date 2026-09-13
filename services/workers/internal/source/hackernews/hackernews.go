// Package hackernews polls Hacker News through the Algolia search API.
//
// Not the Firebase API, which is what the front page is served from: /v0/topstories.json is
// a fixed ranked list with no way to ask it for a subject, so scoping a poll to a topic is
// impossible there. Algolia indexes the same corpus and takes a query, which is the whole
// reason for the swap — and it returns full stories in the search response, so the per-item
// fan-out the Firebase API forced is gone with it.
//
// A topic's query here is plain search terms ("rust async"), matched against titles, URLs,
// and story text. Neither credentials nor a key are required.
package hackernews

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"strconv"
	"time"

	"github.com/olmits/social-tool/services/workers/internal/source"
)

const (
	// Name matches the SignalSource enum in the core API.
	Name = "HACKER_NEWS"

	defaultBaseURL = "https://hn.algolia.com/api/v1"

	// defaultLimit is how many hits to take per query. Fifty is roughly the front page plus
	// the items climbing towards it, which is the part worth drafting from.
	defaultLimit = 50

	// defaultWindowDays scopes results to stories submitted in the last N days. Unscoped,
	// a search returns the best-matching stories of all time — a 2013 classic outranks
	// this week's discussion, which is the opposite of what a trend radar is for.
	defaultWindowDays = 7

	// storyTag restricts hits to submissions. The index also holds comments, polls, and
	// user records, none of which are drafting material.
	storyTag = "story"

	// itemURLPrefix is where a story with no external link lives (Ask HN, Show HN text posts).
	itemURLPrefix = "https://news.ycombinator.com/item?id="
)

// Source searches Hacker News for recent stories matching a topic.
type Source struct {
	baseURL    string
	limit      int
	windowDays int
	httpClient *http.Client
	now        func() time.Time
}

// New returns a Source reading the top `limit` stories submitted within the last
// `windowDays` days. Non-positive values fall back to defaults; an empty baseURL uses the
// public API.
func New(httpClient *http.Client, baseURL string, limit, windowDays int) *Source {
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
		httpClient: httpClient,
		now:        time.Now,
	}
}

func (s *Source) Name() string { return Name }

// Fetch runs one search for the topic's terms.
//
// The /search endpoint ranks by Algolia's own relevance rather than by points, which is what
// is wanted here: the radar rescales by points itself, and asking for the most relevant
// stories in a window beats asking for the highest-scoring ones of any relevance.
func (s *Source) Fetch(ctx context.Context, query string) ([]source.Item, error) {
	since := s.now().UTC().AddDate(0, 0, -s.windowDays).Unix()

	params := url.Values{}
	params.Set("query", query)
	params.Set("tags", storyTag)
	params.Set("numericFilters", "created_at_i>"+strconv.FormatInt(since, 10))
	params.Set("hitsPerPage", strconv.Itoa(s.limit))
	endpoint := s.baseURL + "/search?" + params.Encode()

	var result searchResult
	if err := source.GetJSON(ctx, s.httpClient, endpoint, &result); err != nil {
		return nil, fmt.Errorf("hacker news search for %q: %w", query, err)
	}

	items := make([]source.Item, 0, len(result.Hits))
	for _, payload := range result.Hits {
		var story hit
		if err := json.Unmarshal(payload, &story); err != nil {
			// One malformed hit should not cost the rest of the page.
			continue
		}
		if story.ObjectID == "" || story.Title == "" {
			continue
		}

		// Ask HN and Show HN text posts carry no url; the signal still needs somewhere to
		// point, and the discussion is the content in that case anyway.
		link := story.URL
		if link == "" {
			link = itemURLPrefix + story.ObjectID
		}

		items = append(items, source.Item{
			ExternalID:  story.ObjectID,
			Title:       story.Title,
			URL:         link,
			NativeScore: story.Points,
			RawPayload:  payload,
		})
	}
	return items, nil
}

type searchResult struct {
	Hits []json.RawMessage `json:"hits"`
}

// hit is the subset of the Algolia story shape the radar reads. ObjectID is the HN item
// number as a string — the same identifier the Firebase API returns as an int, so the
// external ids stored before this source moved to Algolia still match.
type hit struct {
	ObjectID string `json:"objectID"`
	Title    string `json:"title"`
	URL      string `json:"url"`
	Points   int    `json:"points"`
}
