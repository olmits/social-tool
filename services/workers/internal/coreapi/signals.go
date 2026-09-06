package coreapi

import (
	"context"
	"encoding/json"
	"net/http"
	"net/url"
	"time"
)

// MaxIngestBatch mirrors SignalService.MAX_BATCH_SIZE in the core API. Callers with more
// signals than this must split them; IngestSignals does not chunk on their behalf, so that
// a caller stays in control of what it retries after a partial failure.
const MaxIngestBatch = 2000

// SignalSource mirrors com.omits.social_api.signal.model.SignalSource.
type SignalSource string

const (
	SourceHackerNews     SignalSource = "HACKER_NEWS"
	SourceDevto          SignalSource = "DEVTO"
	SourceGitHubTrending SignalSource = "GITHUB_TRENDING"
	SourceReddit         SignalSource = "REDDIT"
	SourceProductHunt    SignalSource = "PRODUCT_HUNT"
)

// Signal is one normalized item as the core API's ingest endpoint accepts it.
type Signal struct {
	Source     SignalSource    `json:"source"`
	ExternalID string          `json:"externalId"`
	Topic      string          `json:"topic"`
	URL        string          `json:"url"`
	Score      int             `json:"score"`
	RawPayload json.RawMessage `json:"rawPayload"`
	FetchedAt  time.Time       `json:"fetchedAt"`
}

// StoredSignal mirrors the core API's SignalResponse.
type StoredSignal struct {
	ID         string          `json:"id"`
	Source     SignalSource    `json:"source"`
	ExternalID string          `json:"externalId"`
	Topic      string          `json:"topic"`
	URL        string          `json:"url"`
	Score      int             `json:"score"`
	RawPayload json.RawMessage `json:"rawPayload"`
	FetchedAt  time.Time       `json:"fetchedAt"`
	CreatedAt  time.Time       `json:"createdAt"`
}

type ingestSignalsCommand struct {
	Signals []Signal `json:"signals"`
}

// IngestResult mirrors the core API's IngestSignalsResponse.
type IngestResult struct {
	Received int `json:"received"`
	Created  int `json:"created"`
	Updated  int `json:"updated"`
}

// IngestSignals stores a batch of signals.
//
// Unlike the draft callbacks, this is idempotent: the core API upserts on
// (source, externalId), so re-posting a batch after an ambiguous failure refreshes the
// stored rows rather than duplicating them. A retry is always safe.
func (c *Client) IngestSignals(ctx context.Context, signals []Signal) (IngestResult, error) {
	var result IngestResult
	body := ingestSignalsCommand{Signals: signals}
	if err := c.do(ctx, http.MethodPost, "/signals", body, &result); err != nil {
		return IngestResult{}, err
	}
	return result, nil
}

// ListSignals returns stored signals ranked by score, optionally limited to one source.
func (c *Client) ListSignals(ctx context.Context, sig SignalSource) ([]StoredSignal, error) {
	path := "/signals"
	if sig != "" {
		query := url.Values{}
		query.Set("source", string(sig))
		path += "?" + query.Encode()
	}

	var signals []StoredSignal
	if err := c.do(ctx, http.MethodGet, path, nil, &signals); err != nil {
		return nil, err
	}
	return signals, nil
}
