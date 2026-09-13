package coreapi_test

import (
	"net/http"
	"testing"

	"github.com/olmits/social-tool/services/workers/internal/coreapi"
)

func TestListTopicQueriesReadsTheWorkList(t *testing.T) {
	var gotMethod, gotPath string

	client := newTestClient(t, func(w http.ResponseWriter, r *http.Request) {
		gotMethod, gotPath = r.Method, r.URL.Path
		respondJSON(t, w, http.StatusOK, `[
			{"topicId":"11111111-1111-1111-1111-111111111111","name":"Go",
			 "queries":{"HACKER_NEWS":"golang","DEVTO":"go"}},
			{"topicId":"22222222-2222-2222-2222-222222222222","name":"Postgres",
			 "queries":{"DEVTO":"postgres"}}
		]`)
	})

	topics, err := client.ListTopicQueries(t.Context())
	if err != nil {
		t.Fatalf("ListTopicQueries: %v", err)
	}

	if gotMethod != http.MethodGet || gotPath != "/topics/queries" {
		t.Errorf("request = %s %s, want GET /topics/queries", gotMethod, gotPath)
	}
	if len(topics) != 2 {
		t.Fatalf("expected 2 topics, got %d", len(topics))
	}
	if topics[0].TopicID != "11111111-1111-1111-1111-111111111111" || topics[0].Name != "Go" {
		t.Errorf("first topic = %+v, want its id and name carried through", topics[0])
	}
	// The map key is what tells the poller which source a query is for, so it has to survive
	// decoding as a SignalSource rather than a bare string.
	if got := topics[0].Queries[coreapi.SourceHackerNews]; got != "golang" {
		t.Errorf("hacker news query = %q, want it keyed by the source enum", got)
	}
	if got := topics[0].Queries[coreapi.SourceDevto]; got != "go" {
		t.Errorf("dev.to query = %q", got)
	}
}

// A source absent from the map means "do not poll this topic there", which must not decode
// into an empty query the poller would then send.
func TestListTopicQueriesLeavesUnnamedSourcesAbsent(t *testing.T) {
	client := newTestClient(t, func(w http.ResponseWriter, _ *http.Request) {
		respondJSON(t, w, http.StatusOK, `[
			{"topicId":"11111111-1111-1111-1111-111111111111","name":"Go","queries":{"DEVTO":"go"}}
		]`)
	})

	topics, err := client.ListTopicQueries(t.Context())
	if err != nil {
		t.Fatalf("ListTopicQueries: %v", err)
	}
	if _, ok := topics[0].Queries[coreapi.SourceGitHubTrending]; ok {
		t.Error("a source with no query should be absent from the map, not present and empty")
	}
}

func TestListTopicQueriesSurfacesAPIErrors(t *testing.T) {
	client := newTestClient(t, func(w http.ResponseWriter, _ *http.Request) {
		respondJSON(t, w, http.StatusInternalServerError, `{"message":"boom"}`)
	})

	if _, err := client.ListTopicQueries(t.Context()); err == nil {
		t.Fatal("expected a non-2xx response to surface as an error")
	}
}
