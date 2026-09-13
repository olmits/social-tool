package coreapi

import (
	"context"
	"net/http"
)

// TopicQueries is one enabled topic and the queries to send for it, mirroring the core
// API's TopicQueriesResponse.
//
// Queries is keyed by the source each query is sent to, and a source absent from the map is
// simply not polled for this topic — which is what makes the map the right shape rather than
// one field per source. The values are opaque to the poller: a dev.to tag, a set of GitHub
// search qualifiers, and Hacker News search terms have nothing in common beyond being
// strings the source knows how to send upstream.
type TopicQueries struct {
	TopicID string                  `json:"topicId"`
	Name    string                  `json:"name"`
	Queries map[SignalSource]string `json:"queries"`
}

// ListTopicQueries returns the poller's whole work list: every enabled topic with its
// per-source queries.
//
// Disabled topics are filtered server-side, so everything returned is safe to ingest against
// — the core API rejects a signal naming a topic that is switched off.
func (c *Client) ListTopicQueries(ctx context.Context) ([]TopicQueries, error) {
	var topics []TopicQueries
	if err := c.do(ctx, http.MethodGet, "/topics/queries", nil, &topics); err != nil {
		return nil, err
	}
	return topics, nil
}
