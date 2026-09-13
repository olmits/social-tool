// Display + shape mappers for accounts, drafts, and trend-radar signals.
// Client-safe (no server-only imports) so client components can consume these and
// format at the call site.

import type { Platform } from "@/lib/types";
import type {
  AccountResponse,
  AccountStatus,
  DraftStatus,
  SignalSource,
} from "./types";

/** Badge colours for a platform: the dot, and the chip's background/foreground. */
export interface PlatformMeta {
  dot: string;
  badgeBg: string;
  badgeText: string;
}

export const PLATFORM_META: Record<Platform, PlatformMeta> = {
  BLUESKY: {
    dot: "#0a7aff",
    badgeBg: "bg-blue-50 dark:bg-blue-950/40",
    badgeText: "text-blue-700 dark:text-blue-400",
  },
  MASTODON: {
    dot: "#6364ff",
    badgeBg: "bg-indigo-50 dark:bg-indigo-950/40",
    badgeText: "text-indigo-700 dark:text-indigo-400",
  },
  REDDIT: {
    dot: "#ff4500",
    badgeBg: "bg-orange-50 dark:bg-orange-950/40",
    badgeText: "text-orange-700 dark:text-orange-400",
  },
};

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
  DISCARDED: "Discarded",
};

/** Human-friendly status label, e.g. `"DRAFT"` → `"Draft"`. */
export function statusLabel(status: DraftStatus): string {
  return DRAFT_STATUS_LABELS[status];
}

// --- Signals (trend radar) ------------------------------------------------
// Components consume `SignalResponse` directly (no reshape). Only the per-source
// display lookups live here — the label, the badge colours, and the unit that
// makes `nativeScore` mean something.

const SIGNAL_SOURCE_LABELS: Record<SignalSource, string> = {
  HACKER_NEWS: "Hacker News",
  DEVTO: "dev.to",
  GITHUB_TRENDING: "GitHub",
  REDDIT: "Reddit",
  PRODUCT_HUNT: "Product Hunt",
};

/** Human-friendly source name, e.g. `"HACKER_NEWS"` → `"Hacker News"`. */
export function sourceLabel(source: SignalSource): string {
  return SIGNAL_SOURCE_LABELS[source];
}

/** Badge colours for a signal source — same shape as {@link PlatformMeta}. */
export type SourceMeta = PlatformMeta;

export const SOURCE_META: Record<SignalSource, SourceMeta> = {
  HACKER_NEWS: {
    dot: "#ff6600",
    badgeBg: "bg-orange-50 dark:bg-orange-950/40",
    badgeText: "text-orange-700 dark:text-orange-400",
  },
  DEVTO: {
    dot: "#0a0a0a",
    badgeBg: "bg-neutral-100 dark:bg-neutral-800",
    badgeText: "text-neutral-900 dark:text-neutral-100",
  },
  GITHUB_TRENDING: {
    dot: "#111827",
    badgeBg: "bg-neutral-100 dark:bg-neutral-800",
    badgeText: "text-neutral-900 dark:text-neutral-100",
  },
  REDDIT: {
    dot: "#ff4500",
    badgeBg: "bg-orange-50 dark:bg-orange-950/40",
    badgeText: "text-orange-700 dark:text-orange-400",
  },
  PRODUCT_HUNT: {
    dot: "#da552f",
    badgeBg: "bg-red-50 dark:bg-red-950/40",
    badgeText: "text-red-700 dark:text-red-400",
  },
};

/**
 * The sources actually polled — **not** every value of the enum. `REDDIT` and
 * `PRODUCT_HUNT` have no `Source` implementation in the Go worker
 * (services/workers/cmd/workers/poll.go), so filtering by them, or writing a
 * query for them, can only ever return nothing.
 *
 * Drive every source choice in the UI from this, and widen it when the worker
 * grows an implementation.
 */
export const SIGNAL_SOURCES: SignalSource[] = [
  "HACKER_NEWS",
  "DEVTO",
  "GITHUB_TRENDING",
];

/** Unit for a source's own count: points, reactions, stars. */
const SIGNAL_SOURCE_UNITS: Record<SignalSource, string> = {
  HACKER_NEWS: "pts",
  DEVTO: "reactions",
  GITHUB_TRENDING: "★",
  REDDIT: "↑",
  PRODUCT_HUNT: "↑",
};

// Fixed locale so server-rendered and client-hydrated output match (no hydration
// mismatch from the viewer's locale).
const COMPACT_NUMBER = new Intl.NumberFormat("en-US", {
  notation: "compact",
  maximumFractionDigits: 1,
});

/**
 * A signal's engagement, e.g. `"842 pts"`, `"456 reactions"`, `"1.2K ★"`. This is
 * the only place `nativeScore` is formatted: the number is meaningless without
 * the unit its source counts in.
 */
export function engagementLabel(
  source: SignalSource,
  nativeScore: number,
): string {
  return `${COMPACT_NUMBER.format(nativeScore)} ${SIGNAL_SOURCE_UNITS[source]}`;
}
