package coreapi

import (
	"context"
	"net/http"
)

// Health checks that the core API is up. It is the one endpoint that does not require an
// API key, so a success here proves reachability but says nothing about authentication.
// A degraded API answers 503, which surfaces as an *APIError.
func (c *Client) Health(ctx context.Context) (Health, error) {
	var health Health
	if err := c.do(ctx, http.MethodGet, "/health", nil, &health); err != nil {
		return Health{}, err
	}
	return health, nil
}
