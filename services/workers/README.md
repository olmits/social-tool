# social-workers

Background workers for the social media publishing tool. Everything in the pipeline that runs
on a timer rather than in response to a user lives here: publishing scheduled posts, polling
content sources for topic ideas, and ingesting per-post metrics.

**Stack:** Go 1.25 · standard library only

---

## What the workers do

Three jobs, one binary. The worker to run is chosen by subcommand, so all three ship in the
same image and the split happens at the deployment level.

| Subcommand | Job | Status |
|---|---|---|
| `publish` | Takes drafts that are approved and past their scheduled time, posts them to the platform, and reports the outcome back to the core API. | Working — Bluesky only |
| `poll` | Polls free-API sources (Hacker News, Reddit, dev.to, GitHub Trending) into normalized `signals` for use as drafting input. | Not implemented |
| `analytics` | Fetches engagement metrics for published posts. | Not implemented |

The publisher is the only component in the whole system that writes to a social platform, and
it only ever acts on drafts a human has already approved in the admin panel. Nothing is posted
automatically.

**Workers never touch Postgres.** The draft state machine in the Java core API is the single
writer of `drafts.status`, so every read and every state transition goes over HTTP through
`internal/coreapi`. See the root `PLAN.md` for the architecture and `PLAN.md` in this
directory for the phased build-out.

---

## Prerequisites

- Go 1.25
- A running core API (`services/api`) — the workers are a client of it
- Docker (only for containerised runs)

---

## Running locally

### 1. Start the core API

```bash
cd ../api && docker compose up -d
```

It listens on `http://localhost:8080` with the shared dev key `local-dev-api-key`.

### 2. Run a worker

```bash
CORE_API_KEY=local-dev-api-key go run ./cmd/workers publish
```

Or via the Makefile, which supplies the dev key by default:

```bash
make run
```

The publisher checks for due drafts immediately, then every `POLL_INTERVAL` (default 30s):

```json
{"level":"INFO","msg":"worker starting","worker":"publisher"}
{"level":"INFO","msg":"core api reachable","url":"http://localhost:8080","status":"UP"}
{"level":"INFO","msg":"publish loop started","interval":"30s"}
{"level":"INFO","msg":"publishing due drafts","scheduled":2,"due":1}
{"level":"INFO","msg":"published","draft":"383dee20-…","platform":"BLUESKY","remoteId":"at://…"}
```

Ctrl-C (or `SIGTERM`) shuts it down cleanly and exits 0. If the core API is down the worker
logs a warning and keeps running rather than exiting — it may well start before the API does.

`--once` runs a single pass and exits, which is handy while testing:

```bash
CORE_API_KEY=local-dev-api-key go run ./cmd/workers publish --once
```

`workers help` prints the available subcommands and every environment variable.

### Credentials in local development

The publisher resolves an account's credential itself: the core API returns only a
`credentialRef`, never the secret. Locally that reference points at a file written by the
API's `LocalCredentialStore`, so `LOCAL_CREDENTIALS_DIR` must be the same directory the API
writes to (`social-api.local.credentials-dir`).

The default, `../api/.local-secrets`, matches an API started with `./mvnw spring-boot:run`
from `services/api`. If you run the API under **Docker Compose** instead, its secrets live
inside the container at `/tmp/local-secrets`, where the worker cannot reach them — bind-mount
that path to a host directory and point `LOCAL_CREDENTIALS_DIR` at it.

### Trying it without posting for real

Point `BLUESKY_BASE_URL` at a local stub that answers the two XRPC endpoints
(`com.atproto.server.createSession` and `com.atproto.repo.createRecord`) to exercise the whole
loop without touching a real account.

---

## Configuration

All configuration is environment variables. Anything invalid is reported at startup, all
problems at once, and the process exits 1.

| Variable | Default | Required |
|---|---|---|
| `CORE_API_URL` | `http://localhost:8080` | no |
| `CORE_API_KEY` | — | **yes** |
| `LOG_LEVEL` | `info` | no — `debug` \| `info` \| `warn` \| `error` |
| `HTTP_TIMEOUT` | `10s` | no — any Go duration |
| `POLL_INTERVAL` | `30s` | no — how often to check for due drafts |
| `BLUESKY_BASE_URL` | `https://bsky.social` | no — override for a self-hosted PDS or a stub |
| `CREDENTIALS_BACKEND` | `local` | no — `local` \| `secretsmanager` |
| `LOCAL_CREDENTIALS_DIR` | `../api/.local-secrets` | no — used by the `local` backend |
| `AWS_REGION` | — | no — used by the `secretsmanager` backend |

`CORE_API_KEY` must match the core API's `API_KEY`; it is sent as the `X-API-Key` header on
every request.

---

## Development

```bash
make check          # go vet + go test
make test           # tests only
make build          # binary at bin/workers
make fmt            # gofmt -w
```

Tests are standard-library only — the core API client is exercised against `httptest` stubs,
so nothing external needs to be running.

---

## Running with Docker

```bash
make docker-build
docker compose up --build
```

The compose file runs the publisher and reaches the core API through `host.docker.internal`,
since the API is started from its own compose file in `services/api`.

The image has no default subcommand — it comes from the command, so one image backs every
worker:

```bash
docker run --rm -e CORE_API_KEY=local-dev-api-key social-workers:local publish
```

---

## Project layout

```
cmd/workers/          # entrypoint: subcommand dispatch, logging, signal handling
│   ├── main.go
│   └── publish.go    # the publisher subcommand — wires the pieces together
internal/
├── config/           # environment loading and validation
├── coreapi/          # typed client for the Java core API
├── creds/            # credential resolution: local files, or Secrets Manager
├── adapter/          # the platform interface
│   └── bluesky/      # AT Protocol: createSession + createRecord
├── publisher/        # the poll-and-publish loop and its callbacks
└── worker/           # shared lifecycle: start/stop logging, shutdown semantics
```
