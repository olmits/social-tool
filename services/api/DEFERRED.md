# Deferred work — social-api

Interim decisions made while building the draft state machine and generation slices. Each was a
deliberate shortcut with a known follow-up, recorded here so a future session can pick
it up without re-deriving the context. See root `PLAN.md` for architecture intent.

---

## 1. `remote_id` lives on `drafts` temporarily — move it to a `posts` table

**Current state.** When a post is published, the platform returns a remote id (the post's
address on Bluesky/Mastodon/etc). `DraftService.markPublished(draftId, remoteId)` stores it
in `drafts.remote_id` (added in `V6__drafts_lifecycle_fields.sql`, alongside `scheduled_at`
and `failure_reason`).

**Why this is interim.** `PLAN.md` (data model) designates a dedicated `posts` table as the
home for published-post data: `posts | draft_id, account_id, platform, remote_id, published_at`.
A published post is conceptually a separate record from the draft. `remote_id` was parked on
the draft only so `markPublished` had somewhere to write it before that table existed.

**Follow-up.**
- Introduce the `posts` table + `Post` entity + `PostRepository` (likely with the
  scheduler/publisher slice, Phase 1).
- Change `markPublished` to create a `Post` row (`draft_id`, `account_id`, `platform`,
  `remote_id`, `published_at`) instead of writing `drafts.remote_id`.
- Drop `drafts.remote_id` in a new migration. Consider whether `published_at` also belongs
  on `posts` (currently the draft's `updated_at` implicitly captures publish time).

**Acceptance.** `markPublished` produces a `posts` row; `drafts` no longer carries `remote_id`.

---

## 2. A `SCHEDULED` draft cannot be un-scheduled — it has no way back

**Current state.** `DraftService` exposes no `SCHEDULED -> APPROVED` transition, and both
`edit` and `discard` reject `SCHEDULED` (they allow `DRAFT` and `APPROVED` only). A scheduled
draft's only exits are `markPublished` and `markFailed`, both of which are Go-publisher
callbacks. So once you schedule a draft, you cannot change its content, cancel it, or move its
publish time — and since the Go publisher does not exist yet, nothing moves it out of
`SCHEDULED` at all.

**Why this is interim.** Freezing a draft at `SCHEDULED` is deliberate and should stay:
approval attests to specific content (the disclosure gate in `approve()` would otherwise be
bypassable by editing an already-approved, already-queued draft), and once the publisher owns
the item, an in-place edit races with a worker that may have already read it. The gap is not
the freeze — it is that there is no *supported* way to take a draft back out of the queue
before that freeze applies. The transition was left out because the queue it would coordinate
with does not exist yet, so there was nothing to define "safe to reclaim" against.

**Follow-up.**
- Add `DraftService.unschedule(draftId)`: `SCHEDULED -> APPROVED`, clearing `scheduled_at`.
  Edit and discard then become reachable again through the rules already in place — no change
  to their allowed-status sets.
- Expose it as `PATCH /drafts/{id}/unschedule`, alongside the other transition routes.
- Wire the panel: an "Unschedule" action on a `SCHEDULED` draft (`DraftDetailActions` currently
  disables every button in that state), and consider making it the entry point for "reschedule"
  rather than adding a separate transition.
- **Once the publisher lands**, guard against reclaiming an item the worker has already
  claimed from SQS — otherwise unschedule races with publish and the post goes out anyway.
  Decide whether that is a status check, a claim/lease column, or a conditional queue delete.

**Acceptance.** A scheduled draft can be returned to `APPROVED` and then edited, discarded, or
re-scheduled; the transition cannot silently cancel a publish that is already in flight.

---

## 3. Draft generation leaves four small things behind

**Current state.** The `generation/` slice ships (`POST /drafts/generate`, `generation/README.md`).
Four deliberate omissions, none blocking:

- **`GET /signals/{id}` does not exist.** `SignalService.get` does, but it is a service method
  with no route. The panel therefore renders draft provenance as the bare string "from a trend
  signal" (`DraftDetail.tsx:136`) instead of the signal's title and link. Adding the route is
  small; the panel side wants its own component, since `DraftDetail` is already at the size
  limit `apps/admin/CLAUDE.md` sets.
- **The Regenerate button on the Review page is still inert** — a `<button>` with no
  `onClick` (`DraftDetail.tsx:102–121`). It can now call the same action the radar does, given
  a draft that carries a `signalId`.
- **`pause_turn` is not resumed.** With one permitted web fetch it is close to unreachable, and
  resuming means replaying the assistant turn, which cannot be tested without a recorded
  fixture. It currently fails loudly rather than returning a half-written post.
- **Nothing records which voice wrote a draft**, and the model's one-sentence `rationale` is
  logged at DEBUG and dropped. Both are a column on `drafts` if they turn out to matter.

**Acceptance.** Each is independently shippable; none is a precondition for the others.

---

## Resolved

- **Account validation on draft create** — `DraftCreationService` now checks the account
  exists, is `ACTIVE`, and matches the draft's platform before delegating to `DraftService`.
- **Draft edit endpoint** — `PATCH /drafts/{id}` replaces `content` / `affiliateLinks` /
  `disclosureIncluded` in `DRAFT` or `APPROVED` (editing an approved draft reverts it to
  `DRAFT`). The disclosure gate is now reachable over HTTP and covered by
  `DraftControllerIntegrationTest.approveWithAffiliateLinkAndNoDisclosureReturns422`.
- **`signalId` on draft creation** — `CreateDraftCommand` carries it, `DraftCreationService`
  validates it exists (a 404 rather than the 500 an FK violation produced), and
  `DraftService.create` no longer hard-codes null. The `drafts.signal_id` column has been
  there since `V7__signals.sql`; nothing could populate it until now.
- **AI drafting** — the `generation/` slice, `V10__voice_profiles.sql`, and the `voice/` slice.
  `PLAN.md` named a voice profile as one of drafting's three inputs; it is now a real entity
  rather than the only one of the three that did not exist.
