package main

import (
	"context"
	"flag"
	"fmt"
	"log/slog"

	"github.com/olmits/social-tool/services/workers/internal/adapter"
	"github.com/olmits/social-tool/services/workers/internal/adapter/bluesky"
	"github.com/olmits/social-tool/services/workers/internal/config"
	"github.com/olmits/social-tool/services/workers/internal/coreapi"
	"github.com/olmits/social-tool/services/workers/internal/creds"
	"github.com/olmits/social-tool/services/workers/internal/publisher"
	"github.com/olmits/social-tool/services/workers/internal/worker"
)

// publish runs the publisher: poll the core API for drafts whose scheduled time has passed,
// post them through the platform adapter, and report each outcome back.
func publish(ctx context.Context, cfg config.Config, logger *slog.Logger, args []string) error {
	flags := flag.NewFlagSet(cmdPublish, flag.ContinueOnError)
	once := flags.Bool("once", false, "run a single pass and exit, instead of looping")
	if err := flags.Parse(args); err != nil {
		return err
	}

	client := coreapi.New(cfg.CoreAPIURL, cfg.CoreAPIKey, cfg.HTTPTimeout, logger)

	credentials, err := credentialStore(ctx, cfg)
	if err != nil {
		return err
	}

	pub := publisher.New(publisher.Options{
		API:         client,
		Credentials: credentials,
		Adapters: map[coreapi.Platform]adapter.Factory{
			coreapi.PlatformBluesky: bluesky.NewFactory(cfg.BlueskyBaseURL, cfg.HTTPTimeout),
		},
		Logger: logger,
	})

	return worker.Run(ctx, "publisher", logger, func(ctx context.Context) error {
		// Reachability is reported, not enforced: the API may well start after this
		// worker does, and the loop retries on every tick regardless.
		if health, err := client.Health(ctx); err != nil {
			logger.Warn("core api unreachable at startup", "url", cfg.CoreAPIURL, "error", err)
		} else {
			logger.Info("core api reachable", "url", cfg.CoreAPIURL, "status", health.Status)
		}

		if *once {
			return pub.RunOnce(ctx)
		}
		return pub.Run(ctx, cfg.PollInterval)
	})
}

func credentialStore(ctx context.Context, cfg config.Config) (creds.Store, error) {
	switch cfg.Credentials.Backend {
	case config.BackendSecretsManager:
		return creds.NewSecretsManagerStore(ctx, cfg.Credentials.AWSRegion)
	case config.BackendLocal:
		return creds.NewLocalStore(cfg.Credentials.LocalDir), nil
	default:
		return nil, fmt.Errorf("unknown credential backend %q", cfg.Credentials.Backend)
	}
}
