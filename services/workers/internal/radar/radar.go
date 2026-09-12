// Package radar is the trend-radar loop: it polls every configured content source,
// normalizes what they return, and stores the result through the core API.
//
// Sources are polled concurrently and are failure-isolated. A source that errors, times
// out, or gets rate-limited is logged and skipped for that run; the rest still report. This
// matters more here than in the publisher — the radar depends on several third-party APIs
// it does not control, and any of them can be down without that being an emergency.
//
// Unlike the publisher, a run is safe to repeat: the core API upserts signals on
// (source, externalId), so overlapping or retried runs converge on the same rows. No claim
// or lease is needed.
package radar

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"slices"
	"sync"
	"time"

	"github.com/olmits/social-tool/services/workers/internal/coreapi"
	"github.com/olmits/social-tool/services/workers/internal/source"
)

// CoreAPI is the slice of the core API client the radar needs.
type CoreAPI interface {
	IngestSignals(ctx context.Context, signals []coreapi.Signal) (coreapi.IngestResult, error)
}

// Radar polls content sources and stores the signals they produce.
type Radar struct {
	api     CoreAPI
	sources []source.Source
	logger  *slog.Logger
	now     func() time.Time
}

// Options configures a Radar. Now is overridable so tests can assert on FetchedAt without
// depending on the wall clock.
type Options struct {
	API     CoreAPI
	Sources []source.Source
	Logger  *slog.Logger
	Now     func() time.Time
}

// New builds a Radar.
func New(opts Options) *Radar {
	logger := opts.Logger
	if logger == nil {
		logger = slog.New(slog.DiscardHandler)
	}
	now := opts.Now
	if now == nil {
		now = time.Now
	}
	return &Radar{api: opts.API, sources: opts.Sources, logger: logger, now: now}
}

// Run polls every interval until ctx is cancelled, returning ctx.Err() on shutdown.
//
// A failed pass is logged and the loop continues: neither a flaky source nor a briefly
// unreachable core API should take the radar down.
func (r *Radar) Run(ctx context.Context, interval time.Duration) error {
	r.logger.Info("radar loop started", "interval", interval.String(), "sources", r.sourceNames())

	// Poll once straight away, so a restart doesn't leave the signal pool stale for a
	// full interval.
	r.pass(ctx)

	ticker := time.NewTicker(interval)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			return ctx.Err()
		case <-ticker.C:
			r.pass(ctx)
		}
	}
}

// pass runs one poll, logging rather than propagating its failure.
func (r *Radar) pass(ctx context.Context) {
	if err := r.RunOnce(ctx); err != nil && !errors.Is(err, context.Canceled) {
		r.logger.Error("radar pass failed", "error", err)
	}
}

// RunOnce polls every source once and stores what they returned.
//
// It fails only when nothing could be collected at all — every source erroring, or the
// ingest call itself failing. A run where some sources succeeded is a success, since the
// signals that did arrive are worth storing.
func (r *Radar) RunOnce(ctx context.Context) error {
	if len(r.sources) == 0 {
		return errors.New("no sources configured")
	}

	started := r.now()
	signals, failures := r.collect(ctx)

	if len(failures) == len(r.sources) {
		return fmt.Errorf("every source failed: %w", errors.Join(failures...))
	}
	if len(signals) == 0 {
		r.logger.Warn("radar pass collected no signals",
			"sources", len(r.sources), "failed", len(failures))
		return nil
	}

	var result coreapi.IngestResult
	for chunk := range slices.Chunk(signals, coreapi.MaxIngestBatch) {
		stored, err := r.api.IngestSignals(ctx, chunk)
		if err != nil {
			return fmt.Errorf("ingest %d signals: %w", len(chunk), err)
		}
		result.Received += stored.Received
		result.Created += stored.Created
		result.Updated += stored.Updated
	}

	r.logger.Info("radar pass complete",
		"received", result.Received,
		"created", result.Created,
		"updated", result.Updated,
		"failedSources", len(failures),
		"duration", r.now().Sub(started).Round(time.Millisecond).String())
	return nil
}

// collect polls every source concurrently, returning the signals gathered and the errors
// from whichever sources failed.
func (r *Radar) collect(ctx context.Context) ([]coreapi.Signal, []error) {
	type outcome struct {
		signals []coreapi.Signal
		err     error
	}
	outcomes := make([]outcome, len(r.sources))

	var wg sync.WaitGroup
	for i, src := range r.sources {
		wg.Add(1)
		go func() {
			defer wg.Done()

			fetchedAt := r.now()
			items, err := src.Fetch(ctx)
			if err != nil {
				outcomes[i] = outcome{err: fmt.Errorf("source %s: %w", src.Name(), err)}
				return
			}

			normalized := source.Normalize(src.Name(), items, fetchedAt)
			outcomes[i] = outcome{signals: toWire(normalized)}
			r.logger.Debug("source polled", "source", src.Name(), "items", len(normalized))
		}()
	}
	wg.Wait()

	var signals []coreapi.Signal
	var failures []error
	for _, o := range outcomes {
		if o.err != nil {
			// Logged per source as well as aggregated, so a persistently broken source is
			// visible without waiting for every source to fail.
			r.logger.Warn("source failed, skipping for this pass", "error", o.err)
			failures = append(failures, o.err)
			continue
		}
		signals = append(signals, o.signals...)
	}
	return signals, failures
}

func (r *Radar) sourceNames() []string {
	names := make([]string, 0, len(r.sources))
	for _, src := range r.sources {
		names = append(names, src.Name())
	}
	return names
}

// toWire maps domain signals onto the core API's ingest shape.
func toWire(signals []source.Signal) []coreapi.Signal {
	wire := make([]coreapi.Signal, 0, len(signals))
	for _, s := range signals {
		wire = append(wire, coreapi.Signal{
			Source:      coreapi.SignalSource(s.Source),
			ExternalID:  s.ExternalID,
			Title:       s.Title,
			URL:         s.URL,
			Score:       s.Score,
			NativeScore: s.NativeScore,
			RawPayload:  s.RawPayload,
			FetchedAt:   s.FetchedAt,
		})
	}
	return wire
}
