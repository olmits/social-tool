// Review-board-specific constants: the status filter tabs (backend UPPERCASE
// statuses plus an "ALL" pseudo-value). Draft-centric display helpers live in
// components/draft/draftDisplay.ts.

export const REVIEW_STATUS_TABS = [
  "ALL",
  "DRAFT",
  "APPROVED",
  "SCHEDULED",
  "PUBLISHED",
  "FAILED",
] as const;
export type ReviewFilter = (typeof REVIEW_STATUS_TABS)[number];
