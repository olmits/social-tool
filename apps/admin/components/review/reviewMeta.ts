// Review-board-specific constants: the status filter tabs (backend UPPERCASE
// statuses plus an "ALL" pseudo-value). Draft-centric display helpers live in
// components/draft/draftDisplay.ts.

import type { DraftStatus } from "@/lib/api/types";

export const REVIEW_STATUS_TABS = [
  "ALL",
  "DRAFT",
  "APPROVED",
  "SCHEDULED",
  "PUBLISHED",
  "FAILED",
  "DISCARDED",
] as const;
export type ReviewFilter = (typeof REVIEW_STATUS_TABS)[number];

/**
 * Discarded drafts are excluded from "All": that tab is the working queue, and a
 * rejected draft is done with. They stay reachable under their own tab.
 */
export const HIDDEN_FROM_ALL: DraftStatus = "DISCARDED";
