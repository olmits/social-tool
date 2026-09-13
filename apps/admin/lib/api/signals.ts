import { apiFetch } from "./client";
import type { SignalResponse, SignalSource } from "./types";

/** Cache tag for all signal reads. Invalidate via `updateTag` to re-read the store. */
export const SIGNALS_TAG = "signals";

export interface ListSignalsFilters {
  topicId?: string;
  source?: SignalSource;
}

/**
 * `GET /signals`, ranked by score and optionally narrowed by topic and/or source
 * (both filters are optional, and compose). Tag-cached for revalidation.
 *
 * Returns nothing at all in an environment with no topics: the poller works from
 * `GET /topics/queries`, so no topic means no signal was ever stored.
 */
export function listSignals(
  filters: ListSignalsFilters = {},
): Promise<SignalResponse[]> {
  const params = new URLSearchParams();
  if (filters.topicId) {
    params.set("topicId", filters.topicId);
  }
  if (filters.source) {
    params.set("source", filters.source);
  }
  const query = params.toString();
  return apiFetch<SignalResponse[]>(`/signals${query ? `?${query}` : ""}`, {
    next: { tags: [SIGNALS_TAG] },
  });
}
