package publisher

import (
	"context"
	"errors"
	"log/slog"
	"net/http"
	"time"

	"github.com/olmits/social-tool/services/workers/internal/coreapi"
)

const (
	callbackAttempts = 3
	callbackBackoff  = time.Second
)

// recordSuccess tells the core API the post went out.
//
// This is the one call in the loop that retries. Once a post is live, failing to record it
// is the only error here that can do real damage: the draft stays SCHEDULED, so the next
// pass would publish it a second time. Retrying shrinks that window. It does not close it —
// that needs a claim on the draft, which the core API does not yet offer.
func (p *Publisher) recordSuccess(ctx context.Context, logger *slog.Logger, draftID, remoteID string) {
	err := p.retryCallback(ctx, func(ctx context.Context) error {
		_, err := p.api.MarkPublished(ctx, draftID, remoteID)
		if err != nil && coreapi.IsConflict(err) {
			// SCHEDULED is the only state markPublished accepts, so a conflict means an
			// earlier attempt already recorded this post. There is nothing left to do.
			logger.Warn("draft was already marked published", "remoteId", remoteID)
			return nil
		}
		return err
	})

	if err != nil {
		logger.Error("post is live but was not recorded; the draft is still SCHEDULED and will publish again",
			"remoteId", remoteID, "error", err)
	}
}

// recordFailure tells the core API the publish attempt failed.
func (p *Publisher) recordFailure(ctx context.Context, logger *slog.Logger, draftID, reason string) {
	if _, err := p.api.MarkFailed(ctx, draftID, reason); err != nil {
		// Nothing was posted, so a missed callback is harmless: the draft stays
		// SCHEDULED and the next pass tries the whole thing again.
		logger.Error("could not record publish failure", "error", err)
	}
}

func (p *Publisher) retryCallback(ctx context.Context, call func(context.Context) error) error {
	var err error
	for attempt := 1; attempt <= callbackAttempts; attempt++ {
		if err = call(ctx); err == nil {
			return nil
		}
		if !retryable(err) || attempt == callbackAttempts {
			return err
		}

		select {
		case <-ctx.Done():
			return err
		case <-time.After(time.Duration(attempt) * callbackBackoff):
		}
	}
	return err
}

// retryable reports whether another attempt could plausibly succeed. A transport failure
// might; a 404 or a validation error will not.
func retryable(err error) bool {
	var apiErr *coreapi.APIError
	if !errors.As(err, &apiErr) {
		return true
	}
	return apiErr.StatusCode == http.StatusTooManyRequests || apiErr.StatusCode >= 500
}
