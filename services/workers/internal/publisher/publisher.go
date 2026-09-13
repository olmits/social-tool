// Package publisher is the scheduled-publish loop: it asks the core API which drafts are
// due, publishes them through the right platform adapter, and reports each outcome back.
//
// It assumes a single running instance. The core API has no claim or lease on a draft, so
// two publishers polling the same queue would both see the same due draft and both post it.
// See services/api/DEFERRED.md §2.
package publisher

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"slices"
	"time"

	"github.com/olmits/social-tool/services/workers/internal/adapter"
	"github.com/olmits/social-tool/services/workers/internal/coreapi"
	"github.com/olmits/social-tool/services/workers/internal/creds"
)

// CoreAPI is the slice of the core API client the publisher needs.
type CoreAPI interface {
	ListDrafts(ctx context.Context, params coreapi.ListDraftsParams) ([]coreapi.Draft, error)
	GetAccount(ctx context.Context, id string) (coreapi.Account, error)
	MarkPublished(ctx context.Context, id, remoteID string) (coreapi.Draft, error)
	MarkFailed(ctx context.Context, id, reason string) (coreapi.Draft, error)
}

// Publisher polls for due drafts and publishes them.
type Publisher struct {
	api      CoreAPI
	creds    creds.Store
	adapters map[coreapi.Platform]adapter.Factory
	logger   *slog.Logger
	now      func() time.Time
}

// Options configures a Publisher. Now is overridable so tests can control due-time
// selection without sleeping.
type Options struct {
	API         CoreAPI
	Credentials creds.Store
	Adapters    map[coreapi.Platform]adapter.Factory
	Logger      *slog.Logger
	Now         func() time.Time
}

// New builds a Publisher.
func New(opts Options) *Publisher {
	logger := opts.Logger
	if logger == nil {
		logger = slog.New(slog.DiscardHandler)
	}
	now := opts.Now
	if now == nil {
		now = time.Now
	}
	return &Publisher{
		api:      opts.API,
		creds:    opts.Credentials,
		adapters: opts.Adapters,
		logger:   logger,
		now:      now,
	}
}

// Run polls every interval until ctx is cancelled, returning ctx.Err() on shutdown.
//
// A failed pass is logged and the loop continues: the core API being briefly unreachable
// should not take the publisher down.
func (p *Publisher) Run(ctx context.Context, interval time.Duration) error {
	p.logger.Info("publish loop started", "interval", interval.String())

	// Poll once straight away, so a restart doesn't leave a due draft waiting out a
	// full interval.
	p.pass(ctx)

	ticker := time.NewTicker(interval)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			return ctx.Err()
		case <-ticker.C:
			p.pass(ctx)
		}
	}
}

func (p *Publisher) pass(ctx context.Context) {
	if err := p.RunOnce(ctx); err != nil && !errors.Is(err, context.Canceled) {
		p.logger.Error("publish pass failed", "error", err)
	}
}

// RunOnce performs a single poll-and-publish pass. Per-draft failures are logged and do not
// abort the pass; only a failure to reach the core API returns an error.
func (p *Publisher) RunOnce(ctx context.Context) error {
	scheduled, err := p.api.ListDrafts(ctx, coreapi.ListDraftsParams{Status: coreapi.StatusScheduled})
	if err != nil {
		return fmt.Errorf("list scheduled drafts: %w", err)
	}

	for _, draft := range scheduled {
		if draft.ScheduledAt == nil {
			// Nothing will ever make this draft due; schedule() always sets the time,
			// so this means the row was tampered with.
			p.logger.Warn("scheduled draft has no scheduled time", "draft", draft.ID)
		}
	}

	due := DueDrafts(scheduled, p.now())
	if len(due) == 0 {
		p.logger.Debug("no drafts due", "scheduled", len(scheduled))
		return nil
	}

	p.logger.Info("publishing due drafts", "scheduled", len(scheduled), "due", len(due))
	for _, draft := range due {
		if err := ctx.Err(); err != nil {
			return err
		}
		p.publish(ctx, draft)
	}
	return nil
}

// DueDrafts selects the drafts that are scheduled and whose time has arrived, oldest first.
func DueDrafts(drafts []coreapi.Draft, now time.Time) []coreapi.Draft {
	due := make([]coreapi.Draft, 0, len(drafts))
	for _, draft := range drafts {
		if draft.Status != coreapi.StatusScheduled || draft.ScheduledAt == nil {
			continue
		}
		if draft.ScheduledAt.After(now) {
			continue
		}
		due = append(due, draft)
	}

	slices.SortFunc(due, func(a, b coreapi.Draft) int {
		return a.ScheduledAt.Compare(*b.ScheduledAt)
	})
	return due
}

func (p *Publisher) publish(ctx context.Context, draft coreapi.Draft) {
	logger := p.logger.With("draft", draft.ID, "platform", string(draft.Platform))

	target, err := p.open(ctx, draft)
	if err != nil {
		// Everything up to this point is environmental — an unreachable API, an
		// unreadable secret, a disconnected account. None of it is the draft's fault, so
		// the draft stays SCHEDULED and the next pass retries once the cause is fixed.
		// Marking it FAILED here would force the user to recreate a perfectly good draft.
		logger.Error("draft not publishable yet, leaving it scheduled", "error", err)
		return
	}

	remoteID, err := target.Post(ctx, draft.Content)
	if err != nil {
		// The platform itself rejected the post. That is a real publish failure.
		logger.Error("publish failed", "error", err)
		p.recordFailure(ctx, logger, draft.ID, err.Error())
		return
	}

	logger.Info("published", "remoteId", remoteID)
	p.recordSuccess(ctx, logger, draft.ID, remoteID)
}

// open resolves everything needed to publish a draft and authenticates with the platform.
func (p *Publisher) open(ctx context.Context, draft coreapi.Draft) (adapter.Adapter, error) {
	factory, ok := p.adapters[draft.Platform]
	if !ok {
		return nil, fmt.Errorf("no adapter registered for platform %s", draft.Platform)
	}

	account, err := p.api.GetAccount(ctx, draft.AccountID)
	if err != nil {
		return nil, fmt.Errorf("load account %s: %w", draft.AccountID, err)
	}
	if account.Status != coreapi.AccountActive {
		return nil, fmt.Errorf("account %s is %s", account.ID, account.Status)
	}
	if account.Platform != draft.Platform {
		return nil, fmt.Errorf("account %s is %s but the draft targets %s",
			account.ID, account.Platform, draft.Platform)
	}

	secret, err := p.creds.Resolve(ctx, account.CredentialRef)
	if err != nil {
		return nil, fmt.Errorf("resolve credential for account %s: %w", account.ID, err)
	}

	instance := ""
	if account.Instance != nil {
		instance = *account.Instance
	}

	return factory.ForAccount(ctx, adapter.Account{
		Handle:   account.Handle,
		Instance: instance,
		Secret:   secret,
	})
}
