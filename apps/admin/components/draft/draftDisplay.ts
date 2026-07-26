// Draft-centric display metadata + derivations, keyed by the backend's UPPERCASE
// DraftStatus. Shared by the draft detail components and the review board.

import { charLimit } from "@/lib/api/mappers";
import type { DraftResponse, DraftStatus } from "@/lib/api/types";

export interface StatusMeta {
  badgeBg: string;
  badgeText: string;
  dot: string;
}

export const STATUS_META: Record<DraftStatus, StatusMeta> = {
  DRAFT: {
    badgeBg: "bg-muted",
    badgeText: "text-muted-foreground",
    dot: "bg-neutral-400",
  },
  APPROVED: {
    badgeBg: "bg-green-50 dark:bg-green-950/40",
    badgeText: "text-green-700 dark:text-green-400",
    dot: "bg-green-500",
  },
  SCHEDULED: {
    badgeBg: "bg-blue-50 dark:bg-blue-950/40",
    badgeText: "text-blue-700 dark:text-blue-400",
    dot: "bg-blue-500",
  },
  PUBLISHED: {
    badgeBg: "bg-violet-50 dark:bg-violet-950/40",
    badgeText: "text-violet-700 dark:text-violet-400",
    dot: "bg-violet-500",
  },
  FAILED: {
    badgeBg: "bg-red-50 dark:bg-red-950/40",
    badgeText: "text-red-700 dark:text-red-400",
    dot: "bg-red-500",
  },
};

export const STATE_MACHINE_STEPS = [
  "DRAFT",
  "APPROVED",
  "SCHEDULED",
  "PUBLISHED",
] as const satisfies DraftStatus[];
export type StateStep = (typeof STATE_MACHINE_STEPS)[number];

export function draftChars(draft: DraftResponse): number {
  return draft.content.length;
}

export function draftLimit(draft: DraftResponse): number {
  return charLimit(draft.platform);
}

export function draftHasAffiliate(draft: DraftResponse): boolean {
  return Boolean(draft.affiliateLinks?.trim());
}

// Fixed locale + UTC so server-rendered and client-hydrated output match (no
// hydration mismatch from the viewer's locale/timezone).
const DATE_FORMAT: Intl.DateTimeFormatOptions = {
  month: "short",
  day: "numeric",
  hour: "numeric",
  minute: "2-digit",
  timeZone: "UTC",
};

function formatUtc(iso: string): string {
  return `${new Date(iso).toLocaleString("en-US", DATE_FORMAT)} UTC`;
}

/** Short subtitle for a draft in the list, derived from its status + timestamps. */
export function draftMeta(draft: DraftResponse): string {
  switch (draft.status) {
    case "SCHEDULED":
      return draft.scheduledAt
        ? `scheduled ${formatUtc(draft.scheduledAt)}`
        : "scheduled";
    case "PUBLISHED":
      return `published ${formatUtc(draft.updatedAt)}`;
    case "FAILED":
      return draft.failureReason ? `failed · ${draft.failureReason}` : "failed";
    case "APPROVED":
      return `approved ${formatUtc(draft.updatedAt)}`;
    default:
      return `edited ${formatUtc(draft.updatedAt)}`;
  }
}
