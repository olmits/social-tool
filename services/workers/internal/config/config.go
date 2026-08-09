// Package config loads and validates the workers' runtime configuration from the
// environment. Every worker shares the same configuration surface.
package config

import (
	"errors"
	"fmt"
	"log/slog"
	"net/url"
	"os"
	"strings"
	"time"
)

const (
	envCoreAPIURL  = "CORE_API_URL"
	envCoreAPIKey  = "CORE_API_KEY"
	envLogLevel    = "LOG_LEVEL"
	envHTTPTimeout = "HTTP_TIMEOUT"

	defaultCoreAPIURL  = "http://localhost:8080"
	defaultLogLevel    = "info"
	defaultHTTPTimeout = 10 * time.Second
)

// Config is the fully validated configuration for a worker process.
type Config struct {
	// CoreAPIURL is the base URL of the Java core API, without a trailing slash.
	CoreAPIURL string
	// CoreAPIKey is the shared secret sent as the X-API-Key header on every request.
	CoreAPIKey string
	// LogLevel is the minimum level emitted by the process logger.
	LogLevel slog.Level
	// HTTPTimeout bounds a single outbound HTTP call to the core API.
	HTTPTimeout time.Duration
}

// Load reads the configuration from the environment. It reports every problem it finds
// at once rather than failing on the first one, so a misconfigured deployment surfaces
// its full set of mistakes in a single startup log line.
func Load() (Config, error) {
	var problems []error

	cfg := Config{
		CoreAPIURL:  envOrDefault(envCoreAPIURL, defaultCoreAPIURL),
		CoreAPIKey:  os.Getenv(envCoreAPIKey),
		HTTPTimeout: defaultHTTPTimeout,
	}

	cfg.CoreAPIURL = strings.TrimRight(cfg.CoreAPIURL, "/")
	if parsed, err := url.Parse(cfg.CoreAPIURL); err != nil {
		problems = append(problems, fmt.Errorf("%s is not a valid URL: %w", envCoreAPIURL, err))
	} else if parsed.Scheme == "" || parsed.Host == "" {
		problems = append(problems, fmt.Errorf("%s must be an absolute URL (got %q)", envCoreAPIURL, cfg.CoreAPIURL))
	}

	if cfg.CoreAPIKey == "" {
		problems = append(problems, fmt.Errorf("%s is required", envCoreAPIKey))
	}

	// slog.Level.UnmarshalText accepts DEBUG/INFO/WARN/ERROR case-insensitively,
	// plus offsets such as "WARN+2".
	if err := cfg.LogLevel.UnmarshalText([]byte(envOrDefault(envLogLevel, defaultLogLevel))); err != nil {
		problems = append(problems, fmt.Errorf("%s is not a valid level: %w", envLogLevel, err))
	}

	if raw, ok := os.LookupEnv(envHTTPTimeout); ok && raw != "" {
		timeout, err := time.ParseDuration(raw)
		switch {
		case err != nil:
			problems = append(problems, fmt.Errorf("%s is not a valid duration: %w", envHTTPTimeout, err))
		case timeout <= 0:
			problems = append(problems, fmt.Errorf("%s must be positive (got %s)", envHTTPTimeout, timeout))
		default:
			cfg.HTTPTimeout = timeout
		}
	}

	if len(problems) > 0 {
		return Config{}, errors.Join(problems...)
	}
	return cfg, nil
}

func envOrDefault(key, fallback string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return fallback
}
