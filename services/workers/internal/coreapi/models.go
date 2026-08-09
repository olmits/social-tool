package coreapi

import "time"

// Platform mirrors com.omits.social_api.account.model.Platform. The core API serializes
// enums as their uppercase Java constant names and deserializes them case-sensitively.
type Platform string

const (
	PlatformBluesky  Platform = "BLUESKY"
	PlatformMastodon Platform = "MASTODON"
	PlatformReddit   Platform = "REDDIT"
)

// DraftStatus mirrors com.omits.social_api.draft.model.DraftStatus — the state machine
// driving the pipeline. Only DraftService writes this field; workers observe it and
// report terminal outcomes through MarkPublished / MarkFailed.
type DraftStatus string

const (
	StatusDraft     DraftStatus = "DRAFT"
	StatusApproved  DraftStatus = "APPROVED"
	StatusScheduled DraftStatus = "SCHEDULED"
	StatusPublished DraftStatus = "PUBLISHED"
	StatusFailed    DraftStatus = "FAILED"
	StatusDiscarded DraftStatus = "DISCARDED"
)

// Draft mirrors the core API's DraftResponse. Nullable columns are pointers so that a
// JSON null round-trips as nil rather than collapsing into a zero value.
type Draft struct {
	ID                 string      `json:"id"`
	AccountID          string      `json:"accountId"`
	SignalID           *string     `json:"signalId"`
	Platform           Platform    `json:"platform"`
	Content            string      `json:"content"`
	AffiliateLinks     *string     `json:"affiliateLinks"`
	Status             DraftStatus `json:"status"`
	AIGenerated        bool        `json:"aiGenerated"`
	DisclosureIncluded bool        `json:"disclosureIncluded"`
	ScheduledAt        *time.Time  `json:"scheduledAt"`
	RemoteID           *string     `json:"remoteId"`
	FailureReason      *string     `json:"failureReason"`
	CreatedAt          time.Time   `json:"createdAt"`
	UpdatedAt          time.Time   `json:"updatedAt"`
}

// Health is the core API's GET /health payload. Unlike every other endpoint, /health is
// unauthenticated.
type Health struct {
	Status string            `json:"status"`
	Checks map[string]string `json:"checks"`
}
