# CLAUDE.md — workers (Go)

This file is for Claude Code. It describes how to work in this service.
See the root PLAN.md for architecture intent and the full project context.

## What this service does

The background workers for the social media tool. One binary, three subcommands:

- `publish` — claims approved drafts and posts them to platforms.
- `poll` — the trend radar: reads enabled topics from the core API, polls
  content sources for each of them, stores the result as drafting input.
- `analytics` — reserved; not implemented.

All three ship in one image; the split happens at the task-definition level.

This service owns no database. Every read and write goes over HTTP through
`internal/coreapi`, because the draft state machine in the API's `DraftService`
is the single writer of `drafts.status`.

## Stack

- Go 1.25 (see `go.mod` for the exact toolchain)
- Standard library only for HTTP, JSON, and tests
- AWS SDK v2 — Secrets Manager (credentials)
- No web framework, no assertion library, no mocking library

## Package structure

```
cmd/workers/            # main.go dispatches the subcommand; poll.go, publish.go
internal/
├── adapter/            # Adapter interface + bluesky/
├── config/             # environment loading and validation
├── coreapi/            # typed client for the Java core API
├── creds/              # credential resolution (local files or Secrets Manager)
├── publisher/          # the publish loop
├── radar/              # the trend-radar loop
├── source/             # Source interface + devto/, github/, hackernews/
└── worker/             # shared run/shutdown plumbing
```

## Key conventions

**`ok` is the name of a comma-ok bool.** A map lookup, type assertion, channel
receive, or any stdlib call returning a `(value, bool)` pair names that bool
`ok` — never `running`, `set`, `found`, `present`, `exists`, or a name invented
for the specific call site. The reader recognises `ok` instantly as "the lookup
succeeded"; anything else makes them stop and work out whether it is a domain
fact or a presence check. `src, ok := r.sources[name]`, not
`src, running := r.sources[name]`.

**Errors wrap with `%w` and name the operation.** `fmt.Errorf("source %s, topic
%s: %w", name, f.topic, err)`. An error should read as a path from the failure
to the caller. Never return a bare `err` from a layer that knows something the
caller does not.

**Structured logging via `slog`.** Key-value pairs, not formatted strings:
`logger.Warn("fetch failed", "source", name, "error", err)`. A nil logger passed
into a constructor is replaced with `slog.New(slog.DiscardHandler)`, so tests
never need to supply one.

**Adapters and sources are registries, not switches on type.** Adding a platform
is a new sub-package implementing `adapter.Adapter` plus one entry where the
registry is built. Adding a content source is a new sub-package implementing
`source.Source`, one entry in `buildSources` (`cmd/workers/poll.go`), and one
name in `config.knownSources`. No change to a run loop.

**The radar is topic-scoped.** A pass reads `GET /topics/queries`, plans one
fetch per `(source, topic)` pair, and stamps every signal with its topic id.
A pass with no enabled topics polls nothing — it does not fall back to a global
listing, which is the behaviour topics replaced.

**Fan out by host, not by unit of work.** `collect` runs one goroutine per
source and that source's topics in sequence. Rate limits are enforced per host,
so the shape of `map[string][]fetch` is what keeps one request per host in
flight. Do not flatten that into a goroutine per fetch.

**Per-goroutine result slots, not a mutex.** Where goroutines write results
concurrently, allocate the slice at full length up front and give each goroutine
its own index. It is the array never being resized, plus each index having one
writer, that makes this safe — a `map` in the same position would not compile
and would need a lock.

**Inject the clock.** Anything that reads the time takes a `now func() time.Time`
on its options struct, defaulted to `time.Now`. Tests assert on timestamps
without touching the wall clock.

**Ingest is idempotent; publishing is not.** The core API upserts signals on
`(source, externalId)`, so a retried radar pass converges on the same rows and
needs no claim or lease. The publisher does claim, because a draft posted twice
cannot be taken back.

## Configuration

All configuration comes from the environment via `config.Load`, which reports
every problem it finds at once rather than failing on the first. Add a new
setting as a constant for the key, a field on `Config`, and a validation line
in `Load` — not as a scattered `os.Getenv` at the point of use.

## Testing approach

- Standard library `testing` only. No testify, no gomock, no golden-file
  helpers. Table tests where the cases are genuinely uniform, separate named
  tests where they are not.
- Upstream HTTP is faked with `httptest.NewServer`, never by swapping in an
  interface for `*http.Client`. The stub asserts on what was actually sent.
- Collaborators are faked with small hand-written structs in the `_test` package
  (see `fakeAPI` and `fakeSource` in `internal/radar/radar_test.go`).
- Test names state the behaviour, not the method:
  `TestRunOnceStampsEachSignalWithItsTopic`, not `TestRunOnce2`.
- Run concurrency-touching packages under `-race` before committing:
  `go test -race ./internal/radar/...`

## Before committing

```
gofmt -l .      # must print nothing
go vet ./...
go test ./...
```
