// Radar-specific derivations: the age badge, the header's "updated" line, and the
// stat tiles. Per-source display lookups (label, badge, engagement unit) live in
// lib/api/mappers.ts, the same split drafts use between draftDisplay and mappers.
//
// Every function takes `now` rather than reading the clock. These render on the
// server and hydrate on the client, and a relative time that reads the clock twice
// can disagree between the two ("59m" against "1h"); one `now`, taken once per
// request and passed down, is the same number in both places.

import type { SignalResponse } from "@/lib/api/types";

const MINUTE = 60_000;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;

function elapsed(iso: string, now: number): number {
  return Math.max(0, now - new Date(iso).getTime());
}

function shortDuration(ms: number): string {
  if (ms < MINUTE) return "now";
  if (ms < HOUR) return `${Math.floor(ms / MINUTE)}m`;
  if (ms < DAY) return `${Math.floor(ms / HOUR)}h`;
  return `${Math.floor(ms / DAY)}d`;
}

/**
 * How long ago the signal was **first** seen, from `createdAt`.
 *
 * Not `fetchedAt`: `SignalService.ingest` refreshes that on every re-poll, so a
 * story that has sat on the front page for two days would report an age of
 * minutes. `createdAt` is the first-sighting timestamp this badge means.
 */
export function signalAge(signal: SignalResponse, now: number): string {
  return shortDuration(elapsed(signal.createdAt, now));
}

/**
 * When the radar last saw any of these signals — `max(fetchedAt)`, which is
 * exactly what *last seen* is good for. Null for an empty list, where there is
 * nothing to have been updated.
 */
export function lastUpdatedLabel(
  signals: SignalResponse[],
  now: number,
): string | null {
  if (signals.length === 0) return null;
  const latest = Math.min(
    ...signals.map((signal) => elapsed(signal.fetchedAt, now)),
  );
  return `updated ${shortDuration(latest)} ago`;
}

export interface RadarStat {
  label: string;
  value: string;
}

/**
 * The three tiles above the list. There are no deltas: nothing stores a previous
 * run to compare against, and inventing a "+6" on a live page is worse than
 * showing the number alone.
 *
 * "Drafted today" is deliberately absent — it needs drafts that carry a
 * `signalId`, and `CreateDraftCommand` has no such field yet (RADAR_PLAN task 15).
 */
export function radarStats(
  signals: SignalResponse[],
  now: number,
): RadarStat[] {
  // First sightings, so a re-poll of the same story doesn't inflate the count.
  const fresh = signals.filter(
    (signal) => elapsed(signal.createdAt, now) < DAY,
  ).length;
  const sources = new Set(signals.map((signal) => signal.source)).size;
  const avg =
    signals.length === 0
      ? 0
      : Math.round(
          signals.reduce((total, signal) => total + signal.score, 0) /
            signals.length,
        );

  return [
    { label: "New signals (24h)", value: String(fresh) },
    { label: "Sources active", value: String(sources) },
    { label: "Avg match score", value: `${avg}%` },
  ];
}

export type RadarEmptyReason = "no-topics" | "no-signals" | "filtered";

/**
 * Why the list came back empty. The order matters: anything the server did return
 * means the search box is what emptied the view, whatever the topic list looks
 * like — a install with legacy signals but no topics would otherwise be told to
 * add a topic when all it did was mistype a search.
 *
 * @param fetched     signals the server returned for the current filters
 * @param topicScoped the same, minus the source filter — empty means the topic has
 *                    nothing at all, rather than nothing on the chosen source
 * @param topicCount  topics configured; none means the radar polls nothing, by design
 */
export function radarEmptyReason(
  fetched: SignalResponse[],
  topicScoped: SignalResponse[],
  topicCount: number,
): RadarEmptyReason {
  if (fetched.length > 0) return "filtered";
  if (topicCount === 0) return "no-topics";
  if (topicScoped.length === 0) return "no-signals";
  return "filtered";
}
