package radar_test

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"strconv"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/olmits/social-tool/services/workers/internal/coreapi"
	"github.com/olmits/social-tool/services/workers/internal/radar"
	"github.com/olmits/social-tool/services/workers/internal/source"
)

var now = time.Date(2026, 8, 22, 9, 0, 0, 0, time.UTC)

// --- fakes -------------------------------------------------------------------

type fakeAPI struct {
	topics    []coreapi.TopicQueries
	topicsErr error
	batches   [][]coreapi.Signal
	err       error
}

func (f *fakeAPI) ListTopicQueries(context.Context) ([]coreapi.TopicQueries, error) {
	if f.topicsErr != nil {
		return nil, f.topicsErr
	}
	return f.topics, nil
}

func (f *fakeAPI) IngestSignals(_ context.Context, signals []coreapi.Signal) (coreapi.IngestResult, error) {
	if f.err != nil {
		return coreapi.IngestResult{}, f.err
	}
	f.batches = append(f.batches, signals)
	return coreapi.IngestResult{Received: len(signals), Created: len(signals)}, nil
}

// ingested flattens every batch the fake received.
func (f *fakeAPI) ingested() []coreapi.Signal {
	var all []coreapi.Signal
	for _, batch := range f.batches {
		all = append(all, batch...)
	}
	return all
}

// fakeSource answers every query with the same items unless byQuery says otherwise, and
// records what it was asked for.
type fakeSource struct {
	name    string
	items   []source.Item
	byQuery map[string][]source.Item
	err     error

	mu      sync.Mutex
	queries []string
	// inFlight tracks whether a second Fetch overlaps the first, which is what the
	// per-source serialization is there to prevent.
	inFlight  int
	overlap   bool
	blockUnti chan struct{}
}

func (f *fakeSource) Name() string { return f.name }

func (f *fakeSource) Fetch(_ context.Context, query string) ([]source.Item, error) {
	f.mu.Lock()
	f.queries = append(f.queries, query)
	f.inFlight++
	if f.inFlight > 1 {
		f.overlap = true
	}
	f.mu.Unlock()

	if f.blockUnti != nil {
		<-f.blockUnti
	}

	defer func() {
		f.mu.Lock()
		f.inFlight--
		f.mu.Unlock()
	}()

	if f.err != nil {
		return nil, f.err
	}
	if f.byQuery != nil {
		return f.byQuery[query], nil
	}
	return f.items, nil
}

func (f *fakeSource) asked() []string {
	f.mu.Lock()
	defer f.mu.Unlock()
	return append([]string(nil), f.queries...)
}

func items(n int) []source.Item {
	built := make([]source.Item, 0, n)
	for i := range n {
		id := strconv.Itoa(i)
		built = append(built, source.Item{
			ExternalID:  id,
			Title:       "title " + id,
			URL:         "https://example.test/" + id,
			NativeScore: n - i,
			RawPayload:  json.RawMessage(`{"id":` + id + `}`),
		})
	}
	return built
}

// topic builds a work-list entry naming one query per source given.
func topic(id, name string, queries map[coreapi.SignalSource]string) coreapi.TopicQueries {
	return coreapi.TopicQueries{TopicID: id, Name: name, Queries: queries}
}

func newRadar(api radar.CoreAPI, sources ...source.Source) *radar.Radar {
	return radar.New(radar.Options{
		API:     api,
		Sources: sources,
		Now:     func() time.Time { return now },
	})
}

// --- tests -------------------------------------------------------------------

func TestRunOncePollsEverySourceOfEveryTopic(t *testing.T) {
	api := &fakeAPI{topics: []coreapi.TopicQueries{
		topic("t-go", "Go", map[coreapi.SignalSource]string{"HACKER_NEWS": "golang", "DEVTO": "go"}),
		topic("t-pg", "Postgres", map[coreapi.SignalSource]string{"DEVTO": "postgres"}),
	}}
	hn := &fakeSource{name: "HACKER_NEWS", items: items(3)}
	devto := &fakeSource{name: "DEVTO", items: items(2)}
	rad := newRadar(api, hn, devto)

	if err := rad.RunOnce(context.Background()); err != nil {
		t.Fatalf("RunOnce: %v", err)
	}

	if got := hn.asked(); len(got) != 1 || got[0] != "golang" {
		t.Errorf("hacker news was asked %v, want the topic's own query", got)
	}
	if got := devto.asked(); len(got) != 2 {
		t.Errorf("dev.to was asked %v, want one query per topic that names it", got)
	}

	// 3 from HN for Go, 2 from dev.to for Go, 2 from dev.to for Postgres.
	ingested := api.ingested()
	if len(ingested) != 7 {
		t.Fatalf("expected 7 signals, got %d", len(ingested))
	}
	for _, s := range ingested {
		if !s.FetchedAt.Equal(now) {
			t.Errorf("fetchedAt = %s, want the run's clock", s.FetchedAt)
		}
		if s.Score < 0 || s.Score > 100 {
			t.Errorf("score = %d, outside the range the core API accepts", s.Score)
		}
	}
}

// The point of the whole slice: a stored signal has to name the topic it was fetched for,
// or the panel cannot filter by one.
func TestRunOnceStampsEachSignalWithItsTopic(t *testing.T) {
	api := &fakeAPI{topics: []coreapi.TopicQueries{
		topic("t-go", "Go", map[coreapi.SignalSource]string{"DEVTO": "go"}),
		topic("t-pg", "Postgres", map[coreapi.SignalSource]string{"DEVTO": "postgres"}),
	}}
	devto := &fakeSource{name: "DEVTO", byQuery: map[string][]source.Item{
		"go":       items(2),
		"postgres": items(3),
	}}

	if err := newRadar(api, devto).RunOnce(context.Background()); err != nil {
		t.Fatalf("RunOnce: %v", err)
	}

	byTopic := map[string]int{}
	for _, s := range api.ingested() {
		if s.TopicID == nil {
			t.Fatalf("signal %q stored with no topic", s.ExternalID)
		}
		byTopic[*s.TopicID]++
	}
	if byTopic["t-go"] != 2 || byTopic["t-pg"] != 3 {
		t.Errorf("signals not attributed to their topics: %v", byTopic)
	}
}

// Each (source, topic) fetch is normalized on its own, so a quiet topic's leader scores 100
// within its own topic instead of being buried under whatever is trending globally.
func TestRunOnceScoresWithinATopicNotAcrossThem(t *testing.T) {
	api := &fakeAPI{topics: []coreapi.TopicQueries{
		topic("t-busy", "Busy", map[coreapi.SignalSource]string{"DEVTO": "busy"}),
		topic("t-quiet", "Quiet", map[coreapi.SignalSource]string{"DEVTO": "quiet"}),
	}}
	devto := &fakeSource{name: "DEVTO", byQuery: map[string][]source.Item{
		"busy":  {{ExternalID: "b", Title: "busy leader", NativeScore: 5000}},
		"quiet": {{ExternalID: "q", Title: "quiet leader", NativeScore: 12}},
	}}

	if err := newRadar(api, devto).RunOnce(context.Background()); err != nil {
		t.Fatalf("RunOnce: %v", err)
	}

	for _, s := range api.ingested() {
		if s.Score != 100 {
			t.Errorf("%q scored %d; each topic's leader should reach 100 within its own topic",
				s.Title, s.Score)
		}
	}
}

// A topic may name a source this worker is not running — one left out of RADAR_SOURCES, or
// one the core API knows and the poller does not.
func TestRunOnceSkipsQueriesForSourcesItDoesNotRun(t *testing.T) {
	api := &fakeAPI{topics: []coreapi.TopicQueries{
		topic("t-go", "Go", map[coreapi.SignalSource]string{"DEVTO": "go", "REDDIT": "golang"}),
	}}
	devto := &fakeSource{name: "DEVTO", items: items(2)}

	if err := newRadar(api, devto).RunOnce(context.Background()); err != nil {
		t.Fatalf("an unrunnable source should be skipped, not fail the pass: %v", err)
	}
	if len(api.ingested()) != 2 {
		t.Errorf("expected the runnable source's signals, got %d", len(api.ingested()))
	}
}

// The fan-out's point: one dead source must not stall a run.
func TestRunOnceIsolatesAFailingFetch(t *testing.T) {
	api := &fakeAPI{topics: []coreapi.TopicQueries{
		topic("t-go", "Go", map[coreapi.SignalSource]string{"HACKER_NEWS": "golang", "DEVTO": "go"}),
	}}
	rad := newRadar(api,
		&fakeSource{name: "HACKER_NEWS", err: errors.New("upstream down")},
		&fakeSource{name: "DEVTO", items: items(4)},
	)

	if err := rad.RunOnce(context.Background()); err != nil {
		t.Fatalf("a partially failed run should still succeed: %v", err)
	}

	ingested := api.ingested()
	if len(ingested) != 4 {
		t.Fatalf("expected the healthy source's 4 signals, got %d", len(ingested))
	}
	for _, s := range ingested {
		if s.Source != "DEVTO" {
			t.Errorf("unexpected source %q from a failed fetch", s.Source)
		}
	}
}

// Failure isolation is per (source, topic), not per source: one topic's query being rejected
// must not cost that source's other topics.
func TestRunOnceIsolatesOneTopicOnASharedSource(t *testing.T) {
	api := &fakeAPI{topics: []coreapi.TopicQueries{
		topic("t-good", "Good", map[coreapi.SignalSource]string{"DEVTO": "good"}),
		topic("t-bad", "Bad", map[coreapi.SignalSource]string{"DEVTO": "bad"}),
	}}
	devto := &failingQuerySource{
		fakeSource: fakeSource{name: "DEVTO", byQuery: map[string][]source.Item{"good": items(3)}},
		badQuery:   "bad",
	}

	if err := newRadar(api, devto).RunOnce(context.Background()); err != nil {
		t.Fatalf("one bad query should not fail the pass: %v", err)
	}
	ingested := api.ingested()
	if len(ingested) != 3 {
		t.Fatalf("expected the healthy topic's 3 signals, got %d", len(ingested))
	}
	if ingested[0].TopicID == nil || *ingested[0].TopicID != "t-good" {
		t.Errorf("expected the surviving signals to belong to the healthy topic")
	}
}

func TestRunOnceFailsWhenEveryFetchFails(t *testing.T) {
	api := &fakeAPI{topics: []coreapi.TopicQueries{
		topic("t-go", "Go", map[coreapi.SignalSource]string{"HACKER_NEWS": "golang", "DEVTO": "go"}),
	}}
	rad := newRadar(api,
		&fakeSource{name: "HACKER_NEWS", err: errors.New("hn down")},
		&fakeSource{name: "DEVTO", err: errors.New("devto down")},
	)

	err := rad.RunOnce(context.Background())
	if err == nil {
		t.Fatal("expected an error when nothing could be collected")
	}
	// The aggregate should name both, so one log line explains the whole run.
	for _, want := range []string{"hn down", "devto down"} {
		if !strings.Contains(err.Error(), want) {
			t.Errorf("error %q should mention %q", err, want)
		}
	}
	if len(api.batches) != 0 {
		t.Error("nothing should be ingested when every fetch failed")
	}
}

// Without the work list there is nothing the radar could legitimately fetch: every signal it
// stores is scoped to a topic, and it has no fallback listing to fall back to.
func TestRunOnceFailsWhenTheWorkListIsUnavailable(t *testing.T) {
	api := &fakeAPI{topicsErr: errors.New("core api down")}
	devto := &fakeSource{name: "DEVTO", items: items(2)}

	if err := newRadar(api, devto).RunOnce(context.Background()); err == nil {
		t.Fatal("expected an unavailable work list to fail the pass")
	}
	if len(devto.asked()) != 0 {
		t.Error("no source should be polled without a work list")
	}
}

func TestRunOnceWithoutTopicsPollsNothing(t *testing.T) {
	// Not an error — the usual cause is that no topic has been created yet. The radar must
	// not fall back to polling whatever is globally popular, which is what topics replaced.
	api := &fakeAPI{}
	devto := &fakeSource{name: "DEVTO", items: items(5)}

	if err := newRadar(api, devto).RunOnce(context.Background()); err != nil {
		t.Fatalf("a pass with no topics should succeed quietly: %v", err)
	}
	if len(devto.asked()) != 0 {
		t.Errorf("dev.to was polled %v with no topic to poll for", devto.asked())
	}
	if len(api.batches) != 0 {
		t.Error("nothing should be ingested when there is nothing to poll")
	}
}

func TestRunOnceSkipsIngestWhenNothingCollected(t *testing.T) {
	// A source succeeding but returning nothing is not an error — a narrow topic may simply
	// have no matches this hour, and an empty POST would be pure noise.
	api := &fakeAPI{topics: []coreapi.TopicQueries{
		topic("t-go", "Go", map[coreapi.SignalSource]string{"DEVTO": "go"}),
	}}
	rad := newRadar(api, &fakeSource{name: "DEVTO"})

	if err := rad.RunOnce(context.Background()); err != nil {
		t.Fatalf("an empty but healthy run should succeed: %v", err)
	}
	if len(api.batches) != 0 {
		t.Errorf("expected no ingest call, got %d", len(api.batches))
	}
}

func TestRunOnceChunksLargeHarvests(t *testing.T) {
	api := &fakeAPI{topics: []coreapi.TopicQueries{
		topic("t-go", "Go", map[coreapi.SignalSource]string{"HACKER_NEWS": "golang"}),
	}}
	oversized := coreapi.MaxIngestBatch + 10
	rad := newRadar(api, &fakeSource{name: "HACKER_NEWS", items: items(oversized)})

	if err := rad.RunOnce(context.Background()); err != nil {
		t.Fatalf("RunOnce: %v", err)
	}

	if len(api.batches) != 2 {
		t.Fatalf("expected the harvest to be split into 2 batches, got %d", len(api.batches))
	}
	for i, batch := range api.batches {
		if len(batch) > coreapi.MaxIngestBatch {
			t.Errorf("batch %d has %d signals, over the core API's limit of %d",
				i, len(batch), coreapi.MaxIngestBatch)
		}
	}
	if total := len(api.ingested()); total != oversized {
		t.Errorf("ingested %d signals across batches, want %d", total, oversized)
	}
}

func TestRunOnceFailsWhenIngestFails(t *testing.T) {
	api := &fakeAPI{
		topics: []coreapi.TopicQueries{
			topic("t-go", "Go", map[coreapi.SignalSource]string{"DEVTO": "go"}),
		},
		err: errors.New("core api down"),
	}
	rad := newRadar(api, &fakeSource{name: "DEVTO", items: items(2)})

	if err := rad.RunOnce(context.Background()); err == nil {
		t.Fatal("expected a failed ingest to surface")
	}
}

func TestRunOnceWithoutSources(t *testing.T) {
	if err := newRadar(&fakeAPI{}).RunOnce(context.Background()); err == nil {
		t.Fatal("expected an error when no sources are configured")
	}
}

// A source's own topics are polled one after another, so a pass's request burst against one
// host does not grow with the number of topics — GitHub's unauthenticated search allows ten
// requests a minute.
func TestRunOnceSerializesOneSourcesTopics(t *testing.T) {
	api := &fakeAPI{topics: []coreapi.TopicQueries{
		topic("t-1", "One", map[coreapi.SignalSource]string{"DEVTO": "one"}),
		topic("t-2", "Two", map[coreapi.SignalSource]string{"DEVTO": "two"}),
		topic("t-3", "Three", map[coreapi.SignalSource]string{"DEVTO": "three"}),
	}}
	devto := &fakeSource{name: "DEVTO", items: items(1)}

	if err := newRadar(api, devto).RunOnce(context.Background()); err != nil {
		t.Fatalf("RunOnce: %v", err)
	}
	if len(devto.asked()) != 3 {
		t.Fatalf("expected 3 queries, got %v", devto.asked())
	}
	if devto.overlap {
		t.Error("two queries to the same source overlapped; a source's topics must run in sequence")
	}
}

// ...while different sources still overlap, which is where the wall-clock saving is.
func TestRunOncePollsDifferentSourcesConcurrently(t *testing.T) {
	release := make(chan struct{})
	api := &fakeAPI{topics: []coreapi.TopicQueries{
		topic("t-go", "Go", map[coreapi.SignalSource]string{"HACKER_NEWS": "golang", "DEVTO": "go"}),
	}}
	hn := &fakeSource{name: "HACKER_NEWS", items: items(1), blockUnti: release}
	devto := &fakeSource{name: "DEVTO", items: items(1), blockUnti: release}

	done := make(chan error, 1)
	go func() { done <- newRadar(api, hn, devto).RunOnce(context.Background()) }()

	// Both sources must be inside Fetch at once, or unblocking them together would deadlock
	// the pass until the test times out.
	deadline := time.After(2 * time.Second)
	for {
		if len(hn.asked()) == 1 && len(devto.asked()) == 1 {
			break
		}
		select {
		case <-deadline:
			t.Fatal("sources were not polled concurrently")
		default:
		}
	}
	close(release)

	if err := <-done; err != nil {
		t.Fatalf("RunOnce: %v", err)
	}
}

func TestRunStopsOnContextCancellation(t *testing.T) {
	api := &fakeAPI{topics: []coreapi.TopicQueries{
		topic("t-go", "Go", map[coreapi.SignalSource]string{"DEVTO": "go"}),
	}}
	rad := newRadar(api, &fakeSource{name: "DEVTO", items: items(1)})

	ctx, cancel := context.WithCancel(context.Background())
	cancel()

	err := rad.Run(ctx, time.Hour)
	if !errors.Is(err, context.Canceled) {
		t.Fatalf("Run error = %v, want context.Canceled", err)
	}
}

// A failing pass must not take the loop down — the radar depends on third-party APIs that
// can be briefly unavailable without that being an emergency.
func TestRunSurvivesAFailingPass(t *testing.T) {
	api := &fakeAPI{topics: []coreapi.TopicQueries{
		topic("t-go", "Go", map[coreapi.SignalSource]string{"DEVTO": "go"}),
	}}
	failing := &fakeSource{name: "DEVTO", err: fmt.Errorf("transient")}
	rad := newRadar(api, failing)

	ctx, cancel := context.WithTimeout(context.Background(), 50*time.Millisecond)
	defer cancel()

	err := rad.Run(ctx, 10*time.Millisecond)
	if !errors.Is(err, context.DeadlineExceeded) {
		t.Fatalf("Run error = %v, want the loop to keep going until the context expired", err)
	}
}

// failingQuerySource fails one named query and serves the rest normally.
type failingQuerySource struct {
	fakeSource
	badQuery string
}

func (f *failingQuerySource) Fetch(ctx context.Context, query string) ([]source.Item, error) {
	if query == f.badQuery {
		f.mu.Lock()
		f.queries = append(f.queries, query)
		f.mu.Unlock()
		return nil, errors.New("query rejected")
	}
	return f.fakeSource.Fetch(ctx, query)
}
