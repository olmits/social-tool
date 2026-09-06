package main

import (
	"context"
	"flag"
	"fmt"
	"log/slog"
	"net/http"

	"github.com/olmits/social-tool/services/workers/internal/config"
	"github.com/olmits/social-tool/services/workers/internal/coreapi"
	"github.com/olmits/social-tool/services/workers/internal/radar"
	"github.com/olmits/social-tool/services/workers/internal/source"
	"github.com/olmits/social-tool/services/workers/internal/source/devto"
	"github.com/olmits/social-tool/services/workers/internal/source/github"
	"github.com/olmits/social-tool/services/workers/internal/source/hackernews"
	"github.com/olmits/social-tool/services/workers/internal/worker"
)

// poll runs the trend radar: fetch every configured content source, normalize what they
// return, and store it through the core API as drafting input.
func poll(ctx context.Context, cfg config.Config, logger *slog.Logger, args []string) error {
	flags := flag.NewFlagSet(cmdPoll, flag.ContinueOnError)
	once := flags.Bool("once", false, "run a single pass and exit, instead of looping")
	if err := flags.Parse(args); err != nil {
		return err
	}

	client := coreapi.New(cfg.CoreAPIURL, cfg.CoreAPIKey, cfg.HTTPTimeout, logger)

	sources, err := buildSources(cfg)
	if err != nil {
		return err
	}

	rad := radar.New(radar.Options{
		API:     client,
		Sources: sources,
		Logger:  logger,
	})

	return worker.Run(ctx, "radar", logger, func(ctx context.Context) error {
		// Reachability is reported, not enforced: the API may well start after this
		// worker does, and the loop retries on every tick regardless.
		if health, err := client.Health(ctx); err != nil {
			logger.Warn("core api unreachable at startup", "url", cfg.CoreAPIURL, "error", err)
		} else {
			logger.Info("core api reachable", "url", cfg.CoreAPIURL, "status", health.Status)
		}

		if *once {
			return rad.RunOnce(ctx)
		}
		return rad.Run(ctx, cfg.Radar.Interval)
	})
}

// buildSources instantiates the sources named in the configuration.
//
// Every source gets its own http.Client sharing the configured timeout: they are polled
// concurrently against unrelated hosts, so one host's slow connections should not occupy
// another's connection pool.
func buildSources(cfg config.Config) ([]source.Source, error) {
	sources := make([]source.Source, 0, len(cfg.Radar.Sources))
	for _, name := range cfg.Radar.Sources {
		httpClient := &http.Client{Timeout: cfg.HTTPTimeout}

		switch name {
		case config.SourceHackerNews:
			sources = append(sources, hackernews.New(httpClient, "", cfg.Radar.Limit))
		case config.SourceDevto:
			sources = append(sources, devto.New(httpClient, "", cfg.Radar.Limit, 0))
		case config.SourceGitHubTrending:
			sources = append(sources, github.New(httpClient, "", cfg.Radar.Limit, 0, cfg.Radar.GitHubToken))
		default:
			// Unreachable via Load, which validates RADAR_SOURCES against the same set.
			return nil, fmt.Errorf("unknown radar source %q", name)
		}
	}
	return sources, nil
}
