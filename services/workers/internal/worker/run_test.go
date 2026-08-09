package worker_test

import (
	"context"
	"errors"
	"log/slog"
	"testing"

	"github.com/olmits/social-tool/services/workers/internal/worker"
)

func discardLogger() *slog.Logger { return slog.New(slog.DiscardHandler) }

// A cancelled context is the shutdown signal, not a failure.
func TestRunTreatsContextCancellationAsCleanStop(t *testing.T) {
	ctx, cancel := context.WithCancel(t.Context())

	err := worker.Run(ctx, "publisher", discardLogger(), func(ctx context.Context) error {
		cancel()
		<-ctx.Done()
		return ctx.Err()
	})

	if err != nil {
		t.Fatalf("Run returned %v, want nil", err)
	}
}

func TestRunReturnsWorkerError(t *testing.T) {
	want := errors.New("boom")

	err := worker.Run(t.Context(), "publisher", discardLogger(), func(context.Context) error {
		return want
	})

	if !errors.Is(err, want) {
		t.Fatalf("Run returned %v, want %v", err, want)
	}
}

func TestRunReturnsNilWhenWorkerCompletes(t *testing.T) {
	called := false

	err := worker.Run(t.Context(), "publisher", discardLogger(), func(context.Context) error {
		called = true
		return nil
	})

	if err != nil {
		t.Fatalf("Run returned %v, want nil", err)
	}
	if !called {
		t.Error("Run did not invoke the worker function")
	}
}
