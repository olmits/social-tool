// Package source defines the trend radar's content sources and the normalized shape they
// produce. Adding a source is a new sub-package implementing Source plus one entry in the
// radar's registry — no change to the run loop.
//
// Sources return Items carrying their own native popularity number; the radar converts
// those to Signals, rescaling the score so items from different sources can sit in one
// ranked list. Keeping that split here means a Source never has to know how scoring works
// across the rest of the system.
package source

import (
	"context"
	"encoding/json"
	"math"
	"time"
)

// Item is one entry as its source sees it. Score is in whatever unit that source counts
// in — HN points, dev.to public reactions, GitHub stars gained today — and is comparable
// only against other items from the same source.
type Item struct {
	// ExternalID is the source's own identifier, stable across runs. It is half of the
	// dedup key the core API upserts on, so it must not embed anything that changes
	// between runs (a rank or a timestamp would defeat deduplication).
	ExternalID string
	// Title is the headline, used as drafting input.
	Title string
	// URL is where the item lives.
	URL string
	// NativeScore is the source's own popularity count. Non-negative.
	NativeScore int
	// RawPayload is the source's untouched JSON for this item.
	RawPayload json.RawMessage
}

// Signal is an Item with provenance attached and its score rescaled to 0-100. It carries no
// JSON tags on purpose: the wire shape the core API ingests belongs to the coreapi package,
// and the radar maps this across. Keeping them separate means a change to the endpoint's
// contract does not reach into the sources.
type Signal struct {
	Source     string
	ExternalID string
	Title      string
	URL        string
	// Score is NativeScore rescaled to 0-100 within this batch, for ranking across sources.
	Score int
	// NativeScore is the source's own count, carried through unchanged. Normalization is
	// lossy and one-way, so the panel could not recover this number from Score; it is what
	// the radar shows as engagement, while Score is what it ranks by.
	NativeScore int
	RawPayload  json.RawMessage
	FetchedAt   time.Time
}

// Source polls one content source.
type Source interface {
	// Name identifies the source and must match a SignalSource value in the core API.
	Name() string
	// Fetch retrieves the current listing. Returning an empty slice with a nil error is
	// valid and means the source had nothing to report.
	Fetch(ctx context.Context) ([]Item, error)
}

// Normalize converts a source's items into signals, rescaling NativeScore onto 0-100
// relative to the highest score in the batch.
//
// The rescale is logarithmic, not linear, because popularity distributions here have long
// tails: a front-page HN story can outscore the tenth item by an order of magnitude, and a
// linear scale would flatten everything below the leader into low single digits. Taking
// logs keeps the mid-field distinguishable, which is the part worth drafting from — the
// top item is obvious without a score.
//
// An empty batch returns nil. When every item scores zero, all results score zero.
func Normalize(name string, items []Item, fetchedAt time.Time) []Signal {
	if len(items) == 0 {
		return nil
	}

	maxNative := 0
	for _, item := range items {
		if item.NativeScore > maxNative {
			maxNative = item.NativeScore
		}
	}

	signals := make([]Signal, 0, len(items))
	for _, item := range items {
		signals = append(signals, Signal{
			Source:      name,
			ExternalID:  item.ExternalID,
			Title:       item.Title,
			URL:         item.URL,
			Score:       rescale(item.NativeScore, maxNative),
			NativeScore: item.NativeScore,
			RawPayload:  item.RawPayload,
			FetchedAt:   fetchedAt,
		})
	}
	return signals
}

// rescale maps native onto 0-100 against maxNative on a log curve. Negative inputs are
// treated as zero; the core API rejects anything outside 0-100, so this must not overshoot.
func rescale(native, maxNative int) int {
	if native <= 0 || maxNative <= 0 {
		return 0
	}
	if native >= maxNative {
		return 100
	}
	scaled := 100 * math.Log1p(float64(native)) / math.Log1p(float64(maxNative))
	return int(math.Round(scaled))
}
