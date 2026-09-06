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

Available now:

- `GET /signals?source=` — stored signals, ranked by score

Arriving with `services/api/RADAR_PLAN.md` Phase 1, and required by everything here:

- `GET /topics`, `POST /topics`, `PATCH /topics/{id}`, `DELETE /topics/{id}`
- `GET /signals?topicId=&source=` — the `topicId` filter
- `signals.topic` renamed to `signals.title`, plus a nullable `topicId`

`SignalSource` values are UPPERCASE (`HACKER_NEWS | DEVTO | GITHUB_TRENDING | REDDIT |
PRODUCT_HUNT`), like every other backend enum.

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

## A naming note, before anything else

The mock already uses the vocabulary the backend is moving to:

```ts
{ source: "Hacker News", title: "Show HN: …", topic: "Local-first", match: 92 }
```

`title` is the headline; `topic` is the **category**; `match` is per-topic relevance. The
backend's `signals.topic` column currently holds the headline, which collides with all of
this — hence the rename in the API plan. **The UI does not need to adapt to the backend here;
the backend is adapting to the UI.** Keep the mock's vocabulary when wiring the real data, and
`match` becomes the per-topic normalized `score`.

---

## Phase 1 — Data / API layer (foundation)

1. **Types** — add `SignalResponse`, `SignalSource`, `TopicResponse`, `TopicQuery`, and the
   command types to `lib/api/types.ts`, mirroring the Java DTOs.
2. **Topics data layer** — `lib/api/topics.ts`: `listTopics()`, `createTopic`, `updateTopic`,
   `deleteTopic`; export `TOPICS_TAG` and tag the reads.
3. **Signals data layer** — `lib/api/signals.ts`: `listSignals({ topicId?, source? })`;
   export `SIGNALS_TAG`.
4. **Server actions** — `createTopicAction`, `updateTopicAction`, `deleteTopicAction` in a
   topic actions module: wrap the calls, return `ActionResult`, `updateTag(TOPICS_TAG)`, and
   map 400/409 to user-facing messages (409 is a duplicate topic name).
5. **Display helpers** — add `sourceLabel()` and per-source badge metadata to
   `lib/api/mappers.ts`, alongside the existing `statusLabel` / `charLimit`.

## Phase 2 — Extract `PLATFORM_META` out of the mocks

6. **Move `PLATFORM_META` to `lib/api/mappers.ts`.** This is display metadata, not fixture
   data, and it is imported by six *real* components — `sidebar`, `ReviewBoard`, `EventCard`,
   `ScheduleCalendar`, `PlatformPicker`, `DetailHeader`. It has to move before
   `lib/mock-data.ts` can be deleted, and doing it as its own step keeps the Radar rewiring
   diff readable.

   `SOURCE_META` gets the same treatment, keyed by the UPPERCASE `SignalSource` rather than
   the mock's display strings.

## Phase 3 — Topics page

7. **Route + nav** — `app/(app)/topics/page.tsx`, and a fourth `sidebar.tsx` entry placed
   *above* Trend Radar, since topics configure it.
8. **`components/topics/`** — `TopicList` (rows with name, per-source queries, enabled
   toggle), `TopicDialog` (create/edit), `useTopicForm` (`useController` fields, per the
   `connect-account/` reference implementation). Delete behind a confirm dialog, matching
   `DiscardDraftDialog`.
9. **Empty state that teaches** — an empty Topics list is the first thing a new install shows,
   and the radar returns nothing until a topic exists. The empty state should say so and link
   straight to the create dialog.

## Phase 4 — Radar page off mocks

10. **Signals from the API** — replace the `SIGNALS` mock with `listSignals(...)`, keep the
    existing layout (stats row, search, source chips, table/cards toggle). Add loading, empty,
    and error states; the page currently has none because mock data cannot fail.
11. **Topic filter** — a topic selector in the header, driven by a `topicId` query param so a
    filtered view is linkable. `SOURCE_CHIPS` stays as-is and composes with it.
12. **Real stats** — `RADAR_STATS` is four hardcoded tiles. Derive them from the signal list
    (new since last run, topics active, drafted) or drop the tiles that have no real source
    behind them. Inventing numbers in a live page is worse than showing three tiles.
13. **"Draft a post" action** — the primary action per signal card, creating a `DRAFT` via the
    drafting slice and routing to Review. **Blocked** — see below.

## Phase 5 — Cleanup

14. **Delete `lib/mock-data.ts`.** After Phases 2 and 4 the file holds only `SIGNALS`,
    `RADAR_STATS`, `SOURCE_CHIPS`, and `SOURCE_META`, all of which are then dead. Confirm with
    a grep that nothing imports it.

---

## Blocked on backend — do not start until the endpoint exists

- **Everything in Phases 1, 3, and 4** depends on `services/api/RADAR_PLAN.md` Phase 1
  (`/topics` and the `topicId` filter). `GET /signals` alone is not enough to build against —
  without topics the page has nothing to filter by and no `match` to rank on.
- **Task 13, "Draft a post" from a signal** — needs the drafting slice (`drafting/` is still
  an empty package). `POST /drafts` takes manual content only, so a signal cannot yet become a
  draft. Until then the signal card's action is inert, exactly as the Regenerate button is on
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

Phase 1 (tasks 1→5) → Phase 2 (6) → Phase 3 (7→9) → Phase 4 (10→12; 13 stays inert) →
Phase 5 (14).

Phase 2 before Phase 4 deliberately: extracting `PLATFORM_META` first means the Radar rewiring
is one focused diff instead of a rename tangled through six unrelated components. Topics
before Radar because the Radar page is not useful, and barely testable, until there is
something to filter by.
