package coreapi

import (
	"context"
	"net/http"
	"net/url"
)

// ListDraftsParams are the filters GET /drafts accepts. Both are optional; the zero value
// lists every draft.
//
// Note there is deliberately no due-time filter here: the core API exposes only accountId
// and status, so selecting drafts whose ScheduledAt has passed is the caller's job.
type ListDraftsParams struct {
	AccountID string
	Status    DraftStatus
}

// ListDrafts returns the drafts matching params. The response is unpaginated.
func (c *Client) ListDrafts(ctx context.Context, params ListDraftsParams) ([]Draft, error) {
	query := url.Values{}
	if params.AccountID != "" {
		query.Set("accountId", params.AccountID)
	}
	if params.Status != "" {
		query.Set("status", string(params.Status))
	}

	path := "/drafts"
	if len(query) > 0 {
		path += "?" + query.Encode()
	}

	var drafts []Draft
	if err := c.do(ctx, http.MethodGet, path, nil, &drafts); err != nil {
		return nil, err
	}
	return drafts, nil
}

// GetDraft returns a single draft by id, or an error for which IsNotFound reports true.
func (c *Client) GetDraft(ctx context.Context, id string) (Draft, error) {
	var draft Draft
	if err := c.do(ctx, http.MethodGet, "/drafts/"+url.PathEscape(id), nil, &draft); err != nil {
		return Draft{}, err
	}
	return draft, nil
}

type publishDraftCommand struct {
	RemoteID string `json:"remoteId"`
}

// MarkPublished records a successful publish, moving the draft SCHEDULED -> PUBLISHED and
// storing the platform's identifier for the post.
//
// This callback is NOT idempotent. SCHEDULED is the only accepted source state, so calling
// it twice returns 409 the second time. A caller retrying after an ambiguous failure should
// treat IsConflict as "already recorded" rather than as an error — the post did go out.
func (c *Client) MarkPublished(ctx context.Context, id, remoteID string) (Draft, error) {
	var draft Draft
	body := publishDraftCommand{RemoteID: remoteID}
	if err := c.do(ctx, http.MethodPatch, "/drafts/"+url.PathEscape(id)+"/published", body, &draft); err != nil {
		return Draft{}, err
	}
	return draft, nil
}

type failDraftCommand struct {
	Reason *string `json:"reason"`
}

// MarkFailed records a failed publish attempt, moving the draft SCHEDULED -> FAILED.
// Like MarkPublished, SCHEDULED is the only accepted source state.
func (c *Client) MarkFailed(ctx context.Context, id, reason string) (Draft, error) {
	// The core API rejects an absent body outright, and does so with a non-ErrorResponse
	// 400, so an empty reason still has to travel as {"reason":null}.
	body := failDraftCommand{}
	if reason != "" {
		body.Reason = &reason
	}

	var draft Draft
	if err := c.do(ctx, http.MethodPatch, "/drafts/"+url.PathEscape(id)+"/failed", body, &draft); err != nil {
		return Draft{}, err
	}
	return draft, nil
}
