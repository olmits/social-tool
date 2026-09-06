// Package config loads and validates the workers' runtime configuration from the
// environment. Every worker shares the same configuration surface.
package config

import (
	"errors"
	"fmt"
	"log/slog"
	"net/url"
	"os"
	"slices"
	"strconv"
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
	envRadarInterval  = "RADAR_INTERVAL"
	envRadarSources   = "RADAR_SOURCES"
	envRadarLimit     = "RADAR_LIMIT"
	envGitHubToken    = "GITHUB_TOKEN"

	defaultCoreAPIURL     = "http://localhost:8080"
	defaultLogLevel       = "info"
	defaultHTTPTimeout    = 10 * time.Second
	defaultPollInterval   = 30 * time.Second
	defaultBlueskyBaseURL = "https://bsky.social"
	// defaultCredsLocalDir is where the API's LocalCredentialStore writes when it runs
	// from services/api via ./mvnw spring-boot:run.
	defaultCredsLocalDir = "../api/.local-secrets"
	// defaultRadarInterval is deliberately much longer than the publisher's: front pages
	// and trending listings turn over across hours, and polling them by the minute spends
	// third-party rate limit for near-identical results.
	defaultRadarInterval = time.Hour
	// defaultRadarLimit is how many items to take from each source per run.
	defaultRadarLimit = 50
)

// Sources the radar knows how to poll. These match the SignalSource enum in the core API.
// Reddit and Product Hunt are absent on purpose: both need API credentials, and Reddit
// additionally needs the app approval described in PLAN.md.
const (
	SourceHackerNews     = "HACKER_NEWS"
	SourceDevto          = "DEVTO"
	SourceGitHubTrending = "GITHUB_TRENDING"
)

// knownSources is the set RADAR_SOURCES is validated against.
var knownSources = []string{SourceHackerNews, SourceDevto, SourceGitHubTrending}

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
	// Radar configures the trend-radar poller.
	Radar RadarConfig
}

// RadarConfig selects which content sources the trend radar polls and how hard.
type RadarConfig struct {
	// Interval is how often a full poll of every source runs.
	Interval time.Duration
	// Sources lists the sources to poll, by their SignalSource name. Never empty after
	// a successful Load.
	Sources []string
	// Limit is how many items to take from each source per run.
	Limit int
	// GitHubToken is optional. Unauthenticated GitHub search allows 10 requests per
	// minute, which one run per interval stays well inside; a token raises that to 30.
	GitHubToken string
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
		Radar: RadarConfig{
			Interval:    defaultRadarInterval,
			Limit:       defaultRadarLimit,
			GitHubToken: os.Getenv(envGitHubToken),
		},
	}

	sources, err := radarSources(os.Getenv(envRadarSources))
	if err != nil {
		problems = append(problems, err)
	}
	cfg.Radar.Sources = sources

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
		durationFromEnv(envRadarInterval, &cfg.Radar.Interval),
		intFromEnv(envRadarLimit, &cfg.Radar.Limit),
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

// radarSources parses the comma-separated RADAR_SOURCES list. An empty value selects every
// known source, which is the useful default: the radar is only worth running across all of
// them, and narrowing the list is the exception (disabling one that is rate-limiting or
// down). Names are matched case-insensitively and duplicates collapse.
func radarSources(raw string) ([]string, error) {
	if strings.TrimSpace(raw) == "" {
		return slices.Clone(knownSources), nil
	}

	var selected []string
	for _, name := range strings.Split(raw, ",") {
		name = strings.ToUpper(strings.TrimSpace(name))
		if name == "" {
			continue
		}
		if !slices.Contains(knownSources, name) {
			return nil, fmt.Errorf("%s contains unknown source %q (known: %s)",
				envRadarSources, name, strings.Join(knownSources, ", "))
		}
		if !slices.Contains(selected, name) {
			selected = append(selected, name)
		}
	}
	if len(selected) == 0 {
		return nil, fmt.Errorf("%s must name at least one source", envRadarSources)
	}
	return selected, nil
}

// intFromEnv overwrites target when key is set to a valid positive integer.
func intFromEnv(key string, target *int) error {
	raw, set := os.LookupEnv(key)
	if !set || raw == "" {
		return nil
	}

	value, err := strconv.Atoi(raw)
	switch {
	case err != nil:
		return fmt.Errorf("%s is not a valid integer: %w", key, err)
	case value <= 0:
		return fmt.Errorf("%s must be positive (got %d)", key, value)
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
