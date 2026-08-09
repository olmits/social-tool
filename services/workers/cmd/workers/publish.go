package main

import (
	"context"
	"flag"
	"log/slog"

	"github.com/olmits/social-tool/services/workers/internal/config"
	"github.com/olmits/social-tool/services/workers/internal/coreapi"
	"github.com/olmits/social-tool/services/workers/internal/worker"
)

// publish runs the publisher.
//
// Phase 0 scope: prove the process starts, reaches the core API, and shuts down cleanly.
// The poll-and-publish loop arrives in Phase 1, along with the platform adapters.
func publish(ctx context.Context, cfg config.Config, logger *slog.Logger, args []string) error {
	flags := flag.NewFlagSet(cmdPublish, flag.ContinueOnError)
	if err := flags.Parse(args); err != nil {
		return err
	}

	client := coreapi.New(cfg.CoreAPIURL, cfg.CoreAPIKey, cfg.HTTPTimeout, logger)

	return worker.Run(ctx, "publisher", logger, func(ctx context.Context) error {
		// Reachability is reported, not enforced: the API may well start after this
		// worker does, and Phase 1's loop retries on every tick regardless.
		if health, err := client.Health(ctx); err != nil {
			logger.Warn("core api unreachable at startup", "url", cfg.CoreAPIURL, "error", err)
		} else {
			logger.Info("core api reachable", "url", cfg.CoreAPIURL, "status", health.Status)
		}

		<-ctx.Done()
		return ctx.Err()
	})
}
