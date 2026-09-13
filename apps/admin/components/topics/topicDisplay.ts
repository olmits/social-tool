// Topic-form copy: what to type into a source's query box. Kept out of the form
// hook so the hook is logic only, and out of lib/api/mappers.ts because this is
// authoring guidance, not a mapping of anything the API returns.

import type { SignalSource } from "@/lib/api/types";

/** Per-source placeholder, in that source's own query dialect. */
export const SOURCE_QUERY_PLACEHOLDERS: Record<SignalSource, string> = {
  HACKER_NEWS: "local-first OR CRDT",
  DEVTO: "react",
  GITHUB_TRENDING: "rust",
  REDDIT: "r/programming",
  PRODUCT_HUNT: "developer tools",
};

/** Backend limits (Topic.MAX_NAME_LENGTH / MAX_QUERY_LENGTH) — mirrored so the form rejects first. */
export const TOPIC_NAME_MAX = 120;
export const TOPIC_QUERY_MAX = 255;
