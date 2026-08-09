package config_test

import (
	"log/slog"
	"strings"
	"testing"
	"time"

	"github.com/olmits/social-tool/services/workers/internal/config"
)

func TestLoadAppliesDefaults(t *testing.T) {
	t.Setenv("CORE_API_KEY", "local-dev-api-key")

	cfg, err := config.Load()
	if err != nil {
		t.Fatalf("Load: %v", err)
	}

	if cfg.CoreAPIURL != "http://localhost:8080" {
		t.Errorf("CoreAPIURL = %q, want http://localhost:8080", cfg.CoreAPIURL)
	}
	if cfg.LogLevel != slog.LevelInfo {
		t.Errorf("LogLevel = %v, want info", cfg.LogLevel)
	}
	if cfg.HTTPTimeout != 10*time.Second {
		t.Errorf("HTTPTimeout = %v, want 10s", cfg.HTTPTimeout)
	}
}

func TestLoadReadsOverridesAndTrimsTrailingSlash(t *testing.T) {
	t.Setenv("CORE_API_KEY", "k")
	t.Setenv("CORE_API_URL", "https://api.example.com/")
	t.Setenv("LOG_LEVEL", "DEBUG")
	t.Setenv("HTTP_TIMEOUT", "45s")

	cfg, err := config.Load()
	if err != nil {
		t.Fatalf("Load: %v", err)
	}

	// A trailing slash would produce "//drafts" once the client appends a path.
	if cfg.CoreAPIURL != "https://api.example.com" {
		t.Errorf("CoreAPIURL = %q, want https://api.example.com", cfg.CoreAPIURL)
	}
	if cfg.LogLevel != slog.LevelDebug {
		t.Errorf("LogLevel = %v, want debug", cfg.LogLevel)
	}
	if cfg.HTTPTimeout != 45*time.Second {
		t.Errorf("HTTPTimeout = %v, want 45s", cfg.HTTPTimeout)
	}
}

func TestLoadRejectsBadValues(t *testing.T) {
	tests := []struct {
		name    string
		env     map[string]string
		wantVar string
	}{
		{"missing api key", map[string]string{}, "CORE_API_KEY"},
		{"relative url", map[string]string{"CORE_API_KEY": "k", "CORE_API_URL": "localhost:8080"}, "CORE_API_URL"},
		{"bad log level", map[string]string{"CORE_API_KEY": "k", "LOG_LEVEL": "chatty"}, "LOG_LEVEL"},
		{"bad duration", map[string]string{"CORE_API_KEY": "k", "HTTP_TIMEOUT": "ten seconds"}, "HTTP_TIMEOUT"},
		{"non-positive duration", map[string]string{"CORE_API_KEY": "k", "HTTP_TIMEOUT": "0s"}, "HTTP_TIMEOUT"},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			for key, value := range tt.env {
				t.Setenv(key, value)
			}
			// t.Setenv cannot unset, so make sure the required key is absent when the
			// test is specifically about it being missing.
			if _, set := tt.env["CORE_API_KEY"]; !set {
				t.Setenv("CORE_API_KEY", "")
			}

			_, err := config.Load()
			if err == nil {
				t.Fatal("Load returned nil error")
			}
			if !strings.Contains(err.Error(), tt.wantVar) {
				t.Errorf("error %q does not name %s", err, tt.wantVar)
			}
		})
	}
}

// A misconfigured deployment should learn about all of its mistakes in one startup log.
func TestLoadReportsEveryProblemAtOnce(t *testing.T) {
	t.Setenv("CORE_API_KEY", "")
	t.Setenv("CORE_API_URL", "not-a-url")
	t.Setenv("HTTP_TIMEOUT", "nonsense")

	_, err := config.Load()
	if err == nil {
		t.Fatal("Load returned nil error")
	}

	for _, want := range []string{"CORE_API_KEY", "CORE_API_URL", "HTTP_TIMEOUT"} {
		if !strings.Contains(err.Error(), want) {
			t.Errorf("error %q does not mention %s", err, want)
		}
	}
}
