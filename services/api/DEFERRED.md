# Deferred work — social-api

Interim decisions made while building the drafting/state-machine slice. Each was a
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

## 2. `DraftService.create` does not validate the account

**Current state.** `create(accountId, platform, content)` saves a draft without checking that
the account exists or is still connected (`ACTIVE`). The `fk_drafts_account` foreign key rejects
a non-existent `accountId`, but surfaces as a generic 500 rather than a clean error, and does
**not** catch a *disconnected* account at all.

**Why this is interim.** A proper check means `DraftService` loading the account and inspecting
its status, which couples the `draft` slice to the `account` slice. We've deliberately kept
these decoupled (reference other aggregates by id, don't hold object references). The natural
place for the check is the request/validation layer, which doesn't exist yet.

**Follow-up (when the `POST /drafts` endpoint lands).**
- Validate at the controller/service boundary that the target account exists and is `ACTIVE`
  before creating a draft (e.g. via `AccountService.get`), returning a clean 404/409.
- Also validate that the draft's `platform` matches the account's `platform`.
- Decide where this lives so the slices stay decoupled (controller orchestration, or an
  explicit application service), rather than injecting the account repository into `DraftService`.

**Acceptance.** Creating a draft for a missing/disconnected account, or with a platform that
doesn't match the account, returns a clear error instead of a 500 or a silently-saved draft.
