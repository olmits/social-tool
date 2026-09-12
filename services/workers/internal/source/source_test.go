package source_test

import (
	"encoding/json"
	"testing"
	"time"

	"github.com/olmits/social-tool/services/workers/internal/source"
)

var fetchedAt = time.Date(2026, 8, 22, 9, 0, 0, 0, time.UTC)

func item(id string, score int) source.Item {
	return source.Item{
		ExternalID:  id,
		Title:       "title " + id,
		URL:         "https://example.test/" + id,
		NativeScore: score,
		RawPayload:  json.RawMessage(`{"id":"` + id + `"}`),
	}
}

func TestNormalizeCarriesProvenanceAndFields(t *testing.T) {
	signals := source.Normalize("HACKER_NEWS", []source.Item{item("1", 10)}, fetchedAt)

	if len(signals) != 1 {
		t.Fatalf("expected 1 signal, got %d", len(signals))
	}
	got := signals[0]
	if got.Source != "HACKER_NEWS" {
		t.Errorf("source = %q, want HACKER_NEWS", got.Source)
	}
	if got.ExternalID != "1" || got.Title != "title 1" || got.URL != "https://example.test/1" {
		t.Errorf("fields not carried through: %+v", got)
	}
	if !got.FetchedAt.Equal(fetchedAt) {
		t.Errorf("fetchedAt = %s, want %s", got.FetchedAt, fetchedAt)
	}
	if string(got.RawPayload) != `{"id":"1"}` {
		t.Errorf("rawPayload = %s, want the item's payload verbatim", got.RawPayload)
	}
}

// Normalization is lossy and one-way: the panel shows the source's own count as engagement
// and cannot recover it from the rescaled score, so the raw number has to survive the batch.
func TestNormalizeKeepsTheNativeScoreAlongsideTheRescaledOne(t *testing.T) {
	signals := source.Normalize("HACKER_NEWS", []source.Item{
		item("leader", 842),
		item("trailer", 12),
	}, fetchedAt)

	if signals[0].NativeScore != 842 || signals[1].NativeScore != 12 {
		t.Errorf("native scores = %d, %d; want them carried through untouched",
			signals[0].NativeScore, signals[1].NativeScore)
	}
	// The leader is rescaled to 100 while keeping its real count — the two are independent.
	if signals[0].Score != 100 {
		t.Errorf("score = %d, want the batch leader rescaled to 100", signals[0].Score)
	}
	if signals[1].Score == signals[1].NativeScore {
		t.Errorf("score and nativeScore both = %d; the rescale should have moved one",
			signals[1].Score)
	}
}

func TestNormalizeEmptyBatch(t *testing.T) {
	if got := source.Normalize("DEVTO", nil, fetchedAt); got != nil {
		t.Errorf("expected nil for an empty batch, got %v", got)
	}
}

func TestNormalizeScoresTopItemAt100(t *testing.T) {
	signals := source.Normalize("DEVTO", []source.Item{
		item("low", 5),
		item("top", 500),
	}, fetchedAt)

	if signals[1].Score != 100 {
		t.Errorf("highest native score should map to 100, got %d", signals[1].Score)
	}
}

// The log curve exists so a runaway leader does not flatten the rest of the field. On a
// linear scale an item at 10%% of the leader would score 10; it must land well above that.
func TestNormalizeKeepsMidFieldDistinguishable(t *testing.T) {
	signals := source.Normalize("HACKER_NEWS", []source.Item{
		item("leader", 2000),
		item("middle", 200),
	}, fetchedAt)

	middle := signals[1].Score
	if middle <= 20 {
		t.Errorf("mid-field score = %d; a log scale should lift it well above the linear 10", middle)
	}
	if middle >= 100 {
		t.Errorf("mid-field score = %d; only the leader should reach 100", middle)
	}
}

func TestNormalizeScoresStayInRange(t *testing.T) {
	// The core API rejects anything outside 0-100, so the rescale must not overshoot at
	// either end — including for negative or zero native scores.
	signals := source.Normalize("GITHUB_TRENDING", []source.Item{
		item("negative", -5),
		item("zero", 0),
		item("one", 1),
		item("max", 9999),
	}, fetchedAt)

	for _, s := range signals {
		if s.Score < 0 || s.Score > 100 {
			t.Errorf("score for %s = %d, outside the accepted 0-100", s.ExternalID, s.Score)
		}
	}
}

func TestNormalizeAllZeroScores(t *testing.T) {
	signals := source.Normalize("DEVTO", []source.Item{item("a", 0), item("b", 0)}, fetchedAt)

	for _, s := range signals {
		if s.Score != 0 {
			t.Errorf("score for %s = %d, want 0 when nothing has any traction", s.ExternalID, s.Score)
		}
	}
}
