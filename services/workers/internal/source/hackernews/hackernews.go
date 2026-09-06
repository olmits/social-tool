// Package hackernews polls Hacker News through its public Firebase API.
//
// The API has no batch endpoint: /v0/topstories.json returns ranked item ids and each item
// must then be fetched individually. Those per-item fetches run concurrently with a bounded
// worker pool — sequential fetching of the whole front page would dominate a radar run, and
// unbounded fan-out would open hundreds of sockets at once.
//
// No credentials are required.
package hackernews

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"strconv"
	"sync"

	"github.com/olmits/social-tool/services/workers/internal/source"
)

const (
	// Name matches the SignalSource enum in the core API.
	Name = "HACKER_NEWS"

	defaultBaseURL = "https://hacker-news.firebaseio.com/v0"

	// defaultLimit is how far down the ranked list to read. The front page is 30; taking a
	// little more gives the drafting slice items that are climbing but not yet at the top.
	defaultLimit = 50

	// fetchConcurrency bounds simultaneous item fetches.
	fetchConcurrency = 8

	// itemURLPrefix is where a story with no external link lives (Ask HN, Show HN text posts).
	itemURLPrefix = "https://news.ycombinator.com/item?id="
)

// Source polls the Hacker News front page.
type Source struct {
	baseURL    string
	limit      int
	httpClient *http.Client
}

// New returns a Source reading the top `limit` stories. A limit of zero or less uses the
// default; an empty baseURL uses the public API.
func New(httpClient *http.Client, baseURL string, limit int) *Source {
	if baseURL == "" {
		baseURL = defaultBaseURL
	}
	if limit <= 0 {
		limit = defaultLimit
	}
	return &Source{baseURL: baseURL, limit: limit, httpClient: httpClient}
}

func (s *Source) Name() string { return Name }

// Fetch reads the ranked story ids, then the stories themselves.
//
// An individual story that fails to fetch is skipped rather than failing the run: the front
// page is a listing, and losing one of fifty entries is not worth discarding the other
// forty-nine. A failure to read the listing itself does fail, since there is nothing to
// report without it.
func (s *Source) Fetch(ctx context.Context) ([]source.Item, error) {
	var ids []int
	if err := source.GetJSON(ctx, s.httpClient, s.baseURL+"/topstories.json", &ids); err != nil {
		return nil, fmt.Errorf("hacker news topstories: %w", err)
	}
	if len(ids) > s.limit {
		ids = ids[:s.limit]
	}

	items := make([]source.Item, len(ids))
	found := make([]bool, len(ids))

	var wg sync.WaitGroup
	slots := make(chan struct{}, fetchConcurrency)
	for i, id := range ids {
		wg.Add(1)
		go func() {
			defer wg.Done()
			slots <- struct{}{}
			defer func() { <-slots }()

			item, ok, err := s.fetchItem(ctx, id)
			if err != nil || !ok {
				return
			}
			items[i] = item
			found[i] = true
		}()
	}
	wg.Wait()

	// A cancelled context fails every in-flight fetch, so whatever landed is an arbitrary
	// fraction of the page. Report the cancellation instead of a partial listing.
	if err := ctx.Err(); err != nil {
		return nil, err
	}

	// Rebuild in rank order, dropping the gaps left by skipped stories.
	ranked := make([]source.Item, 0, len(items))
	for i, ok := range found {
		if ok {
			ranked = append(ranked, items[i])
		}
	}
	return ranked, nil
}

// fetchItem retrieves one story. The bool reports whether the item is usable: the API
// returns a JSON null for deleted items, and non-story types (comments, jobs, polls) are
// not drafting material.
func (s *Source) fetchItem(ctx context.Context, id int) (source.Item, bool, error) {
	url := fmt.Sprintf("%s/item/%d.json", s.baseURL, id)

	var raw json.RawMessage
	if err := source.GetJSON(ctx, s.httpClient, url, &raw); err != nil {
		return source.Item{}, false, err
	}

	var story item
	if err := json.Unmarshal(raw, &story); err != nil {
		return source.Item{}, false, fmt.Errorf("decode hacker news item %d: %w", id, err)
	}
	if story.ID == 0 || story.Type != "story" || story.Dead || story.Deleted || story.Title == "" {
		return source.Item{}, false, nil
	}

	link := story.URL
	if link == "" {
		link = itemURLPrefix + strconv.Itoa(story.ID)
	}

	return source.Item{
		ExternalID:  strconv.Itoa(story.ID),
		Topic:       story.Title,
		URL:         link,
		NativeScore: story.Score,
		RawPayload:  raw,
	}, true, nil
}

// item is the subset of the Firebase item shape the radar reads.
type item struct {
	ID      int    `json:"id"`
	Type    string `json:"type"`
	Title   string `json:"title"`
	URL     string `json:"url"`
	Score   int    `json:"score"`
	Dead    bool   `json:"dead"`
	Deleted bool   `json:"deleted"`
}
