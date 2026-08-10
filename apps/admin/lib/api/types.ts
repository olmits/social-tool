// TypeScript mirrors of the Java core API DTOs (services/api). `Platform` shares
// the backend's UPPERCASE enum values, so it flows through untouched — see
// lib/api/mappers.ts for display formatting only.

import type { Platform } from "@/lib/types";

export type AccountStatus = "ACTIVE" | "DISCONNECTED";

export interface AccountResponse {
  id: string;
  platform: Platform;
  /** Bare handle, no leading `@` (e.g. "me.bsky.social"). */
  handle: string;
  /** Present for MASTODON, null otherwise. */
  instance: string | null;
  status: AccountStatus;
  /**
   * Reference to the account's entry in the credential store — not the credential itself.
   * Exposed for the Go publisher, which resolves it at publish time. The panel must not
   * render or use this.
   */
  credentialRef: string;
  createdAt: string;
  updatedAt: string;
}

export interface ConnectAccountCommand {
  platform: Platform;
  handle: string;
  /** Raw secret (app password / token). The API stores it and never returns it. */
  credentialValue: string;
  /** Required for MASTODON, must be null otherwise (API returns 400 if wrong). */
  instance: string | null;
}

/**
 * Draft lifecycle state. Matches the backend enum (UPPERCASE) and is the state
 * machine that drives the pipeline: `DRAFT → APPROVED → SCHEDULED → PUBLISHED`,
 * with `FAILED` as the publish-error branch and `DISCARDED` as the terminal state
 * for a draft rejected during review.
 */
export type DraftStatus =
  | "DRAFT"
  | "APPROVED"
  | "SCHEDULED"
  | "PUBLISHED"
  | "FAILED"
  | "DISCARDED";

export interface DraftResponse {
  id: string;
  accountId: string;
  /** Source signal (trend radar). Null when authored from a free-form topic. */
  signalId: string | null;
  platform: Platform;
  content: string;
  /** Affiliate link(s) attached to the post; null when none. */
  affiliateLinks: string | null;
  status: DraftStatus;
  aiGenerated: boolean;
  disclosureIncluded: boolean;
  /** ISO-8601 instant the draft is due; set once SCHEDULED, null before. */
  scheduledAt: string | null;
  /** Platform's remote post id; set once PUBLISHED, null before. */
  remoteId: string | null;
  /** Reason the publish failed; set once FAILED, null otherwise. */
  failureReason: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateDraftCommand {
  accountId: string;
  platform: Platform;
  content: string;
}

/**
 * The editable body of a draft. A **full replace**, not a sparse patch: a null
 * `affiliateLinks` clears them rather than leaving them unchanged, so always send
 * all three fields.
 */
export interface EditDraftCommand {
  content: string;
  affiliateLinks: string | null;
  disclosureIncluded: boolean;
}

export interface ScheduleDraftCommand {
  /** ISO-8601 instant the draft should publish (e.g. "2026-08-01T12:00:00Z"). */
  scheduledAt: string;
}

/** Shape of the API's error body: `{ "message": string }`. */
export interface ApiErrorBody {
  message: string;
}
