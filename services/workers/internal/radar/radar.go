// Package radar is the trend-radar loop: it asks the core API what the user writes about,
// polls every configured content source for each of those topics, normalizes what they
// return, and stores the result.
//
// Topics are what make the radar worth running. Without them a pass stores whatever is
// globally popular; with them every signal is scoped to a subject the user chose, and
// carries the topic id that the panel filters and ranks by. A pass with no enabled topics
// therefore polls nothing at all rather than falling back to a global listing — the poller
// has nothing to ask for.
//
// A pass is failure-isolated down to the individual (source, topic) fetch: one that errors,
// times out, or gets rate-limited is logged and skipped, and every other pair still reports.
// This matters more here than in the publisher — the radar depends on several third-party
// APIs it does not control, and any of them can be down, or reject one topic's query, without
// that being an emergency.
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
	"maps"
	"slices"
	"sync"
	"time"

	"github.com/olmits/social-tool/services/workers/internal/coreapi"
	"github.com/olmits/social-tool/services/workers/internal/source"
)

// CoreAPI is the slice of the core API client the radar needs.
type CoreAPI interface {
	ListTopicQueries(ctx context.Context) ([]coreapi.TopicQueries, error)
	IngestSignals(ctx context.Context, signals []coreapi.Signal) (coreapi.IngestResult, error)
}

// Radar polls content sources and stores the signals they produce.
type Radar struct {
	api CoreAPI
	// sources is keyed by SignalSource name, because that is how a topic names the source
	// it wants polled. Planning a pass is a lookup per query, not a scan per query.
	sources map[string]source.Source
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
	sources := make(map[string]source.Source, len(opts.Sources))
	for _, src := range opts.Sources {
		sources[src.Name()] = src
	}
	return &Radar{api: opts.API, sources: sources, logger: logger, now: now}
}

// fetch is one (source, topic) pair to poll. It is both the unit a pass fans out over and
// the unit it isolates failures at: a topic that GitHub rejects costs that topic on that
// source, and nothing else in the run.
type fetch struct {
	src source.Source
	// topicID goes onto every signal the fetch produces — the whole point of the pass.
	topicID string
	// topic is the topic's name, carried for logging only. Errors that say "topic 4f2a..."
	// are not worth reading.
	topic string
	query string
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

// RunOnce runs one pass: fetch the work list, poll every (source, topic) pair on it, and
// store what came back.
//
// It fails only when nothing could be collected at all — the work list being unavailable,
// every fetch erroring, or the ingest call itself failing. A pass where some fetches
// succeeded is a success, since the signals that did arrive are worth storing.
func (r *Radar) RunOnce(ctx context.Context) error {
	if len(r.sources) == 0 {
		return errors.New("no sources configured")
	}

	started := r.now()

	// The work list comes first and is not optional: every signal the radar stores is scoped
	// to a topic, so without it there is nothing this pass could legitimately fetch.
	topics, err := r.api.ListTopicQueries(ctx)
	if err != nil {
		return fmt.Errorf("fetch topic queries: %w", err)
	}

	work := r.plan(topics)
	if len(work) == 0 {
		// Not an error, and the usual cause is simply that no topic has been created yet.
		// Warn rather than Info because a radar with nothing to poll is doing no work at
		// all, which is worth noticing in a log that is otherwise quiet.
		r.logger.Warn("radar pass has nothing to poll; create a topic with a query for a source this worker runs",
			"topics", len(topics), "sources", len(r.sources))
		return nil
	}

	signals, failures := r.collect(ctx, work)

	if len(failures) == countFetches(work) {
		return fmt.Errorf("every fetch failed: %w", errors.Join(failures...))
	}
	if len(signals) == 0 {
		r.logger.Warn("radar pass collected no signals",
			"fetches", countFetches(work), "failed", len(failures))
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
		"topics", len(topics),
		"failedFetches", len(failures),
		"duration", r.now().Sub(started).Round(time.Millisecond).String())
	return nil
}

// plan turns the work list into the fetches this worker can actually make, grouped by source.
//
// Grouped rather than flat because that grouping is what bounds the pass's concurrency —
// see collect.
func (r *Radar) plan(topics []coreapi.TopicQueries) map[string][]fetch {
	work := make(map[string][]fetch, len(r.sources))
	for _, topic := range topics {
		// Sorted so a pass polls in the same order every time, which makes two runs'
		// logs comparable. Map iteration order alone would not.
		for _, name := range slices.Sorted(maps.Keys(topic.Queries)) {
			src, running := r.sources[string(name)]
			if !running {
				// A topic may name a source this worker does not run: one left out of
				// RADAR_SOURCES, or one the core API knows and the poller does not
				// (Reddit, Product Hunt). The topic is simply not polled there, which is
				// the same outcome as having no query for it.
				r.logger.Debug("skipping query for a source this worker does not run",
					"source", name, "topic", topic.Name)
				continue
			}
			work[string(name)] = append(work[string(name)], fetch{
				src:     src,
				topicID: topic.TopicID,
				topic:   topic.Name,
				query:   topic.Queries[name],
			})
		}
	}
	return work
}

// collect runs the planned fetches, returning the signals gathered and the errors from
// whichever fetches failed.
//
// Sources run concurrently; within a source its topics run one after another. Polling every
// pair at once would multiply a pass's request burst by the number of topics, and it is
// per-host rate limits the radar has to stay inside — GitHub's unauthenticated search allows
// ten requests a minute. Serializing a source's own queries keeps at most one request per
// host in flight while still overlapping the hosts against each other, which is where the
// wall-clock saving actually is.
func (r *Radar) collect(ctx context.Context, work map[string][]fetch) ([]coreapi.Signal, []error) {
	type outcome struct {
		signals []coreapi.Signal
		errs    []error
	}
	names := slices.Sorted(maps.Keys(work))
	outcomes := make([]outcome, len(names))

	var wg sync.WaitGroup
	for i, name := range names {
		wg.Add(1)
		go func() {
			defer wg.Done()

			for _, f := range work[name] {
				fetchedAt := r.now()
				items, err := f.src.Fetch(ctx, f.query)
				if err != nil {
					outcomes[i].errs = append(outcomes[i].errs,
						fmt.Errorf("source %s, topic %s: %w", name, f.topic, err))
					continue
				}

				// One Normalize call per (source, topic) batch, so scores are rescaled
				// within a topic rather than against the source's global leader.
				normalized := source.Normalize(name, items, fetchedAt)
				outcomes[i].signals = append(outcomes[i].signals, toWire(normalized, f.topicID)...)
				r.logger.Debug("source polled",
					"source", name, "topic", f.topic, "items", len(normalized))
			}
		}()
	}
	wg.Wait()

	var signals []coreapi.Signal
	var failures []error
	for _, o := range outcomes {
		for _, err := range o.errs {
			// Logged per fetch as well as aggregated, so a persistently broken source is
			// visible without waiting for every fetch to fail.
			r.logger.Warn("fetch failed, skipping for this pass", "error", err)
			failures = append(failures, err)
		}
		signals = append(signals, o.signals...)
	}
	return signals, failures
}

func countFetches(work map[string][]fetch) int {
	total := 0
	for _, fetches := range work {
		total += len(fetches)
	}
	return total
}

func (r *Radar) sourceNames() []string {
	return slices.Sorted(maps.Keys(r.sources))
}

// toWire maps one fetch's domain signals onto the core API's ingest shape, stamping each
// with the topic the fetch was made for.
func toWire(signals []source.Signal, topicID string) []coreapi.Signal {
	wire := make([]coreapi.Signal, 0, len(signals))
	for _, s := range signals {
		wire = append(wire, coreapi.Signal{
			Source:      coreapi.SignalSource(s.Source),
			ExternalID:  s.ExternalID,
			Title:       s.Title,
			TopicID:     &topicID,
			URL:         s.URL,
			Score:       s.Score,
			NativeScore: s.NativeScore,
			RawPayload:  s.RawPayload,
			FetchedAt:   s.FetchedAt,
		})
	}
	return wire
}
