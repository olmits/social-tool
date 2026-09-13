package bluesky_test

import (
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/olmits/social-tool/services/workers/internal/adapter"
	"github.com/olmits/social-tool/services/workers/internal/adapter/bluesky"
)

const (
	testHandle = "me.bsky.social"
	testSecret = "abcd-efgh-ijkl-mnop"
	testDID    = "did:plc:abc123"
	testJWT    = "header.payload.signature"
	testURI    = "at://did:plc:abc123/app.bsky.feed.post/3kxyz"
)

// capture records what the stub PDS received on each call.
type capture struct {
	sessionBody map[string]any
	recordBody  map[string]any
	recordAuth  string
	paths       []string
}

// newStubPDS serves the two XRPC endpoints a publish needs.
func newStubPDS(t *testing.T, got *capture) *bluesky.Factory {
	t.Helper()

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		got.paths = append(got.paths, r.URL.Path)
		w.Header().Set("Content-Type", "application/json")

		switch r.URL.Path {
		case "/xrpc/com.atproto.server.createSession":
			if err := json.NewDecoder(r.Body).Decode(&got.sessionBody); err != nil {
				t.Errorf("decode createSession body: %v", err)
			}
			io.WriteString(w, `{"did":"`+testDID+`","accessJwt":"`+testJWT+`","refreshJwt":"r","handle":"`+testHandle+`"}`)

		case "/xrpc/com.atproto.repo.createRecord":
			got.recordAuth = r.Header.Get("Authorization")
			if err := json.NewDecoder(r.Body).Decode(&got.recordBody); err != nil {
				t.Errorf("decode createRecord body: %v", err)
			}
			io.WriteString(w, `{"uri":"`+testURI+`","cid":"bafy123"}`)

		default:
			t.Errorf("unexpected path %s", r.URL.Path)
			w.WriteHeader(http.StatusNotFound)
		}
	}))
	t.Cleanup(server.Close)

	return bluesky.NewFactory(server.URL, 5*time.Second)
}

func TestPostSendsSessionThenRecord(t *testing.T) {
	var got capture
	factory := newStubPDS(t, &got)

	target, err := factory.ForAccount(t.Context(), adapter.Account{Handle: testHandle, Secret: testSecret})
	if err != nil {
		t.Fatalf("ForAccount: %v", err)
	}

	remoteID, err := target.Post(t.Context(), "hello world")
	if err != nil {
		t.Fatalf("Post: %v", err)
	}

	if remoteID != testURI {
		t.Errorf("remoteID = %q, want %q", remoteID, testURI)
	}

	// The session must be opened once, at ForAccount time, not per post.
	wantPaths := []string{"/xrpc/com.atproto.server.createSession", "/xrpc/com.atproto.repo.createRecord"}
	if strings.Join(got.paths, ",") != strings.Join(wantPaths, ",") {
		t.Errorf("paths = %v, want %v", got.paths, wantPaths)
	}

	// createSession takes the handle as "identifier".
	if got.sessionBody["identifier"] != testHandle {
		t.Errorf("identifier = %v, want %q", got.sessionBody["identifier"], testHandle)
	}
	if got.sessionBody["password"] != testSecret {
		t.Errorf("password was not sent verbatim")
	}

	// createRecord is bearer-authenticated with the session token.
	if got.recordAuth != "Bearer "+testJWT {
		t.Errorf("Authorization = %q, want Bearer %s", got.recordAuth, testJWT)
	}
	if got.recordBody["repo"] != testDID {
		t.Errorf("repo = %v, want %q", got.recordBody["repo"], testDID)
	}
	if got.recordBody["collection"] != "app.bsky.feed.post" {
		t.Errorf("collection = %v, want app.bsky.feed.post", got.recordBody["collection"])
	}

	record, ok := got.recordBody["record"].(map[string]any)
	if !ok {
		t.Fatalf("record = %v, want an object", got.recordBody["record"])
	}
	if record["$type"] != "app.bsky.feed.post" {
		t.Errorf("record $type = %v, want app.bsky.feed.post", record["$type"])
	}
	if record["text"] != "hello world" {
		t.Errorf("record text = %v, want hello world", record["text"])
	}
	createdAt, _ := record["createdAt"].(string)
	if _, err := time.Parse(time.RFC3339, createdAt); err != nil {
		t.Errorf("record createdAt %q is not RFC3339: %v", createdAt, err)
	}
	// A top-level post carries no reply reference.
	if _, ok := record["reply"]; ok {
		t.Errorf("record carried a reply field: %v", record)
	}
}

func TestForAccountReportsRejectedCredentials(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusUnauthorized)
		io.WriteString(w, `{"error":"AuthenticationRequired","message":"Invalid identifier or password"}`)
	}))
	t.Cleanup(server.Close)

	_, err := bluesky.NewFactory(server.URL, 5*time.Second).
		ForAccount(t.Context(), adapter.Account{Handle: testHandle, Secret: "wrong"})
	if err == nil {
		t.Fatal("ForAccount returned nil error for a 401")
	}

	var apiErr *bluesky.APIError
	if !errors.As(err, &apiErr) {
		t.Fatalf("error %v is not a *bluesky.APIError", err)
	}
	if apiErr.Kind != "AuthenticationRequired" {
		t.Errorf("Kind = %q, want AuthenticationRequired", apiErr.Kind)
	}
	if apiErr.Retryable() {
		t.Error("a rejected password was reported as retryable")
	}
	if strings.Contains(err.Error(), "wrong") {
		t.Errorf("error leaked the password: %v", err)
	}
}

func TestPostErrorRetryability(t *testing.T) {
	tests := []struct {
		name          string
		status        int
		body          string
		wantRetryable bool
	}{
		{"rate limited", http.StatusTooManyRequests, `{"error":"RateLimitExceeded"}`, true},
		{"server fault", http.StatusBadGateway, `{"error":"InternalServerError"}`, true},
		{"bad request", http.StatusBadRequest, `{"error":"InvalidRequest","message":"post too long"}`, false},
		{"non-JSON body", http.StatusServiceUnavailable, `upstream unavailable`, true},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				if r.URL.Path == "/xrpc/com.atproto.server.createSession" {
					io.WriteString(w, `{"did":"`+testDID+`","accessJwt":"`+testJWT+`"}`)
					return
				}
				w.WriteHeader(tt.status)
				io.WriteString(w, tt.body)
			}))
			t.Cleanup(server.Close)

			target, err := bluesky.NewFactory(server.URL, 5*time.Second).
				ForAccount(t.Context(), adapter.Account{Handle: testHandle, Secret: testSecret})
			if err != nil {
				t.Fatalf("ForAccount: %v", err)
			}

			_, err = target.Post(t.Context(), "hello")
			if err == nil {
				t.Fatalf("Post returned nil error for a %d", tt.status)
			}

			var apiErr *bluesky.APIError
			if !errors.As(err, &apiErr) {
				t.Fatalf("error %v is not a *bluesky.APIError", err)
			}
			if apiErr.Retryable() != tt.wantRetryable {
				t.Errorf("Retryable() = %v, want %v", apiErr.Retryable(), tt.wantRetryable)
			}
		})
	}
}

func TestForAccountRejectsIncompleteSession(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// A 200 with no token would otherwise produce unauthenticated posts.
		io.WriteString(w, `{"handle":"`+testHandle+`"}`)
	}))
	t.Cleanup(server.Close)

	_, err := bluesky.NewFactory(server.URL, 5*time.Second).
		ForAccount(t.Context(), adapter.Account{Handle: testHandle, Secret: testSecret})
	if err == nil {
		t.Fatal("ForAccount accepted a session with no accessJwt")
	}
}
