// Command workers is the entrypoint for every background worker in the social tool.
// The worker to run is chosen by subcommand, so all three ship in one image and the
// split happens at the task-definition level.
package main

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"os"
	"os/signal"
	"syscall"

	"github.com/olmits/social-tool/services/workers/internal/config"
)

const (
	cmdPublish   = "publish"
	cmdPoll      = "poll"
	cmdAnalytics = "analytics"
)

func main() {
	if err := run(os.Args[1:]); err != nil {
		fmt.Fprintf(os.Stderr, "workers: %v\n", err)
		os.Exit(1)
	}
}

func run(args []string) error {
	if len(args) == 0 {
		usage()
		return errors.New("no subcommand given")
	}

	name, rest := args[0], args[1:]
	switch name {
	case "help", "-h", "--help":
		usage()
		return nil
	case cmdPublish, cmdPoll, cmdAnalytics:
	default:
		usage()
		return fmt.Errorf("unknown subcommand %q", name)
	}

	// Configuration is loaded only once a real subcommand is known, so `workers help`
	// works on a machine with nothing set up.
	cfg, err := config.Load()
	if err != nil {
		return fmt.Errorf("invalid configuration:\n%w", err)
	}

	logger := slog.New(slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{Level: cfg.LogLevel}))

	// ECS stops a task with SIGTERM. A publisher killed part-way through a post is the
	// worst failure mode in this service, so the shutdown plumbing exists from the start
	// even though Phase 0 has nothing in flight to protect.
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	switch name {
	case cmdPublish:
		return publish(ctx, cfg, logger, rest)
	default:
		return fmt.Errorf("the %s worker is not implemented yet", name)
	}
}

func usage() {
	fmt.Fprintf(os.Stderr, `workers — background workers for the social tool

usage:
  workers <command> [flags]

commands:
  %-9s publish scheduled drafts to their platform
  %-9s poll content sources into signals (not implemented yet)
  %-9s ingest per-post metrics (not implemented yet)
  %-9s show this message

environment:
  CORE_API_URL           base URL of the Java core API (default http://localhost:8080)
  CORE_API_KEY           shared X-API-Key for the core API (required)
  LOG_LEVEL              debug | info | warn | error (default info)
  HTTP_TIMEOUT           per-request timeout, as a Go duration (default 10s)
  POLL_INTERVAL          how often to check for due drafts (default 30s)
  BLUESKY_BASE_URL       AT Protocol PDS host (default https://bsky.social)
  CREDENTIALS_BACKEND    local | secretsmanager (default local)
  LOCAL_CREDENTIALS_DIR  credential directory for the local backend
                         (default ../api/.local-secrets)
  AWS_REGION             region for the secretsmanager backend (optional)
`, cmdPublish, cmdPoll, cmdAnalytics, "help")
}
