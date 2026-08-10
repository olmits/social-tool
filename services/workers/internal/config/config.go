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
	envCoreAPIURL     = "CORE_API_URL"
	envCoreAPIKey     = "CORE_API_KEY"
	envLogLevel       = "LOG_LEVEL"
	envHTTPTimeout    = "HTTP_TIMEOUT"
	envPollInterval   = "POLL_INTERVAL"
	envBlueskyBaseURL = "BLUESKY_BASE_URL"
	envCredsBackend   = "CREDENTIALS_BACKEND"
	envCredsLocalDir  = "LOCAL_CREDENTIALS_DIR"
	envAWSRegion      = "AWS_REGION"

	defaultCoreAPIURL     = "http://localhost:8080"
	defaultLogLevel       = "info"
	defaultHTTPTimeout    = 10 * time.Second
	defaultPollInterval   = 30 * time.Second
	defaultBlueskyBaseURL = "https://bsky.social"
	// defaultCredsLocalDir is where the API's LocalCredentialStore writes when it runs
	// from services/api via ./mvnw spring-boot:run.
	defaultCredsLocalDir = "../api/.local-secrets"
)

// Credential store backends.
const (
	BackendLocal          = "local"
	BackendSecretsManager = "secretsmanager"
)

// Config is the fully validated configuration for a worker process.
type Config struct {
	// CoreAPIURL is the base URL of the Java core API, without a trailing slash.
	CoreAPIURL string
	// CoreAPIKey is the shared secret sent as the X-API-Key header on every request.
	CoreAPIKey string
	// LogLevel is the minimum level emitted by the process logger.
	LogLevel slog.Level
	// HTTPTimeout bounds a single outbound HTTP call.
	HTTPTimeout time.Duration
	// PollInterval is how often the publisher checks for due drafts.
	PollInterval time.Duration
	// BlueskyBaseURL is the AT Protocol PDS host.
	BlueskyBaseURL string
	// Credentials selects and configures the credential store.
	Credentials CredentialsConfig
}

// CredentialsConfig picks where account credentials are resolved from.
type CredentialsConfig struct {
	// Backend is BackendLocal or BackendSecretsManager.
	Backend string
	// LocalDir is the credential directory used by BackendLocal. It must match the API's
	// social-api.local.credentials-dir, since the API writes the files this reads.
	LocalDir string
	// AWSRegion is optional; empty means the AWS SDK's own resolution applies.
	AWSRegion string
}

// Load reads the configuration from the environment. It reports every problem it finds
// at once rather than failing on the first one, so a misconfigured deployment surfaces
// its full set of mistakes in a single startup log line.
func Load() (Config, error) {
	var problems []error

	cfg := Config{
		CoreAPIURL:     strings.TrimRight(envOrDefault(envCoreAPIURL, defaultCoreAPIURL), "/"),
		CoreAPIKey:     os.Getenv(envCoreAPIKey),
		HTTPTimeout:    defaultHTTPTimeout,
		PollInterval:   defaultPollInterval,
		BlueskyBaseURL: strings.TrimRight(envOrDefault(envBlueskyBaseURL, defaultBlueskyBaseURL), "/"),
		Credentials: CredentialsConfig{
			Backend:   envOrDefault(envCredsBackend, BackendLocal),
			LocalDir:  envOrDefault(envCredsLocalDir, defaultCredsLocalDir),
			AWSRegion: os.Getenv(envAWSRegion),
		},
	}

	problems = append(problems,
		validateURL(envCoreAPIURL, cfg.CoreAPIURL),
		validateURL(envBlueskyBaseURL, cfg.BlueskyBaseURL),
	)

	if cfg.CoreAPIKey == "" {
		problems = append(problems, fmt.Errorf("%s is required", envCoreAPIKey))
	}

	// slog.Level.UnmarshalText accepts DEBUG/INFO/WARN/ERROR case-insensitively,
	// plus offsets such as "WARN+2".
	if err := cfg.LogLevel.UnmarshalText([]byte(envOrDefault(envLogLevel, defaultLogLevel))); err != nil {
		problems = append(problems, fmt.Errorf("%s is not a valid level: %w", envLogLevel, err))
	}

	problems = append(problems,
		durationFromEnv(envHTTPTimeout, &cfg.HTTPTimeout),
		durationFromEnv(envPollInterval, &cfg.PollInterval),
	)

	switch cfg.Credentials.Backend {
	case BackendLocal:
		if cfg.Credentials.LocalDir == "" {
			problems = append(problems, fmt.Errorf("%s is required when %s is %s",
				envCredsLocalDir, envCredsBackend, BackendLocal))
		}
	case BackendSecretsManager:
	default:
		problems = append(problems, fmt.Errorf("%s must be %s or %s (got %q)",
			envCredsBackend, BackendLocal, BackendSecretsManager, cfg.Credentials.Backend))
	}

	if err := errors.Join(problems...); err != nil {
		return Config{}, err
	}
	return cfg, nil
}

func validateURL(key, value string) error {
	parsed, err := url.Parse(value)
	if err != nil {
		return fmt.Errorf("%s is not a valid URL: %w", key, err)
	}
	if parsed.Scheme == "" || parsed.Host == "" {
		return fmt.Errorf("%s must be an absolute URL (got %q)", key, value)
	}
	return nil
}

// durationFromEnv overwrites target when key is set to a valid positive duration.
func durationFromEnv(key string, target *time.Duration) error {
	raw, set := os.LookupEnv(key)
	if !set || raw == "" {
		return nil
	}

	value, err := time.ParseDuration(raw)
	switch {
	case err != nil:
		return fmt.Errorf("%s is not a valid duration: %w", key, err)
	case value <= 0:
		return fmt.Errorf("%s must be positive (got %s)", key, value)
	default:
		*target = value
		return nil
	}
}

func envOrDefault(key, fallback string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return fallback
}
