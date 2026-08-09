# Drafts — frontend implementation plan (admin panel)

Plan for wiring the draft review/scheduling UI to the Java core API. The Review and
Schedule pages exist visually but run entirely on mock data (`lib/mock-data.ts`); no
drafts API layer exists yet. This plan makes them real, following the **accounts feature
as the reference pattern** (`lib/api/{client,accounts,types,mappers,actions}.ts`).

See root `PLAN.md` for product intent, `services/api` for the backend, and
`services/api/DEFERRED.md` for backend gaps that block parts of this work.

## Backend endpoints available now

- `GET /drafts?accountId=&status=` — list (both filters optional)
- `GET /drafts/{id}` — single draft
- `POST /drafts` — create (`accountId`, `platform`, `content`) → `DRAFT`
- `PATCH /drafts/{id}/approve` → `APPROVED` (422 if affiliate links without disclosure)
- `PATCH /drafts/{id}/schedule` (`scheduledAt`) → `SCHEDULED`
- `PATCH /drafts/{id}/published`, `PATCH /drafts/{id}/failed` — **Go-worker callbacks, not panel actions**

Draft statuses are UPPERCASE (`DRAFT | APPROVED | SCHEDULED | PUBLISHED | FAILED`).

## Conventions to follow

- Server-only fetch via `apiFetch` (`lib/api/client.ts`); `X-API-Key` never reaches the browser.
- One typed function per endpoint in `lib/api/drafts.ts`, tag-cached with a `DRAFTS_TAG`.
- Mutations are `"use server"` actions returning the `ActionResult` discriminated union,
  calling `updateTag(DRAFTS_TAG)` for read-your-writes.
- Components consume `DraftResponse` directly — no `UiDraft` reshape (it would be a near-copy).
  Trivial values (char count, affiliate presence) are derived inline; only non-trivial display
  lookups (per-platform char limit, status label) live alongside the account helpers in
  `lib/api/mappers.ts`. Keep backend UPPERCASE enums in `types.ts` and format for display at the edge.
- Component/hook conventions per `apps/admin/CLAUDE.md` (small components, logic in hooks,
  `PascalCase` components / `camelCase` hooks, `useController` fields).

---

## Phase 1 — Data / API layer (foundation)

1. **Draft API types** — add `DraftResponse`, `CreateDraftCommand`, `ScheduleDraftCommand`,
   and a `DraftStatus` union to `lib/api/types.ts`, mirroring the Java DTOs.
2. **Draft data layer** — `lib/api/drafts.ts`: `listDrafts(accountId?, status?)`, `getDraft(id)`,
   `createDraft`, `approveDraft`, `scheduleDraft`; export `DRAFTS_TAG` and tag the reads.
3. **Draft display helpers** — add to `lib/api/mappers.ts` (alongside the account helpers) the
   non-trivial display lookups only: per-platform char `limit` constant + `charLimit()`, and
   `statusLabel()`. No `UiDraft` reshape; components read `DraftResponse` and derive char count /
   affiliate presence inline. Backend statuses stay UPPERCASE; reconcile the mock's lowercase
   when wiring the pages.
4. **Draft server actions** — `createDraftAction`, `approveDraftAction`, `scheduleDraftAction`
   in a draft actions module: wrap the calls, return `ActionResult`, `updateTag(DRAFTS_TAG)`,
   and map 400/409/422 to user-facing messages.

## Phase 2 — Wire the Review page

5. **Review list from API** — replace the `DRAFTS` mock with `listDrafts(selectedAccountId, filter)`;
   drive the status tabs from the `status` query param; scope by the selected account
   (`account-context`). Add loading / empty / error states.
6. **Review approve + schedule actions** — wire the Approve button to `approveDraftAction` and the
   Schedule button to `scheduleDraftAction` (with a date/time picker for `scheduledAt`); surface
   the 422 disclosure error; refresh via cache tag on success.

## Phase 3 — Wire the Schedule page

7. **Schedule calendar from API** — replace the `WEEK` mock with `listDrafts(status="SCHEDULED")`,
   grouped by `scheduledAt` into the week grid; keep the platform/status legend.

## Phase 4 — Create / compose a draft

8. **Manual compose flow** — a form to create a draft (`accountId` from selected account, `platform`,
   `content`) via `createDraftAction`, landing it in the Review list as `DRAFT`.

---

## Phase 5 — Edit and discard (done)

9. **Edit a draft** — `EditDraftDialog` + `useEditDraftForm` over `PATCH /drafts/{id}`, editing
   content, affiliate link, and the disclosure toggle. Editing an `APPROVED` draft reverts it to
   `DRAFT`; the dialog warns when an affiliate link has no disclosure (saving is allowed, only
   approving is gated).
10. **Discard a draft** — `DiscardDraftDialog` over `PATCH /drafts/{id}/discard`. Soft: the draft
    becomes `DISCARDED` and drops out of the "All" tab, still reachable under its own filter.

---

## Blocked on backend — do not start until the endpoint exists

- **AI generate / regenerate** (the "AI generated" badge, the inert Regenerate button, voice
  profile) — needs the drafting slice (`DraftingService` + a generate endpoint). `POST /drafts`
  only takes manual content today.

## Explicitly out of scope for the panel

- `markPublished` / `markFailed` are **Go-worker callbacks**. The panel only *reads*
  `PUBLISHED` / `FAILED` status; it never triggers these transitions.

## Suggested order

Phase 1 (tasks 1→4, sequential) → Review (5→6) → Schedule (7) → Compose (8) → Edit/discard (9→10).
Phase 1 unblocks everything; Review is the core loop and highest value. All done — the only
remaining work is AI drafting, which is blocked on the backend slice.
