# Trend radar — frontend implementation plan (admin panel)

Plan for the Topics and Trend Radar screens. The Radar page exists visually but runs entirely
on `lib/mock-data.ts`; Topics does not exist at all. This plan makes both real, following the
**accounts feature as the reference pattern** (`lib/api/{client,accounts,types,mappers,actions}.ts`)
and the drafts feature as the precedent for wiring an existing mock page to live data.

See root `PLAN.md` for product intent, `services/api/RADAR_PLAN.md` for the backend slice this
depends on, and `DRAFTS_PLAN.md` for the conventions this mirrors.

This covers the **content-source loop only** — signals that become original posts. The reply
loop (rising posts from Bluesky/Mastodon, replied to rather than written about) is the next
iteration and is recorded under *Deferred* below.

## Backend endpoints

Shipped today (`signal/`, `V7__signals.sql`):

- `GET /signals?source=` — stored signals, ranked by score. **The only read** — there is no
  `GET /signals/{id}`, so nothing here can be built around fetching one signal.
- `POST /signals` — the Go poller's ingest. Not a panel call.

`services/api/RADAR_PLAN.md` Phase 1 has since been **delivered**, so everything below is
unblocked:

- `GET /topics`, `POST /topics`, `PATCH /topics/{id}`, `DELETE /topics/{id}`
- `GET /topics/queries` — the poller's work list. Not a panel call.
- `GET /signals?topicId=&source=` — the `topicId` filter, composing with `source`
- `SignalResponse`: `topic` renamed to `title`, plus `topicId`, `topicName`, and `nativeScore`

Two behaviours worth knowing before wiring the Topics page:

- **`PATCH /topics/{id}` is a sparse patch**, unlike `PATCH /drafts/{id}`. A null field is left
  unchanged, so the enabled toggle sends `{"enabled": false}` alone. A *present* `queries` map
  replaces the topic's queries wholesale — an empty map clears them.
- **A duplicate topic name is a 409**, distinct from a 400 for a blank or over-long one, and
  the match is case-insensitive. The panel can rely on the status.

The DTO the panel consumes:

```ts
interface SignalResponse {
  id: string;
  source: SignalSource;     // HACKER_NEWS | DEVTO | GITHUB_TRENDING | REDDIT | PRODUCT_HUNT
  externalId: string;
  title: string;            // renamed from `topic`
  topicId: string | null;   // null for a signal polled before topics existed
  topicName: string | null; // joined server-side, so the panel does no id→name lookup
  url: string;
  score: number;            // 0-100 — the panel's `match`
  nativeScore: number;      // the source's own count, pre-normalization
  rawPayload: string;       // JSON as a *string*; the panel does not consume it
  fetchedAt: string;
  createdAt: string;
}
```

`topicName` is resolved server-side and `nativeScore` is stored per signal, both added at this
plan's request (`services/api/RADAR_PLAN.md` Phase 1 task 4). Without them the panel could not
render the topic chip or the Engagement column except through a client-side join and a
per-source `rawPayload` parser, which is why they were asked for rather than worked around.

**The poller now selects topics** (API-plan Phase 1 task 6), so the topic filter returns real
rows: a pass reads `GET /topics/queries`, fetches each source per topic, and stamps every
signal with the topic it was fetched for. Two things follow for the panel:

- **A `topicId` of null now means an old signal**, not an unfinished feature — one polled
  before topics existed, or one whose topic was since deleted. Rendering no chip is right.
- **The radar polls nothing until a topic exists.** A fresh environment with no topics stores
  no signals at all, by design. If the Radar page looks empty, the Topics page is the fix, and
  the empty state should say so rather than implying something is broken.

`SignalSource` values are UPPERCASE (`HACKER_NEWS | DEVTO | GITHUB_TRENDING | REDDIT |
PRODUCT_HUNT`), like every other backend enum — but only the first three are ever polled. A
topic may carry a query for any of the five; the poller skips the ones it does not run, so the
Topics form should not offer Reddit or Product Hunt yet.

## Conventions to follow

- Server-only fetch via `apiFetch` (`lib/api/client.ts`); `X-API-Key` never reaches the browser.
- One typed function per endpoint in `lib/api/signals.ts` / `lib/api/topics.ts`, tag-cached
  with `SIGNALS_TAG` / `TOPICS_TAG`.
- Mutations are `"use server"` actions returning the `ActionResult` discriminated union and
  calling `updateTag(...)` for read-your-writes.
- Components consume `SignalResponse` / `TopicResponse` directly — no UI-side reshape. Backend
  enums stay UPPERCASE in `types.ts` and are formatted for display at the edge, in
  `lib/api/mappers.ts`.
- Component/hook conventions per `CLAUDE.md`: components under ~150–200 lines, logic in hooks,
  `PascalCase` components / `camelCase` hooks, `useController` fields, no JSX stored in
  variables.

---

## Status — Phases 1–5 delivered

Everything below is built, against a local API rebuilt to the current `topic`/`signal` slices.
Two decisions differ from the plan as written, both noted at their task:

- **Task 14's stat tiles are scoped to the topic, not to the source chip** — the radar page
  fetches a second, source-unfiltered list for them. "Sources active: 1" whenever a source chip
  is on is a tautology, not a statistic. Without a source filter it is the same fetch.
- **Task 15's Draft button renders `disabled`, with the reason on it**, rather than inert-but-
  clickable like Review's Regenerate. It is the card's primary action; one that looks live and
  does nothing on click is worse than one that says why.

Only task 15 remains, still blocked on the two backend items below.

---

## A naming note, before anything else

The mock already uses the vocabulary the backend is moving to:

```ts
{ source: "Hacker News", title: "Show HN: …", topic: "Local-first", match: 92 }
```

`title` is the headline; `topic` is the **category**; `match` is per-topic relevance. The
backend's `signals.topic` column currently holds the headline, which collides with all of
this — hence the rename in the API plan. **The UI does not need to adapt to the backend here;
the backend is adapting to the UI.** Keep the mock's vocabulary when wiring the real data.

The full mapping, since three of the mock's seven fields are less obvious than the rename:

| Mock `Signal` | Comes from | Note |
|---|---|---|
| `source` | `source` | UPPERCASE enum; formatted at the edge |
| `title` | `title` | after the rename |
| `topic` | `topicName` | new field; a null renders no chip |
| `match` | `score` | see the caveat below |
| `engagement` | `nativeScore` + `source` | new field, and **one** number, not two |
| `age` | `createdAt` — *not* `fetchedAt` | see below |
| — | `url` | the DTO carries it; the page renders nothing for it today |

**`age` must come from `createdAt`.** `SignalService.ingest` refreshes `fetchedAt` on every
re-poll, so it means *last seen*, not *first seen* — a story that has sat on the front page
for two days reports an age of minutes. `createdAt` is `updatable = false` and is the
first-sighting timestamp the "2h" badge actually means.

**`engagement` loses the comment count.** The mock renders `"842 pts · 310 comments"`; the
backend will carry a single `nativeScore`. The panel formats it per source — `842 pts`,
`456 reactions`, `1.2k ★` — and the second number is dropped. Recovering it would mean a
per-source `rawPayload` parser in the panel, which is the thing `nativeScore` exists to avoid.

**`match` is per-topic, and "Match" is now the honest label.** The radar fetches per
`(source, topic)` pair and normalizes each batch on its own
(`services/workers/internal/source/source.go`), so a topic's leader scores 100 within its own
topic rather than against whatever is trending globally — API-plan Phase 2, delivered. What it
still is *not* is a per-source comparison: a 100 from dev.to and a 100 from GitHub mean "top of
this topic on that source", so ranking the two against each other reads more than the number
carries.

---

## Phase 1 — Data / API layer (foundation)

1. **Types** — add `SignalResponse`, `SignalSource`, `TopicResponse`, `TopicQuery`, and the
   command types to `lib/api/types.ts`, mirroring the Java DTOs.
2. **Topics data layer** — `lib/api/topics.ts`: `listTopics()`, `createTopic`, `updateTopic`,
   `deleteTopic`; export `TOPICS_TAG` and tag the reads.
3. **Signals data layer** — `lib/api/signals.ts`: `listSignals({ topicId?, source? })`;
   export `SIGNALS_TAG`.
4. **Server actions** — `createTopicAction`, `updateTopicAction`, `deleteTopicAction`
   appended to the existing `lib/api/actions.ts`; accounts and drafts already share that one
   file, so a separate topics module would be the first split. Wrap the calls, return
   `ActionResult`, `updateTag(TOPICS_TAG)`, and surface `ApiError.message` — `toActionError`
   already does this — rather than switching on status. ~~A duplicate topic name is **not**
   reliably a 409.~~ **It is now:** `DuplicateTopicException` exists and
   `GlobalExceptionHandler` registers it for 409. No branching was needed even so — the
   exception's message already names the clash ("A topic named X already exists"), so the form
   surfaces `message` and the status never has to be read.
5. **Display helpers** — add `sourceLabel()`, per-source badge metadata, and
   `engagementLabel(source, nativeScore)` to `lib/api/mappers.ts`, alongside the existing
   `statusLabel` / `charLimit`. `engagementLabel` is where the per-source unit lives —
   `pts` / `reactions` / `★` — and is the only place `nativeScore` is formatted.
6. **`SIGNAL_SOURCES` — the polled set, not the enum.** Only `HACKER_NEWS`, `DEVTO`, and
   `GITHUB_TRENDING` have a `Source` implementation
   (`services/workers/cmd/workers/poll.go:buildSources`). `REDDIT` and `PRODUCT_HUNT` are
   declared in the enum but unreachable — both need credentials, and Reddit needs the app
   approval in `PLAN.md:80`. Drive the chip row from the polled set so the page does not show
   two filters that can only ever return nothing.

## Phase 2 — Extract `PLATFORM_META` out of the mocks

7. **Move `PLATFORM_META` to `lib/api/mappers.ts`.** This is display metadata, not fixture
   data, and it is imported by six *real* components — `sidebar`, `ReviewBoard`, `EventCard`,
   `ScheduleCalendar`, `PlatformPicker`, `DetailHeader`. It has to move before
   `lib/mock-data.ts` can be deleted, and doing it as its own step keeps the Radar rewiring
   diff readable.

   `SOURCE_META` gets the same treatment, keyed by the UPPERCASE `SignalSource` rather than
   the mock's display strings. `SOURCE_CHIPS` re-keys with it — it becomes
   `(SignalSource | "All")[]` built from the polled set of task 6, because the chip's value is
   what goes into the `source=` query param. It cannot stay a list of display strings.

## Phase 3 — Topics page

8. **Route + nav** — `app/(app)/topics/page.tsx`, and a fourth `sidebar.tsx` entry placed
   *above* Trend Radar, since topics configure it.
9. **`components/topics/`** — `TopicList` (rows with name, per-source queries, enabled
   toggle), `TopicDialog` (create/edit), `useTopicForm` (`useController` fields, per the
   `connect-account/` reference implementation). Delete behind a confirm dialog, matching
   `DiscardDraftDialog`.
10. **Empty state that teaches** — an empty Topics list is the first thing a new install
    shows, and the radar returns nothing until a topic exists. The empty state should say so
    and link straight to the create dialog.

## Phase 4 — Radar page off mocks

11. **Signals from the API** — replace the `SIGNALS` mock with `listSignals(...)`, keep the
    existing layout (stats row, search, source chips, table/cards toggle). Add loading, empty,
    and error states; the page currently has none because mock data cannot fail. Two details
    the mock hid:

    - **Link out.** Each row and card gets `signal.url` as a link on the title. The mock has
      no URL, so the page has never had one — but a signal you cannot open is not actionable,
      and this is the cheapest thing on the page.
    - **`updated 2m ago`** in the header is hardcoded. It is `max(fetchedAt)` across the
      returned signals, which is exactly what `fetchedAt` is good for (see the naming note).

12. **Refresh** — a `"use server"` action calling `updateTag(SIGNALS_TAG)`, not a client
    refetch. It re-reads what the poller has already stored; it does not trigger a run (see
    *Out of scope*).
13. **Topic filter** — a topic selector in the header, driven by a `topicId` query param so a
    filtered view is linkable. The source chips compose with it; both filters are passed
    server-side to `listSignals({ topicId, source })` rather than filtered in the client.
14. **Real stats** — `RADAR_STATS` is four hardcoded tiles. Three are derivable from the
    signal list and one is not:

    | Tile | Derived from |
    |---|---|
    | New signals (24h) | count of `createdAt` within 24h — *first* sightings, so a re-poll does not inflate it |
    | Sources active | distinct `source` values present in the list |
    | Avg match score | mean `score` |
    | ~~Drafted today~~ | **drop it** — needs drafts carrying a `signalId`, blocked with task 15 |

    Drop the deltas (`+6`, `all up`) with it: there is no stored previous run to compare
    against. Inventing numbers on a live page is worse than showing three tiles.
15. **"Draft a post" action** — the primary action per signal card, creating a `DRAFT` from
    the signal and routing to Review. **Blocked on two things** — see below.

## Phase 5 — Cleanup

16. **Delete `lib/mock-data.ts`.** After Phases 2 and 4 the file holds only `SIGNALS`,
    `RADAR_STATS`, `SOURCE_CHIPS`, and `SOURCE_META`, all of which are then dead. Confirm with
    a grep that nothing imports it.

---

## Blocked on backend — do not start until the endpoint exists

- ~~**Everything in Phases 1, 3, and 4** depends on `services/api/RADAR_PLAN.md` Phase 1.~~
  **Fully cleared** — both halves are delivered. The poller reads `GET /topics/queries` and
  stamps every signal with its topic, so a topic-filtered view returns real rows. Nothing in
  Phases 1–4 is waiting on the backend any more, except task 15 below.
- **Task 15, "Draft a post" from a signal — blocked twice over.** Both have to clear:
  1. `drafting/` is still an empty package, so there is nothing that turns a signal into
     draft text.
  2. `CreateDraftCommand` is `(accountId, platform, content)` — **it has no `signalId`
     field**, even though the `drafts.signal_id` column exists (`V7__signals.sql`) and
     `DraftResponse.signalId` is already returned. So even a hand-written draft cannot record
     which signal it came from. This is a small, independent API change and is worth asking
     for separately from the drafting slice: it unblocks the "Drafted today" tile in task 14
     and the provenance line in `DraftDetail.tsx:136`.

  Until both land the signal card's action is inert, exactly as the Regenerate button is on
  the Review page today.

## Deferred — the reply loop (next iteration)

Recorded so the shape is known, but **not built now**. Depends on `services/api/RADAR_PLAN.md`
§D1–D3.

- **A kind filter on Radar.** Both loops share the page — one chip row for *kind* ("To write
  about" / "To reply to"), the existing row for *source*. What differs is the card's primary
  action: `Draft a post` versus `Draft a reply`. That difference is what keeps two loops
  legible on one screen, and is why this does not need a second page.
- **`components/draft/ParentPostCard.tsx`** — a reply draft cannot be reviewed without seeing
  what it replies to. A quoted parent block (author, body, engagement, link out) above the
  draft body in `DraftDetail`. Its own file: `DraftDetail` is already at the size limit
  `CLAUDE.md` sets, and `DraftDetail.tsx:136` currently renders provenance as the bare string
  `"from a trend signal"`, which is the placeholder this replaces.
- **A reply affordance in the Review list** — so reply drafts are distinguishable before you
  click one.
- **Account scoping on Radar.** Content signals are account-agnostic — a Hacker News story is
  not tied to any account — but a platform post is repliable only *from* an account on that
  platform. The selected account should filter platform signals while leaving content signals
  alone. Decide it explicitly; silently hiding signals is the confusing outcome.

## Explicitly out of scope for the panel

- **Triggering a radar run.** The Refresh button re-fetches stored signals; it does not make
  the Go poller run. Polling is on `RADAR_INTERVAL` (default 1h) and, in production, on
  EventBridge. A manual-trigger endpoint is a real feature, but it belongs to the worker's
  plan, not this one.
- **Feed management** (`services/api/RADAR_PLAN.md` Phase 3, RSS). Only reachable if the RSS
  escape hatch is built, and it would be a near-copy of the Topics page.

## Suggested order

Phase 1 (tasks 1→6) → Phase 2 (7) → Phase 3 (8→10) → Phase 4 (11→14; 15 stays inert) →
Phase 5 (16).

Phase 2 before Phase 4 deliberately: extracting `PLATFORM_META` first means the Radar rewiring
is one focused diff instead of a rename tangled through six unrelated components. Topics
before Radar because the Radar page is not useful, and barely testable, until there is
something to filter by.
