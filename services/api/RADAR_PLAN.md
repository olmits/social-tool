# Trend radar — API implementation plan

Scope and intent come from the root `PLAN.md`; the worker side is `services/workers/PLAN.md`.
This covers the **content-source loop only** — polling article sources for topic ideas that
become original posts. The platform loop (reading rising posts on Bluesky/Mastodon/Reddit
and replying to them) is a separate iteration, recorded under *Deferred* below.

## The two loops, and which one this is

The product has two distinct radar loops. They share the `signals` table and the drafting
path, but their sources, their output, and their publish call differ.

| | This iteration | Next iteration |
|---|---|---|
| Loop | Content sources → **original post** | Platform posts → **reply** |
| Sources | Hacker News, dev.to, GitHub | Bluesky, Mastodon, Reddit |
| Signal is | An article or repo | A post you can reply to |
| Publishes via | `adapter.post()` | `adapter.reply()` |

Keeping them separate matters because the second one needs things the first does not: reply
references (Bluesky needs a `uri` **and** a content-hash `cid`, for both the parent and the
thread root), an account to reply *from*, and a per-account reply rate cap. None of that
belongs in the schema until the loop that needs it is being built.

## Where this fits today

Delivered in the first slice (`V7__signals.sql`, `signal/`):

- `signals` table, unique on `(source, external_id)`.
- `SignalService.ingest` — batch upsert, idempotent, in-batch dedup, validation.
- `POST /signals` (worker ingest) and `GET /signals?source=` (ranked by score).
- `SignalSource` enum with all five sources from `PLAN.md:44`; three are polled.
- The `drafts.signal_id` FK that `V5__drafts.sql` deferred.

**Phases 1 and 2 below are now delivered too** (`V8__topics.sql`, `V9__signals_topic.sql`,
`topic/`, `signal/RadarService`, and the worker's `radar`/`source`/`coreapi` packages),
closing the gap this plan was written to close: the radar now knows what you write about,
end to end. A topic created in the panel changes what the next poll fetches, and every signal
stored carries the topic it was fetched for.

Only Phase 3 (optional) and the deferred platform loop are still open.

---

## Decisions to make before writing much code

### 1. How generic can the source list be?

The open question from the review of the first slice. Three levels, and the answer is that
two of them are worth having and the middle one is not.

**Level 1 — typed adapters (what exists).** One Go package per source. Adding a source is
~100–170 lines and one registry entry. Verbose, but it is the only level that copes with what
these APIs actually do: Hacker News needs a two-phase fetch (ids, then N concurrent item
reads) with dead/non-story filtering; GitHub needs a pinned API version and reports throttling
as `403`; the social platforms will need session creation and OAuth refresh.

**Level 2 — a declarative source config (endpoint template + field mappings).** Superficially
appealing, and it genuinely covers dev.to and GitHub, whose fetches are one GET and a field
map. It cannot express Hacker News's fan-out, Bluesky's session handshake, or Reddit's token
refresh. Adding those to the config language means inventing conditionals, loops, and secret
handling in YAML — a worse programming language than the one we already have, and the classic
inner-platform trap. **Rejected.**

**Level 3 — one RSS/Atom adapter plus a `feeds` table.** This is the real answer to "dynamic
sources", and it is dynamic precisely because it standardises on a protocol rather than
inventing a schema. A single Go package plus a user-editable table of feed URLs gives
unlimited user-configurable sources: dev.to publishes per-tag feeds, Reddit exposes `.rss` per
subreddit, GitHub exposes per-repo release feeds, and every blog and newsletter worth watching
has one.

The cost is real and worth stating plainly: **RSS carries no engagement number.** No points,
no reactions, no stars. An RSS signal can be ranked by recency and topic match, but not by
traction — which is the thing that makes a radar a radar rather than a feed reader.

So the two are complementary, not competing:

- **Typed adapters** where engagement data is the point — the three that exist, and the social
  platforms next iteration.
- **RSS** as the escape hatch for the long tail, added from the panel, ranked without a score.

Level 3 is Phase 3 below and is genuinely optional; Phases 1 and 2 do not depend on it.

### 2. Where does topic matching happen?

**Push the query upstream, per source** — not client-side keyword filtering over a fetched
front page. All three sources support it, though one needs a different endpoint:

| Source | Topic query | Notes |
|---|---|---|
| dev.to | `?tag=rust` | Already in the articles API |
| GitHub | `language:rust`, `topic:llm` | Search qualifiers, appended to the existing query |
| Hacker News | Algolia (`hn.algolia.com/api/v1/search`) | The Firebase API has **no search** — this is an endpoint change, not a parameter |

Client-side filtering would fetch 50 items to keep three, and would miss anything below the
front page. The one caveat is that the HN source becomes a rewrite rather than a tweak, since
Algolia returns a different response shape.

### 3. Is `signals.topic` the right name?

**No — rename it.** It currently holds the item's *headline* ("Shipping Go services"). The
panel has always used `topic` to mean the *category* ("Local-first", "Runtimes") with `title`
for the headline. Once topics are a real entity the current name is actively misleading, and
the panel is already written against the other meaning.

`topic` → `title`, plus a nullable `topic_id` FK. Nullable because an RSS signal, or a signal
from a run before topics existed, has no topic behind it.

---

## Phase 1 — Topics as a first-class entity — **delivered**

The slice that turns "what is popular" into "what is popular in the things I write about".

Kept here as the record of what was built and why. Three things landed differently from the
sketch below, all noted in place: the `topic_queries` composite key is modelled as a JPA
element collection rather than an entity, `GET /signals` gained `topicName` and `nativeScore`
on its response (task 4) at the panel plan's request, and the Hacker News source had to be
rewritten against a different upstream API entirely (task 6).

1. **`V8__topics.sql`** — `topics (id, name, enabled, created_at, updated_at)` with `name`
   unique, plus `topic_queries (topic_id, source, query)` unique on `(topic_id, source)`.
   A topic with no row for a source is simply not polled there.
2. **`V9__signals_topic.sql`** — rename `signals.topic` → `signals.title`; add nullable
   `topic_id` with an FK to `topics`; index `(topic_id, score DESC)` for the panel's
   per-topic ranking. Separate from V8 so the rename is revertible on its own.
3. **`topic/` slice** — `Topic`, `TopicQuery`, repositories, `TopicService`, `TopicController`
   with full CRUD (`GET/POST/PATCH/DELETE /topics`). Straight CRUD; no state machine.

   One thing not to leave to the default: a duplicate topic name needs its own exception
   registered in `GlobalExceptionHandler`'s 409 branch, alongside `DuplicateAccountException`.
   Left as a bare `IllegalArgumentException` it falls through to the 400 handler, and the
   panel cannot tell "name already taken" from "name was blank".
4. **Extend signal ingest, and the response the panel reads.** `IngestSignalsCommand.Item`
   gains `topicId`; `SignalService` validates that the topic exists and is enabled. `GET
   /signals` gains a `topicId` filter. Three additions to `SignalResponse` beyond the rename,
   all requested by `apps/admin/RADAR_PLAN.md`:

   - **`topicId`** — the FK, for linking and filtering.
   - **`topicName`** — joined in `SignalResponse.from()`. The panel renders a topic chip on
     every signal row; without the name it would have to fetch `/topics` alongside `/signals`
     and build an id→name map client-side, which breaks its rule of consuming the DTO
     unreshaped. Nullable, like `topicId`.
   - **`nativeScore`** — the source's own count, before normalization: HN points, dev.to
     reactions, GitHub stars. The panel's "Engagement" column has no other source; `score` is
     rescaled to 0-100 and `rawPayload` is deliberately opaque to the UI. One number only —
     the panel drops the mock's secondary comment count rather than have the API model two
     incomparable units.

   `nativeScore` needs a column in `V9__signals_topic.sql`, a field on
   `IngestSignalsCommand.Item`, and validation that it is non-negative (unlike `score` it has
   no upper bound). **It also needs three one-line changes on the Go side**, because the
   number exists there today and is thrown away: `source.Item.NativeScore` is read by
   `source.Normalize`, which keeps only the rescaled result. Carry it through
   `source.Signal`, `coreapi.Signal`, and `radar.toWire`.
5. **`GET /topics/queries`** — a worker-facing projection returning enabled topics with their
   per-source queries, so the poller fetches its whole work list in one call.
6. **The poller selects topics** (`radar/`, `source/`, `coreapi/topics.go`). The half that
   makes the other five visible to a user. A pass now starts by reading the work list, plans
   one fetch per `(source, topic)` pair that has a query, and stamps every signal with the
   topic it was fetched for.

   `source.Source.Fetch` takes the topic's query, in whatever dialect that source searches in.
   Three consequences worth knowing:

   - **Hacker News moved from the Firebase API to Algolia.** `/v0/topstories.json` is a fixed
     ranked list with no way to ask it for a subject, so a topic-scoped poll is not possible
     there at all. Algolia indexes the same corpus, takes a query, and returns whole stories
     in the search response — which also deleted the per-item fan-out and its worker pool.
     `objectID` is the same item number Firebase returned, so external ids did not churn.
   - **A pass with no enabled topics polls nothing** and logs a warning, rather than falling
     back to a global listing. The fallback is the behaviour topics replaced; keeping it would
     quietly refill the radar with the noise this plan set out to remove.
   - **Request volume is now one call per `(source, topic)` pair.** Sources run concurrently
     and a source's own topics run in sequence, so the burst against any one host stays at one
     request — but GitHub's unauthenticated 10/minute ceiling is now reached at ten topics
     rather than never. `GITHUB_TOKEN` lifts it to 30.

   A topic naming a source this worker does not run — one excluded from `RADAR_SOURCES`, or
   Reddit and Product Hunt, which the API knows and the poller does not — is skipped, not an
   error. Failure isolation is per pair: one topic's query being rejected costs that topic on
   that source and nothing else.

**Acceptance.** A topic created in the panel changes what the next radar run fetches, and
`GET /signals?topicId=` returns only that topic's signals, ranked.

## Phase 2 — Per-topic scoring — **delivered with Phase 1**

7. **Normalize within `(source, topic)`, not within source.** The rescale used to run against
   the highest score in a source's batch, so a quiet topic's best item was buried under
   whatever was trending globally. Per-topic rescaling makes a topic's leader score 100 within
   its own topic, which is what the panel's `match` field has always meant.

This landed as a consequence of Phase 1 task 6 rather than as the change to `source.Normalize`
sketched here. Once a pass fetches per `(source, topic)` pair, each batch handed to `Normalize`
*is* one topic's worth of items, and the existing per-batch rescale is already per-topic.
Nothing inside `Normalize` changed; what changed is what a batch means. Worth knowing if the
fan-out is ever reshaped — the property is a product of how the radar groups its fetches, and
would be lost by merging batches back together before normalizing.

## Phase 3 — RSS as the generic source (optional)

8. **`V10__feeds.sql`** — `feeds (id, topic_id, url, name, enabled)`.
9. **`feed/` slice** — CRUD, so feeds are managed from the panel like topics.
10. **`SignalSource.RSS`** — one more enum value; the ingest path is unchanged.

Scoring for RSS signals is recency plus topic match only; the `score` column stays, but an RSS
signal's score is not comparable to a Hacker News one. Worth deciding at that point whether
the panel ranks the two together or in separate lists.

---

## Deferred — the platform loop (next iteration)

Recorded here so the next session does not re-derive it. **None of this should be built now**;
each item exists because the reply loop needs something the content loop does not.

### D1. Signals that are repliable posts

`signals` needs a `kind` discriminator (`CONTENT` | `PLATFORM_POST`) and, for platform posts,
fields the content loop has no use for: the platform, the author handle, the post body, and
the reply reference.

The reply reference is the part that does not fit the current shape. `external_id` alone is
insufficient for Bluesky: a reply needs `ReplyRef{root: {uri, cid}, parent: {uri, cid}}`, and
the **`cid` is a content hash that cannot be derived from the `uri`** — it has to be captured
at fetch time. Mastodon needs only `in_reply_to_id`; Reddit needs a `t3_`/`t1_` fullname.
Three shapes for one concept.

Follow the `mastodon_account_details` precedent rather than nullable columns on `signals`: a
`platform_post_signals` child table keyed by `signal_id`.

### D2. Reply drafts

`drafts` needs to record what a reply replies to. `signal_id` already links a draft to its
origin, so the reply target can be resolved through it — but the *strong ref must be
snapshotted onto the draft at creation*, not read from the signal at publish time. A signal
row is refreshed on every radar run; resolving the ref late would let a re-poll silently
change what an approved draft replies to.

No state machine change: a reply draft is still `DRAFT → APPROVED → SCHEDULED → PUBLISHED`.

### D3. `PlatformAdapter.reply` on the Go side

`PlatformAdapter.reply(parentRemoteId, text)` is already declared in Java, and `ReplyRef` /
`StrongRef` already exist in `adapter/bluesky/dto/`. The Go adapter has only
`Post(ctx, text)` — the publisher literally cannot post a reply today. Go's `adapter.Adapter`
gains a `Reply` method and the publisher branches on the draft's kind.

### D4. Per-account reply rate cap

Replying at volume is the behaviour platforms police hardest, and Reddit's Responsible Builder
Policy (cited at `PLAN.md:80`) applies directly. The manual-approval gate is the primary
safeguard and already exists; a per-account daily reply cap enforced in `DraftService` is the
backstop, so a fast approver cannot run the account past a platform's tolerance.

### D5. Reddit and Product Hunt

Both need API credentials; Reddit additionally needs the app approval described in
`PLAN.md:80`. Both already exist as `SignalSource` values so the enum does not churn when they
land. Naming either in the worker's `RADAR_SOURCES` is rejected at startup rather than
silently doing nothing.

---

## Explicitly out of scope

- ~~**AI drafting**~~ — **delivered.** `generation/` is a real slice now: `POST /drafts/generate`
  takes a signal, a voice profile and an account and returns a persisted `DRAFT`. See
  `generation/README.md` for its shape and the deferrals it leaves behind. `CreateDraftCommand`
  also gained `signalId`, so a hand-written draft can record where it came from.
- **Analytics** (`metrics`, `posts` tables). Phase 3 of the root plan, blocked on
  `DEFERRED.md` §1.

## Suggested order

Phase 1 (tasks 1→6, sequential — 2 depends on 1, 4 on both, 6 on 5) → Phase 2 → panel work
(`apps/admin/RADAR_PLAN.md`) → Phase 3 only if the long tail turns out to matter.

Phase 1 was the whole value of this iteration: without topics the radar stores noise, and
every panel screen was waiting on it. Phases 1 and 2 are done; **the panel work is what is
next**, and nothing in it is blocked.
