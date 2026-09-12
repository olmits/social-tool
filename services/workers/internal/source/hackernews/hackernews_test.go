package hackernews_test

import (
	"context"
	"fmt"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/olmits/social-tool/services/workers/internal/source/hackernews"
)

// stubAPI serves the two Firebase endpoints the source reads. Items are keyed by id;
// an id present in topstories but missing from items serves a JSON null, which is how the
// real API reports a deleted story.
func stubAPI(t *testing.T, topStories string, items map[int]string) *httptest.Server {
	t.Helper()

	mux := http.NewServeMux()
	mux.HandleFunc("/topstories.json", func(w http.ResponseWriter, _ *http.Request) {
		fmt.Fprint(w, topStories)
	})
	mux.HandleFunc("/item/", func(w http.ResponseWriter, r *http.Request) {
		var id int
		if _, err := fmt.Sscanf(strings.TrimPrefix(r.URL.Path, "/item/"), "%d.json", &id); err != nil {
			http.Error(w, "bad id", http.StatusBadRequest)
			return
		}
		body, ok := items[id]
		if !ok {
			fmt.Fprint(w, "null")
			return
		}
		fmt.Fprint(w, body)
	})

	server := httptest.NewServer(mux)
	t.Cleanup(server.Close)
	return server
}

func story(id int, title, url string, score int) string {
	return fmt.Sprintf(`{"id":%d,"type":"story","title":%q,"url":%q,"score":%d}`, id, title, url, score)
}

func newSource(t *testing.T, server *httptest.Server, limit int) *hackernews.Source {
	t.Helper()
	return hackernews.New(&http.Client{Timeout: 5 * time.Second}, server.URL, limit)
}

func TestFetchReadsStoriesInRankOrder(t *testing.T) {
	server := stubAPI(t, `[10,20,30]`, map[int]string{
		10: story(10, "first", "https://example.test/a", 300),
		20: story(20, "second", "https://example.test/b", 200),
		30: story(30, "third", "https://example.test/c", 100),
	})

	items, err := newSource(t, server, 50).Fetch(context.Background())
	if err != nil {
		t.Fatalf("Fetch: %v", err)
	}

	if len(items) != 3 {
		t.Fatalf("expected 3 items, got %d", len(items))
	}
	// Ranking is the listing's order, not the order the concurrent fetches completed in.
	for i, want := range []string{"first", "second", "third"} {
		if items[i].Title != want {
			t.Errorf("item %d title = %q, want %q", i, items[i].Title, want)
		}
	}
	if items[0].ExternalID != "10" {
		t.Errorf("externalID = %q, want the HN item number", items[0].ExternalID)
	}
	if items[0].NativeScore != 300 {
		t.Errorf("nativeScore = %d, want the raw HN points", items[0].NativeScore)
	}
}

func TestFetchHonoursLimit(t *testing.T) {
	server := stubAPI(t, `[1,2,3,4,5]`, map[int]string{
		1: story(1, "one", "https://example.test/1", 10),
		2: story(2, "two", "https://example.test/2", 9),
		3: story(3, "three", "https://example.test/3", 8),
		4: story(4, "four", "https://example.test/4", 7),
		5: story(5, "five", "https://example.test/5", 6),
	})

	items, err := newSource(t, server, 2).Fetch(context.Background())
	if err != nil {
		t.Fatalf("Fetch: %v", err)
	}
	if len(items) != 2 {
		t.Fatalf("expected the limit to cap the batch at 2, got %d", len(items))
	}
}

func TestFetchFallsBackToItemURLForTextPosts(t *testing.T) {
	// Ask HN and Show HN text posts carry no url; the signal still needs somewhere to point.
	server := stubAPI(t, `[42]`, map[int]string{
		42: `{"id":42,"type":"story","title":"Ask HN: what are you building?","score":80}`,
	})

	items, err := newSource(t, server, 50).Fetch(context.Background())
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

func TestFetchSkipsUnusableEntries(t *testing.T) {
	// A deleted story (null), a comment, and a dead story all appear in listings but are
	// not drafting material. Losing them must not cost the healthy story alongside them.
	server := stubAPI(t, `[1,2,3,4]`, map[int]string{
		2: `{"id":2,"type":"comment","text":"a reply"}`,
		3: `{"id":3,"type":"story","title":"flagged","url":"https://example.test/x","score":5,"dead":true}`,
		4: story(4, "healthy", "https://example.test/ok", 120),
	})

	items, err := newSource(t, server, 50).Fetch(context.Background())
	if err != nil {
		t.Fatalf("Fetch: %v", err)
	}

	if len(items) != 1 {
		t.Fatalf("expected only the usable story, got %d items", len(items))
	}
	if items[0].Title != "healthy" {
		t.Errorf("topic = %q, want the one usable story", items[0].Title)
	}
}

func TestFetchSurvivesIndividualItemFailures(t *testing.T) {
	// One item endpoint erroring should cost that story, not the run.
	mux := http.NewServeMux()
	mux.HandleFunc("/topstories.json", func(w http.ResponseWriter, _ *http.Request) {
		fmt.Fprint(w, `[1,2]`)
	})
	mux.HandleFunc("/item/1.json", func(w http.ResponseWriter, _ *http.Request) {
		http.Error(w, "boom", http.StatusInternalServerError)
	})
	mux.HandleFunc("/item/2.json", func(w http.ResponseWriter, _ *http.Request) {
		fmt.Fprint(w, story(2, "survivor", "https://example.test/ok", 50))
	})
	server := httptest.NewServer(mux)
	t.Cleanup(server.Close)

	items, err := newSource(t, server, 50).Fetch(context.Background())
	if err != nil {
		t.Fatalf("one bad item should not fail the fetch: %v", err)
	}
	if len(items) != 1 || items[0].Title != "survivor" {
		t.Errorf("expected just the surviving story, got %+v", items)
	}
}

func TestFetchFailsWhenListingUnavailable(t *testing.T) {
	// Without the listing there is nothing to report, so this one does fail.
	mux := http.NewServeMux()
	mux.HandleFunc("/topstories.json", func(w http.ResponseWriter, _ *http.Request) {
		http.Error(w, "down", http.StatusServiceUnavailable)
	})
	server := httptest.NewServer(mux)
	t.Cleanup(server.Close)

	if _, err := newSource(t, server, 50).Fetch(context.Background()); err == nil {
		t.Fatal("expected an error when topstories is unavailable")
	}
}

func TestFetchReportsCancellation(t *testing.T) {
	server := stubAPI(t, `[1]`, map[int]string{1: story(1, "one", "https://example.test/1", 10)})

	ctx, cancel := context.WithCancel(context.Background())
	cancel()

	if _, err := newSource(t, server, 50).Fetch(ctx); err == nil {
		t.Fatal("expected a cancelled context to surface as an error, not a partial listing")
	}
}
