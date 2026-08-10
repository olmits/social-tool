package coreapi

import (
	"context"
	"net/http"
	"net/url"
	"time"
)

// AccountStatus mirrors com.omits.social_api.account.model.AccountStatus.
type AccountStatus string

const (
	AccountActive       AccountStatus = "ACTIVE"
	AccountDisconnected AccountStatus = "DISCONNECTED"
)

// Account mirrors the core API's AccountResponse.
type Account struct {
	ID       string   `json:"id"`
	Platform Platform `json:"platform"`
	// Handle is the bare handle with no leading "@" — for Bluesky it is also the
	// identifier used to open a session.
	Handle string `json:"handle"`
	// Instance is the server host for federated platforms; set for MASTODON, nil otherwise.
	Instance *string       `json:"instance"`
	Status   AccountStatus `json:"status"`
	// CredentialRef points at the account's entry in the credential store. It is a
	// reference, not the secret — resolve it through a creds.Store.
	CredentialRef string    `json:"credentialRef"`
	CreatedAt     time.Time `json:"createdAt"`
	UpdatedAt     time.Time `json:"updatedAt"`
}

// GetAccount returns a single account by id.
func (c *Client) GetAccount(ctx context.Context, id string) (Account, error) {
	var account Account
	if err := c.do(ctx, http.MethodGet, "/accounts/"+url.PathEscape(id), nil, &account); err != nil {
		return Account{}, err
	}
	return account, nil
}
