package publisher_test

import (
	"context"
	"errors"
	"fmt"
	"strings"
	"testing"
	"time"

	"github.com/olmits/social-tool/services/workers/internal/adapter"
	"github.com/olmits/social-tool/services/workers/internal/coreapi"
	"github.com/olmits/social-tool/services/workers/internal/publisher"
)

var now = time.Date(2026, 8, 9, 12, 0, 0, 0, time.UTC)

const (
	accountID = "91bc4f27-6a3e-4c8d-b1f0-5e2a9d3c7481"
	draftID   = "6f2a1c3d-0b8e-4a17-9c55-2d1f7e4a8b90"
	remoteID  = "at://did:plc:abc123/app.bsky.feed.post/3kxyz"
)

// --- fakes -------------------------------------------------------------------

type fakeAPI struct {
	drafts  []coreapi.Draft
	account coreapi.Account

	listErr    error
	accountErr error
	publishErr error
	failErr    error

	publishCalls []string
	failCalls    []string
	publishTries int
}

func (f *fakeAPI) ListDrafts(context.Context, coreapi.ListDraftsParams) ([]coreapi.Draft, error) {
	return f.drafts, f.listErr
}

func (f *fakeAPI) GetAccount(context.Context, string) (coreapi.Account, error) {
	return f.account, f.accountErr
}

func (f *fakeAPI) MarkPublished(_ context.Context, id, remote string) (coreapi.Draft, error) {
	f.publishTries++
	if f.publishErr != nil {
		return coreapi.Draft{}, f.publishErr
	}
	f.publishCalls = append(f.publishCalls, id+"|"+remote)
	return coreapi.Draft{}, nil
}

func (f *fakeAPI) MarkFailed(_ context.Context, id, reason string) (coreapi.Draft, error) {
	if f.failErr != nil {
		return coreapi.Draft{}, f.failErr
	}
	f.failCalls = append(f.failCalls, id+"|"+reason)
	return coreapi.Draft{}, nil
}

type fakeAdapter struct {
	posted     []string
	postErr    error
	sessionErr error
	gotAccount adapter.Account
}

func (f *fakeAdapter) ForAccount(_ context.Context, account adapter.Account) (adapter.Adapter, error) {
	if f.sessionErr != nil {
		return nil, f.sessionErr
	}
	f.gotAccount = account
	return f, nil
}

func (f *fakeAdapter) Post(_ context.Context, text string) (string, error) {
	if f.postErr != nil {
		return "", f.postErr
	}
	f.posted = append(f.posted, text)
	return remoteID, nil
}

type fakeCreds struct {
	secret string
	err    error
}

func (f fakeCreds) Resolve(context.Context, string) (string, error) { return f.secret, f.err }

// --- helpers -----------------------------------------------------------------

func scheduledDraft(id string, at time.Time) coreapi.Draft {
	return coreapi.Draft{
		ID:          id,
		AccountID:   accountID,
		Platform:    coreapi.PlatformBluesky,
		Content:     "hello from " + id,
		Status:      coreapi.StatusScheduled,
		ScheduledAt: &at,
	}
}

func activeAccount() coreapi.Account {
	return coreapi.Account{
		ID:            accountID,
		Platform:      coreapi.PlatformBluesky,
		Handle:        "me.bsky.social",
		Status:        coreapi.AccountActive,
		CredentialRef: "local/accounts/abc",
	}
}

func newPublisher(api *fakeAPI, bsky adapter.Factory, store fakeCreds) *publisher.Publisher {
	return publisher.New(publisher.Options{
		API:         api,
		Credentials: store,
		Adapters:    map[coreapi.Platform]adapter.Factory{coreapi.PlatformBluesky: bsky},
		Now:         func() time.Time { return now },
	})
}

// --- due selection -----------------------------------------------------------

func TestDueDrafts(t *testing.T) {
	past := scheduledDraft("past", now.Add(-time.Hour))
	justDue := scheduledDraft("just-due", now)
	future := scheduledDraft("future", now.Add(time.Hour))

	noTime := scheduledDraft("no-time", now)
	noTime.ScheduledAt = nil

	approved := scheduledDraft("approved", now.Add(-time.Hour))
	approved.Status = coreapi.StatusApproved

	got := publisher.DueDrafts([]coreapi.Draft{future, noTime, approved, justDue, past}, now)

	// Only the two due ones, oldest first.
	want := []string{"past", "just-due"}
	if len(got) != len(want) {
		t.Fatalf("got %d due drafts (%v), want %v", len(got), ids(got), want)
	}
	for i, id := range want {
		if got[i].ID != id {
			t.Errorf("due[%d] = %s, want %s", i, got[i].ID, id)
		}
	}
}

func ids(drafts []coreapi.Draft) []string {
	out := make([]string, len(drafts))
	for i, d := range drafts {
		out[i] = d.ID
	}
	return out
}

// --- the happy path ----------------------------------------------------------

func TestRunOncePublishesDueDraftAndRecordsIt(t *testing.T) {
	api := &fakeAPI{
		drafts:  []coreapi.Draft{scheduledDraft(draftID, now.Add(-time.Minute))},
		account: activeAccount(),
	}
	bsky := &fakeAdapter{}

	if err := newPublisher(api, bsky, fakeCreds{secret: "app-password"}).RunOnce(t.Context()); err != nil {
		t.Fatalf("RunOnce: %v", err)
	}

	if len(bsky.posted) != 1 || bsky.posted[0] != "hello from "+draftID {
		t.Errorf("posted = %v, want the draft content once", bsky.posted)
	}
	// The resolved secret and handle must reach the adapter.
	if bsky.gotAccount.Handle != "me.bsky.social" || bsky.gotAccount.Secret != "app-password" {
		t.Errorf("adapter got %+v, want the account handle and resolved secret", bsky.gotAccount)
	}
	if len(api.publishCalls) != 1 || api.publishCalls[0] != draftID+"|"+remoteID {
		t.Errorf("markPublished calls = %v, want one with the remote id", api.publishCalls)
	}
	if len(api.failCalls) != 0 {
		t.Errorf("markFailed was called: %v", api.failCalls)
	}
}

func TestRunOnceSkipsDraftsThatAreNotDue(t *testing.T) {
	api := &fakeAPI{
		drafts:  []coreapi.Draft{scheduledDraft(draftID, now.Add(time.Hour))},
		account: activeAccount(),
	}
	bsky := &fakeAdapter{}

	if err := newPublisher(api, bsky, fakeCreds{secret: "s"}).RunOnce(t.Context()); err != nil {
		t.Fatalf("RunOnce: %v", err)
	}

	if len(bsky.posted) != 0 {
		t.Errorf("posted %v, want nothing", bsky.posted)
	}
	if len(api.publishCalls)+len(api.failCalls) != 0 {
		t.Error("a not-yet-due draft triggered a callback")
	}
}

// --- failure handling --------------------------------------------------------

// A platform rejection is a real publish failure and must be recorded as FAILED.
func TestPlatformRejectionMarksDraftFailed(t *testing.T) {
	api := &fakeAPI{
		drafts:  []coreapi.Draft{scheduledDraft(draftID, now.Add(-time.Minute))},
		account: activeAccount(),
	}
	bsky := &fakeAdapter{postErr: errors.New("post too long")}

	if err := newPublisher(api, bsky, fakeCreds{secret: "s"}).RunOnce(t.Context()); err != nil {
		t.Fatalf("RunOnce: %v", err)
	}

	if len(api.failCalls) != 1 {
		t.Fatalf("markFailed calls = %v, want one", api.failCalls)
	}
	if !strings.Contains(api.failCalls[0], "post too long") {
		t.Errorf("failure reason %q does not carry the platform error", api.failCalls[0])
	}
	if len(api.publishCalls) != 0 {
		t.Error("a failed publish was recorded as published")
	}
}

// Everything before the post is environmental. Burning the draft would force the user to
// recreate it, so it must stay SCHEDULED for the next pass.
func TestPreflightProblemsLeaveTheDraftScheduled(t *testing.T) {
	disconnected := activeAccount()
	disconnected.Status = coreapi.AccountDisconnected

	wrongPlatform := activeAccount()
	wrongPlatform.Platform = coreapi.PlatformMastodon

	tests := []struct {
		name    string
		api     *fakeAPI
		creds   fakeCreds
		adapter *fakeAdapter
	}{
		{
			name:    "account lookup fails",
			api:     &fakeAPI{accountErr: errors.New("api down")},
			creds:   fakeCreds{secret: "s"},
			adapter: &fakeAdapter{},
		},
		{
			name:    "account is disconnected",
			api:     &fakeAPI{account: disconnected},
			creds:   fakeCreds{secret: "s"},
			adapter: &fakeAdapter{},
		},
		{
			name:    "account platform does not match the draft",
			api:     &fakeAPI{account: wrongPlatform},
			creds:   fakeCreds{secret: "s"},
			adapter: &fakeAdapter{},
		},
		{
			name:    "credential cannot be resolved",
			api:     &fakeAPI{account: activeAccount()},
			creds:   fakeCreds{err: errors.New("no such secret")},
			adapter: &fakeAdapter{},
		},
		{
			name:    "platform rejects the session",
			api:     &fakeAPI{account: activeAccount()},
			creds:   fakeCreds{secret: "s"},
			adapter: &fakeAdapter{sessionErr: errors.New("bad password")},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			tt.api.drafts = []coreapi.Draft{scheduledDraft(draftID, now.Add(-time.Minute))}

			if err := newPublisher(tt.api, tt.adapter, tt.creds).RunOnce(t.Context()); err != nil {
				t.Fatalf("RunOnce: %v", err)
			}

			if len(tt.adapter.posted) != 0 {
				t.Errorf("posted %v, want nothing", tt.adapter.posted)
			}
			if len(tt.api.failCalls) != 0 {
				t.Errorf("draft was marked FAILED for an environmental problem: %v", tt.api.failCalls)
			}
			if len(tt.api.publishCalls) != 0 {
				t.Errorf("draft was marked PUBLISHED: %v", tt.api.publishCalls)
			}
		})
	}
}

func TestUnknownPlatformDoesNotBurnTheDraft(t *testing.T) {
	draft := scheduledDraft(draftID, now.Add(-time.Minute))
	draft.Platform = coreapi.PlatformReddit // no adapter registered

	api := &fakeAPI{drafts: []coreapi.Draft{draft}, account: activeAccount()}

	if err := newPublisher(api, &fakeAdapter{}, fakeCreds{secret: "s"}).RunOnce(t.Context()); err != nil {
		t.Fatalf("RunOnce: %v", err)
	}
	if len(api.failCalls)+len(api.publishCalls) != 0 {
		t.Error("an unregistered platform produced a state transition")
	}
}

// One bad draft must not stop the ones behind it.
func TestOneFailingDraftDoesNotAbortThePass(t *testing.T) {
	api := &fakeAPI{
		drafts: []coreapi.Draft{
			scheduledDraft("first", now.Add(-2*time.Hour)),
			scheduledDraft("second", now.Add(-time.Hour)),
		},
		account: activeAccount(),
	}
	// Fail the first post, succeed on the second.
	bsky := &sequenceAdapter{errs: []error{errors.New("boom"), nil}}

	if err := newPublisher(api, bsky, fakeCreds{secret: "s"}).RunOnce(t.Context()); err != nil {
		t.Fatalf("RunOnce: %v", err)
	}

	if len(api.failCalls) != 1 || !strings.HasPrefix(api.failCalls[0], "first|") {
		t.Errorf("markFailed calls = %v, want one for the first draft", api.failCalls)
	}
	if len(api.publishCalls) != 1 || !strings.HasPrefix(api.publishCalls[0], "second|") {
		t.Errorf("markPublished calls = %v, want one for the second draft", api.publishCalls)
	}
}

type sequenceAdapter struct {
	errs []error
	call int
}

func (s *sequenceAdapter) ForAccount(context.Context, adapter.Account) (adapter.Adapter, error) {
	return s, nil
}

func (s *sequenceAdapter) Post(context.Context, string) (string, error) {
	err := s.errs[s.call]
	s.call++
	if err != nil {
		return "", err
	}
	return remoteID, nil
}

func TestRunOnceReturnsErrorWhenTheApiIsUnreachable(t *testing.T) {
	api := &fakeAPI{listErr: errors.New("connection refused")}

	err := newPublisher(api, &fakeAdapter{}, fakeCreds{secret: "s"}).RunOnce(t.Context())
	if err == nil {
		t.Fatal("RunOnce returned nil error when listing failed")
	}
}

// --- the published-callback path --------------------------------------------

// A retried callback hits 409 because SCHEDULED is the only state markPublished accepts.
// The post is already live, so this is not a failure.
func TestConflictOnMarkPublishedIsTolerated(t *testing.T) {
	api := &fakeAPI{
		drafts:     []coreapi.Draft{scheduledDraft(draftID, now.Add(-time.Minute))},
		account:    activeAccount(),
		publishErr: &coreapi.APIError{StatusCode: 409, Message: "Cannot transition draft from PUBLISHED to PUBLISHED"},
	}

	if err := newPublisher(api, &fakeAdapter{}, fakeCreds{secret: "s"}).RunOnce(t.Context()); err != nil {
		t.Fatalf("RunOnce: %v", err)
	}

	// Tolerated, so no retries and no failure recorded.
	if api.publishTries != 1 {
		t.Errorf("markPublished attempted %d times, want 1", api.publishTries)
	}
	if len(api.failCalls) != 0 {
		t.Errorf("a 409 was recorded as a failure: %v", api.failCalls)
	}
}

func TestMarkPublishedRetriesTransientErrors(t *testing.T) {
	api := &fakeAPI{
		drafts:     []coreapi.Draft{scheduledDraft(draftID, now.Add(-time.Minute))},
		account:    activeAccount(),
		publishErr: &coreapi.APIError{StatusCode: 503},
	}

	if err := newPublisher(api, &fakeAdapter{}, fakeCreds{secret: "s"}).RunOnce(t.Context()); err != nil {
		t.Fatalf("RunOnce: %v", err)
	}

	if api.publishTries != 3 {
		t.Errorf("markPublished attempted %d times, want 3", api.publishTries)
	}
}

// A 404 will never succeed, so retrying only delays the loop.
func TestMarkPublishedDoesNotRetryPermanentErrors(t *testing.T) {
	api := &fakeAPI{
		drafts:     []coreapi.Draft{scheduledDraft(draftID, now.Add(-time.Minute))},
		account:    activeAccount(),
		publishErr: &coreapi.APIError{StatusCode: 404, Message: "No draft found"},
	}

	if err := newPublisher(api, &fakeAdapter{}, fakeCreds{secret: "s"}).RunOnce(t.Context()); err != nil {
		t.Fatalf("RunOnce: %v", err)
	}

	if api.publishTries != 1 {
		t.Errorf("markPublished attempted %d times, want 1", api.publishTries)
	}
}

// --- the loop ----------------------------------------------------------------

func TestRunPollsImmediatelyThenStopsOnCancel(t *testing.T) {
	api := &fakeAPI{
		drafts:  []coreapi.Draft{scheduledDraft(draftID, now.Add(-time.Minute))},
		account: activeAccount(),
	}
	bsky := &fakeAdapter{}
	pub := newPublisher(api, bsky, fakeCreds{secret: "s"})

	ctx, cancel := context.WithCancel(t.Context())
	done := make(chan error, 1)
	// A long interval proves the first pass does not wait for a tick.
	go func() { done <- pub.Run(ctx, time.Hour) }()

	deadline := time.After(2 * time.Second)
	for len(bsky.posted) == 0 {
		select {
		case <-deadline:
			cancel()
			t.Fatal("Run did not publish before the first tick")
		default:
			time.Sleep(time.Millisecond)
		}
	}

	cancel()
	select {
	case err := <-done:
		if !errors.Is(err, context.Canceled) {
			t.Errorf("Run returned %v, want context.Canceled", err)
		}
	case <-time.After(2 * time.Second):
		t.Fatal("Run did not return after cancellation")
	}
}

func TestRunKeepsGoingWhenAPassFails(t *testing.T) {
	api := &fakeAPI{listErr: fmt.Errorf("api down")}
	pub := newPublisher(api, &fakeAdapter{}, fakeCreds{secret: "s"})

	ctx, cancel := context.WithTimeout(t.Context(), 150*time.Millisecond)
	defer cancel()

	// The first pass fails; Run must keep looping rather than returning the error.
	err := pub.Run(ctx, 20*time.Millisecond)
	if !errors.Is(err, context.DeadlineExceeded) {
		t.Errorf("Run returned %v, want DeadlineExceeded", err)
	}
}
