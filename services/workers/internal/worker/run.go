// Package worker holds the bootstrap shared by every worker process: lifecycle logging
// and shutdown semantics. Worker-specific logic lives in the function passed to Run.
package worker

import (
	"context"
	"errors"
	"log/slog"
	"time"
)

// Run executes fn as the named worker, logging its lifecycle.
//
// Cancelling ctx is the shutdown signal: fn is expected to return promptly, and a
// context.Canceled result is reported as a clean stop rather than a failure. Any other
// error is logged and returned.
func Run(ctx context.Context, name string, logger *slog.Logger, fn func(context.Context) error) error {
	logger = logger.With("worker", name)
	logger.Info("worker starting")

	started := time.Now()
	err := fn(ctx)
	uptime := time.Since(started).Round(time.Millisecond).String()

	if err != nil && !errors.Is(err, context.Canceled) {
		logger.Error("worker stopped with error", "uptime", uptime, "error", err)
		return err
	}

	logger.Info("worker stopped", "uptime", uptime)
	return nil
}
