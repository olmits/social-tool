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
  /**
   * The radar signal this draft was written about, or null when it came from a
   * free-form topic. When set it must name a real signal — the API answers 404
   * otherwise.
   */
  signalId: string | null;
  platform: Platform;
  content: string;
}

/**
 * A request to have Claude write a draft about a signal. Carries no content, by
 * definition, and no `aiGenerated` flag — reaching this endpoint is what makes a
 * draft AI-written.
 *
 * Slow by the standards of every other call here: the model reads the linked
 * article before writing, so budget tens of seconds and show a pending state.
 */
export interface GenerateDraftCommand {
  accountId: string;
  signalId: string;
  platform: Platform;
  /**
   * The voice to write in, or null to use the default profile. Null with no
   * default configured is a 409, not a silent neutral fallback.
   */
  voiceProfileId: string | null;
}

/** How the author sounds. Platform-agnostic: length is the platform's business. */
export interface VoiceProfileResponse {
  id: string;
  name: string;
  /** Free text, reaching the drafting system prompt verbatim. */
  instructions: string;
  enabled: boolean;
  /** Pre-selected in the generate dialog. At most one profile carries it. */
  isDefault: boolean;
  createdAt: string;
  updatedAt: string;
}

/** `enabled` and `isDefault` default to true / false when null. */
export interface CreateVoiceProfileCommand {
  name: string;
  instructions: string;
  enabled: boolean | null;
  isDefault: boolean | null;
}

/**
 * A **sparse** patch, like {@link UpdateTopicCommand} and unlike
 * {@link EditDraftCommand}: a null field is left unchanged. Setting `isDefault`
 * to true moves the default off whichever profile currently holds it.
 */
export interface UpdateVoiceProfileCommand {
  name?: string | null;
  instructions?: string | null;
  enabled?: boolean | null;
  isDefault?: boolean | null;
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

/**
 * A content source the trend radar polls. Mirrors the backend enum
 * (…/signal/model/SignalSource.java).
 *
 * `REDDIT` and `PRODUCT_HUNT` are declared but never polled — neither has a
 * `Source` implementation in the Go worker. Use {@link SIGNAL_SOURCES} in
 * lib/api/mappers.ts wherever the UI offers a choice, so the panel never shows a
 * filter or a query field that can only ever return nothing.
 */
export type SignalSource =
  | "HACKER_NEWS"
  | "DEVTO"
  | "GITHUB_TRENDING"
  | "REDDIT"
  | "PRODUCT_HUNT";

/**
 * Per-source search queries for a topic. A source absent from the map is not
 * polled for that topic, so this is a partial record, not a full one.
 */
export type TopicQueries = Partial<Record<SignalSource, string>>;

export interface TopicResponse {
  id: string;
  name: string;
  enabled: boolean;
  /** Ordered by source name server-side, so the list doesn't reshuffle per request. */
  queries: TopicQueries;
  createdAt: string;
  updatedAt: string;
}

export interface CreateTopicCommand {
  name: string;
  /** Null means enabled — a topic is created to be polled. */
  enabled: boolean | null;
  /** Null or empty creates a topic that is polled nowhere until queries are added. */
  queries: TopicQueries | null;
}

/**
 * A **sparse** patch, unlike {@link EditDraftCommand}: a null field is left
 * unchanged, so the enabled toggle sends `{ enabled: false }` alone rather than
 * resending a name and queries it never read.
 *
 * `queries` is replace-not-merge when present — a map of two entries leaves the
 * topic with exactly those two, and an empty map clears them.
 */
export interface UpdateTopicCommand {
  name?: string | null;
  enabled?: boolean | null;
  queries?: TopicQueries | null;
}

/**
 * A trend-radar signal: one item the poller found for a topic on a source.
 *
 * Note `fetchedAt` vs `createdAt` — `ingest` refreshes `fetchedAt` on every
 * re-poll, so it means *last seen*. A signal's age is `createdAt`, the
 * first-sighting timestamp.
 */
export interface SignalResponse {
  id: string;
  source: SignalSource;
  externalId: string;
  /** The item's headline. */
  title: string;
  /** Null for a signal polled before topics existed, or whose topic was deleted. */
  topicId: string | null;
  /** Resolved server-side, so the panel does no id→name join. Null exactly when `topicId` is. */
  topicName: string | null;
  url: string;
  /** Popularity normalized to 0-100 within its own `(topic, source)` batch — the "match". */
  score: number;
  /** The source's own count behind the score — points, reactions, stars. Unit differs per source. */
  nativeScore: number;
  /** Raw source JSON as a *string*. The panel does not consume it. */
  rawPayload: string;
  createdAt: string;
  fetchedAt: string;
}
