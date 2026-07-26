// Display + shape mappers for accounts and drafts. Client-safe (no server-only
// imports) so client components can consume these and format at the call site.

import type { Platform } from "@/lib/types";
import type { AccountResponse, AccountStatus, DraftStatus } from "./types";

/**
 * Account shape consumed by the UI. `platform` matches the backend enum
 * (UPPERCASE). No display label is stored here — derive it where you render, via
 * {@link accountLabel} / {@link platformLabel}.
 */
export interface UiAccount {
  id: string;
  platform: Platform;
  /** Bare handle from the API (no `@`). */
  handle: string;
  instance: string | null;
  status: AccountStatus;
}

const PLATFORM_LABELS: Record<Platform, string> = {
  BLUESKY: "Bluesky",
  MASTODON: "Mastodon",
  REDDIT: "Reddit",
};

/** Human-friendly platform name for display, e.g. `"BLUESKY"` → `"Bluesky"`. */
export function platformLabel(platform: Platform): string {
  return PLATFORM_LABELS[platform];
}

/**
 * Display handle with the platform-appropriate prefix, e.g. `@me.bsky.social`,
 * `@me@fosstodon.org`, `u/me`. The `@` lives here, not in stored state.
 */
export function accountLabel(account: UiAccount): string {
  const { platform, handle, instance } = account;
  switch (platform) {
    case "MASTODON":
      return instance ? `@${handle}@${instance}` : `@${handle}`;
    case "REDDIT":
      return `u/${handle}`;
    default:
      return `@${handle}`;
  }
}

export function toUiAccount(dto: AccountResponse): UiAccount {
  return {
    id: dto.id,
    platform: dto.platform,
    handle: dto.handle,
    instance: dto.instance,
    status: dto.status,
  };
}

// --- Drafts ---------------------------------------------------------------
// Components consume `DraftResponse` directly (no reshape) and derive trivial
// values inline (char count `content.length`, affiliate presence
// `Boolean(affiliateLinks?.trim())`); only the non-trivial display lookups live
// here.

/** Per-platform character limit, for the length meter in review/compose. */
export const PLATFORM_CHAR_LIMIT: Record<Platform, number> = {
  BLUESKY: 300,
  MASTODON: 500,
  REDDIT: 40000,
};

/** Character limit for a draft's target platform. */
export function charLimit(platform: Platform): number {
  return PLATFORM_CHAR_LIMIT[platform];
}

const DRAFT_STATUS_LABELS: Record<DraftStatus, string> = {
  DRAFT: "Draft",
  APPROVED: "Approved",
  SCHEDULED: "Scheduled",
  PUBLISHED: "Published",
  FAILED: "Failed",
};

/** Human-friendly status label, e.g. `"DRAFT"` → `"Draft"`. */
export function statusLabel(status: DraftStatus): string {
  return DRAFT_STATUS_LABELS[status];
}
