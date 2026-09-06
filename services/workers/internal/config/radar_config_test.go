package config_test

import (
	"slices"
	"strings"
	"testing"
	"time"

	"github.com/olmits/social-tool/services/workers/internal/config"
)

func TestLoadRadarDefaults(t *testing.T) {
	t.Setenv("CORE_API_KEY", "k")

	cfg, err := config.Load()
	if err != nil {
		t.Fatalf("Load: %v", err)
	}

	// The radar's interval is deliberately far longer than the publisher's: trending
	// listings turn over across hours, not seconds.
	if cfg.Radar.Interval != time.Hour {
		t.Errorf("Radar.Interval = %v, want 1h", cfg.Radar.Interval)
	}
	if cfg.Radar.Limit != 50 {
		t.Errorf("Radar.Limit = %d, want 50", cfg.Radar.Limit)
	}
	if cfg.Radar.GitHubToken != "" {
		t.Errorf("Radar.GitHubToken = %q, want empty by default", cfg.Radar.GitHubToken)
	}

	// An unset RADAR_SOURCES means every implemented source, not none.
	want := []string{"HACKER_NEWS", "DEVTO", "GITHUB_TRENDING"}
	if !slices.Equal(cfg.Radar.Sources, want) {
		t.Errorf("Radar.Sources = %v, want %v", cfg.Radar.Sources, want)
	}
}

func TestLoadRadarOverrides(t *testing.T) {
	t.Setenv("CORE_API_KEY", "k")
	t.Setenv("RADAR_INTERVAL", "15m")
	t.Setenv("RADAR_LIMIT", "10")
	t.Setenv("RADAR_SOURCES", "DEVTO,HACKER_NEWS")
	t.Setenv("GITHUB_TOKEN", "ghp_secret")

	cfg, err := config.Load()
	if err != nil {
		t.Fatalf("Load: %v", err)
	}

	if cfg.Radar.Interval != 15*time.Minute {
		t.Errorf("Radar.Interval = %v, want 15m", cfg.Radar.Interval)
	}
	if cfg.Radar.Limit != 10 {
		t.Errorf("Radar.Limit = %d, want 10", cfg.Radar.Limit)
	}
	if cfg.Radar.GitHubToken != "ghp_secret" {
		t.Errorf("Radar.GitHubToken = %q", cfg.Radar.GitHubToken)
	}
	// Order follows the configuration, not the canonical list.
	if !slices.Equal(cfg.Radar.Sources, []string{"DEVTO", "HACKER_NEWS"}) {
		t.Errorf("Radar.Sources = %v, want the configured subset in order", cfg.Radar.Sources)
	}
}

func TestLoadRadarSourcesNormalizesInput(t *testing.T) {
	t.Setenv("CORE_API_KEY", "k")
	t.Setenv("RADAR_SOURCES", " devto , HACKER_NEWS ,devto, ")

	cfg, err := config.Load()
	if err != nil {
		t.Fatalf("Load: %v", err)
	}

	// Case-insensitive, whitespace-tolerant, duplicates collapsed, empty entries dropped.
	if !slices.Equal(cfg.Radar.Sources, []string{"DEVTO", "HACKER_NEWS"}) {
		t.Errorf("Radar.Sources = %v, want [DEVTO HACKER_NEWS]", cfg.Radar.Sources)
	}
}

func TestLoadRejectsBadRadarValues(t *testing.T) {
	cases := []struct {
		name    string
		env     map[string]string
		wantErr string
	}{
		{
			name:    "unknown source",
			env:     map[string]string{"RADAR_SOURCES": "HACKER_NEWS,TWITTER"},
			wantErr: `unknown source "TWITTER"`,
		},
		{
			// Reddit is a valid SignalSource in the core API but has no poller yet, so
			// naming it here is a configuration mistake rather than a working selection.
			name:    "source without a poller",
			env:     map[string]string{"RADAR_SOURCES": "REDDIT"},
			wantErr: `unknown source "REDDIT"`,
		},
		{
			name:    "empty source list",
			env:     map[string]string{"RADAR_SOURCES": " , "},
			wantErr: "must name at least one source",
		},
		{
			name:    "non-numeric limit",
			env:     map[string]string{"RADAR_LIMIT": "lots"},
			wantErr: "RADAR_LIMIT is not a valid integer",
		},
		{
			name:    "zero limit",
			env:     map[string]string{"RADAR_LIMIT": "0"},
			wantErr: "RADAR_LIMIT must be positive",
		},
		{
			name:    "negative limit",
			env:     map[string]string{"RADAR_LIMIT": "-5"},
			wantErr: "RADAR_LIMIT must be positive",
		},
		{
			name:    "bad interval",
			env:     map[string]string{"RADAR_INTERVAL": "hourly"},
			wantErr: "RADAR_INTERVAL is not a valid duration",
		},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			t.Setenv("CORE_API_KEY", "k")
			for key, value := range tc.env {
				t.Setenv(key, value)
			}

			_, err := config.Load()
			if err == nil {
				t.Fatal("expected Load to fail")
			}
			if !strings.Contains(err.Error(), tc.wantErr) {
				t.Errorf("error %q should contain %q", err, tc.wantErr)
			}
		})
	}
}
