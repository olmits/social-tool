# Workers — high-level implementation plan (Go)

Scope and intent come from the root `PLAN.md`. This is the shape of the work, not the
detail. The module is initialized (`go.mod` only) — everything below is still to build.

## What this service owns

Three jobs, per the root plan. They share a codebase and an image, not a runtime.

| Worker | Trigger | Job |
|---|---|---|
| **Publisher** | due scheduled drafts | takes an approved+scheduled draft, posts it to the platform, reports the result back |
| **Trend radar** | periodic | polls HN / Reddit / dev.to / GitHub Trending, normalizes into `signals` |
| **Analytics** | periodic | fetches per-post metrics for published posts, writes `metrics` |

The publisher is the only component in the system that writes to a social platform. It is
also the one that closes the product loop, so it goes first — the other two are additive.

## Where this fits today

- The Java API's draft state machine is done, including the two callbacks the publisher
  needs: `PATCH /drafts/{id}/published` and `PATCH /drafts/{id}/failed`. Auth is a shared
  `X-API-Key`.
- Nothing enqueues to SQS yet. The `sqs` SDK is in the Java `pom.xml`, but there is no
  producer code — the queue described in the root plan does not exist in either service.
- Java has a `BlueskyAdapter`, deliberately *not* wired to publishing. Go needs its own
  platform client; that duplication is by design, not an accident to clean up.
- `signals`, `posts`, and `metrics` tables do not exist yet. Flyway is owned by the Java API.

---

## Decisions to make before writing much code

These are the forks that shape everything else. My recommendation on each.

1. **One binary or three?** → **One binary, a subcommand per worker** (`workers publish`,
   `workers poll`, `workers analytics`). One image, one build, one config path; the split
   stays at the ECS task-definition / EventBridge level where it belongs.

2. **How the publisher gets work.** → **Start by polling the Java API**
   (`GET /drafts?status=SCHEDULED`, filter for due), behind a small `DueDrafts` interface.
   SQS is the target end state, but it requires a producer in Java that doesn't exist;
   putting the seam in an interface means swapping in SQS later doesn't touch publish logic.

3. **Does Go talk to Postgres directly?** → **No — resolved, HTTP for both workers.** The
   publisher was never in question: the state machine has to stay the single writer of
   `drafts.status`. Trend radar was the open case, on the assumption that pushing thousands
   of signals through HTTP would be awkward. Actual volume settled it — three sources at 50
   items each is ~150 rows per run, one request, nowhere near awkward. So Java keeps sole
   ownership of Flyway and the schema, and the workers need no database credentials, no
   connection pool, and no RDS security-group rule. `POST /signals` upserts on
   `(source, external_id)`; see `internal/radar` and the core API's `signal` slice.

4. **How Go gets account credentials.** Secrets Manager by `credential_ref` — but
   `AccountResponse` doesn't expose that field, so there is no path today. **This is a
   blocking gap on the Java side**, see below.

5. **Double-publish protection.** Any queue or poll loop is at-least-once. The state
   machine helps (a second `markPublished` on a `PUBLISHED` draft is an invalid transition),
   but that only catches it *after* the post went out. Needs a claim step before the
   platform call — a lease column or a `SCHEDULED → PUBLISHING` transition. Ties into the
   unschedule race already recorded in `services/api/DEFERRED.md` §2.

---

## Phase 0 — Skeleton

Small, but it sets every convention that follows.

- `cmd/workers` entrypoint with subcommands; `internal/` for everything else.
- Config from env (region, API base URL, API key, poll intervals), validated at startup.
- Structured logging (`log/slog`), context plumbing, graceful shutdown on SIGTERM —
  Fargate sends it, and a publisher killed mid-post is the worst failure mode here.
- A typed client for the Java API (`internal/coreapi`): list drafts, mark published,
  mark failed. Injects `X-API-Key`; maps non-2xx to typed errors.
- Dockerfile + a `workers` service in the compose file so it runs against local Postgres/API.

## Phase 1 — Publisher (Bluesky slice done)

Delivered: `internal/creds` (local + Secrets Manager), `internal/adapter` + `bluesky`,
`internal/publisher` (poll, due-selection, callbacks). `credentialRef` is now exposed on the
core API's `AccountResponse`, closing the blocker below. Still open from this phase: bounded
retry/backoff on the publish itself, per-platform rate limiting, and the Mastodon adapter.

- **Platform adapter interface in Go** — `Post(ctx, text) (remoteID, error)`, mirroring the
  Java surface. One implementation to start: **Bluesky** (session create + `createRecord`).
- **Credential resolution** — Secrets Manager in prod, env/file fallback locally, mirroring
  Java's `CredentialStore` split so local dev needs no AWS.
- **The publish loop** — fetch due drafts → claim → resolve credentials → post → report
  `published` (with `remoteID`) or `failed` (with reason). Idempotent: re-running must not
  double-post.
- **Retries and rate limits** — bounded retry with backoff on transient platform errors,
  terminal errors go straight to `markFailed`. Per-platform rate limiting.
- **Mastodon adapter** second — it proves the interface is right, since Mastodon is
  federated and each account carries its own instance host.

End state: approve and schedule a draft in the panel, and it actually appears on Bluesky.

## Phase 2 — Trend radar (Hacker News / dev.to / GitHub slice done)

Delivered: `internal/source` (the `Source` interface plus score normalization),
`internal/source/{hackernews,devto,github}`, `internal/radar` (concurrent fan-out with
per-source failure isolation, chunked ingest), and the core API's `signal` slice behind
`POST /signals`. Still open from this phase: Reddit and Product Hunt, and surfacing signals
in the panel's Radar page, which still runs on `lib/mock-data.ts`.

- A `Source` interface (`Fetch(ctx) []Item`) with one implementation per source; each is
  independent and failure-isolated — one dead source must not stall a run.
- Normalization into the `signals` shape, plus deduplication across runs.
- **Scoring is normalized per source, not carried raw.** HN points, dev.to reactions, and
  GitHub stars are incomparable units, so each source's native number is rescaled to 0-100
  against the highest in that run. The curve is logarithmic: a front-page HN story can
  outscore the tenth item by an order of magnitude, and a linear scale would flatten the
  whole mid-field — the part actually worth drafting from — into low single digits.
- **Deduplication is server-side, on `(source, external_id)`.** That makes a run idempotent:
  re-polling the same listings, retrying after an ambiguous failure, or overlapping two runs
  all converge on the same rows. Unlike the publisher, the radar needs no claim or lease.
- Persistence — resolved, see decision 3. `POST /signals` on the Java side.
- **Reddit and Product Hunt are not polled.** Both need API credentials, and Reddit needs the
  app approval described in the root PLAN.md. They exist as `SignalSource` values so the
  enum does not have to change when they land, but naming either in `RADAR_SOURCES` is
  rejected at startup rather than silently doing nothing.

## Phase 3 — Analytics

- `FetchMetrics` on the Go adapter interface, per platform.
- A run over published posts, writing `metrics` rows.
- Depends on the `posts` table, which is the follow-up already recorded in
  `services/api/DEFERRED.md` §1 — worth doing before this phase rather than during.

## Phase 4 — Deployment

- ECS Fargate service for the publisher; EventBridge Scheduler for the periodic workers.
- Task IAM roles for Secrets Manager (and SQS, if adopted). CloudWatch logs and an alarm on
  publish failures — a silently broken publisher looks exactly like "no posts were due".

---

## Blocked on the Java API

- **Credential access for workers** — `credential_ref` is not exposed on any endpoint.
  Needs either that field on a worker-scoped account response, or a dedicated endpoint.
  Blocks Phase 1.
- **A claim / lease transition** — see decision 5. Blocks safe Phase 1 operation, though a
  single-instance publisher can ship without it.
- **`posts` table** (DEFERRED §1) — blocks Phase 3.
- ~~**`signals` table**~~ — resolved: `V7__signals.sql` plus the `signal` slice.
- **SQS producer** — only if decision 2 moves off polling.

## Suggested order

Phase 0 → Bluesky publish path end-to-end (the whole point of the service) → Mastodon →
trend radar → analytics. Ship the publisher against a single instance before building
claim/lease machinery; the correctness gap is real but only bites on concurrency.
