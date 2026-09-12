package radar_test

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"strconv"
	"strings"
	"testing"
	"time"

	"github.com/olmits/social-tool/services/workers/internal/coreapi"
	"github.com/olmits/social-tool/services/workers/internal/radar"
	"github.com/olmits/social-tool/services/workers/internal/source"
)

var now = time.Date(2026, 8, 22, 9, 0, 0, 0, time.UTC)

// --- fakes -------------------------------------------------------------------

type fakeAPI struct {
	batches [][]coreapi.Signal
	err     error
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

type fakeSource struct {
	name  string
	items []source.Item
	err   error
}

func (f *fakeSource) Name() string { return f.name }

func (f *fakeSource) Fetch(context.Context) ([]source.Item, error) {
	if f.err != nil {
		return nil, f.err
	}
	return f.items, nil
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

func newRadar(api radar.CoreAPI, sources ...source.Source) *radar.Radar {
	return radar.New(radar.Options{
		API:     api,
		Sources: sources,
		Now:     func() time.Time { return now },
	})
}

// --- tests -------------------------------------------------------------------

func TestRunOnceIngestsFromEverySource(t *testing.T) {
	api := &fakeAPI{}
	rad := newRadar(api,
		&fakeSource{name: "HACKER_NEWS", items: items(3)},
		&fakeSource{name: "DEVTO", items: items(2)},
	)

	if err := rad.RunOnce(context.Background()); err != nil {
		t.Fatalf("RunOnce: %v", err)
	}

	ingested := api.ingested()
	if len(ingested) != 5 {
		t.Fatalf("expected 5 signals, got %d", len(ingested))
	}

	bySource := map[coreapi.SignalSource]int{}
	for _, s := range ingested {
		bySource[s.Source]++
		if !s.FetchedAt.Equal(now) {
			t.Errorf("fetchedAt = %s, want the run's clock", s.FetchedAt)
		}
		if s.Score < 0 || s.Score > 100 {
			t.Errorf("score = %d, outside the range the core API accepts", s.Score)
		}
	}
	if bySource["HACKER_NEWS"] != 3 || bySource["DEVTO"] != 2 {
		t.Errorf("signals not attributed to their sources: %v", bySource)
	}
}

// The point of the radar's fan-out: one dead source must not stall a run.
func TestRunOnceIsolatesAFailingSource(t *testing.T) {
	api := &fakeAPI{}
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

func TestRunOnceFailsWhenEverySourceFails(t *testing.T) {
	api := &fakeAPI{}
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
		t.Error("nothing should be ingested when every source failed")
	}
}

func TestRunOnceSkipsIngestWhenNothingCollected(t *testing.T) {
	// Sources succeeding but returning nothing is not an error — there is simply nothing
	// to store, and an empty POST would be pure noise.
	api := &fakeAPI{}
	rad := newRadar(api, &fakeSource{name: "DEVTO"})

	if err := rad.RunOnce(context.Background()); err != nil {
		t.Fatalf("an empty but healthy run should succeed: %v", err)
	}
	if len(api.batches) != 0 {
		t.Errorf("expected no ingest call, got %d", len(api.batches))
	}
}

func TestRunOnceChunksLargeHarvests(t *testing.T) {
	api := &fakeAPI{}
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
	api := &fakeAPI{err: errors.New("core api down")}
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

func TestRunStopsOnContextCancellation(t *testing.T) {
	api := &fakeAPI{}
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
	api := &fakeAPI{}
	failing := &fakeSource{name: "DEVTO", err: fmt.Errorf("transient")}
	rad := newRadar(api, failing)

	ctx, cancel := context.WithTimeout(context.Background(), 50*time.Millisecond)
	defer cancel()

	err := rad.Run(ctx, 10*time.Millisecond)
	if !errors.Is(err, context.DeadlineExceeded) {
		t.Fatalf("Run error = %v, want the loop to keep going until the context expired", err)
	}
}
