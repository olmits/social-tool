// Pure week/day helpers for the schedule calendar. Everything is computed in UTC
// so server-rendered and client-hydrated output match (no timezone mismatch);
// times therefore display as UTC. Localizing is a follow-up.

import type { DraftResponse } from "@/lib/api/types";

export interface CalendarDay {
  /** Midnight UTC for this day. */
  date: Date;
  /** Abbreviated weekday, e.g. "Mon". */
  dow: string;
  /** Day of month, e.g. 13. */
  dayOfMonth: number;
  /** Scheduled drafts due this day, ascending by time. */
  drafts: DraftResponse[];
}

const DOW = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
const MS_PER_DAY = 86_400_000;

/** Midnight UTC of the Monday on or before `base`. */
export function mondayOfWeekUtc(base: Date): Date {
  const day = new Date(
    Date.UTC(base.getUTCFullYear(), base.getUTCMonth(), base.getUTCDate()),
  );
  const weekday = day.getUTCDay(); // 0 = Sun … 6 = Sat
  day.setUTCDate(day.getUTCDate() + (weekday === 0 ? -6 : 1 - weekday));
  return day;
}

export function addDaysUtc(date: Date, days: number): Date {
  const next = new Date(date);
  next.setUTCDate(next.getUTCDate() + days);
  return next;
}

/** The 7 days of the week starting at `weekStart`, with drafts grouped in. */
export function buildWeek(
  weekStart: Date,
  drafts: DraftResponse[],
): CalendarDay[] {
  const days: CalendarDay[] = Array.from({ length: 7 }, (_, i) => {
    const date = addDaysUtc(weekStart, i);
    return {
      date,
      dow: DOW[date.getUTCDay()],
      dayOfMonth: date.getUTCDate(),
      drafts: [],
    };
  });

  const weekEnd = addDaysUtc(weekStart, 7);
  const scheduled = drafts
    .filter((draft) => draft.scheduledAt)
    .map((draft) => ({ draft, at: new Date(draft.scheduledAt as string) }))
    .filter(({ at }) => at >= weekStart && at < weekEnd)
    .sort((a, b) => a.at.getTime() - b.at.getTime());

  for (const { draft, at } of scheduled) {
    const dayStart = Date.UTC(
      at.getUTCFullYear(),
      at.getUTCMonth(),
      at.getUTCDate(),
    );
    const index = Math.floor((dayStart - weekStart.getTime()) / MS_PER_DAY);
    days[index]?.drafts.push(draft);
  }

  return days;
}

const DATE_OPTS: Intl.DateTimeFormatOptions = {
  month: "short",
  day: "numeric",
  timeZone: "UTC",
};

/** e.g. "Jul 13 – 19, 2026". */
export function formatWeekLabel(weekStart: Date): string {
  const end = addDaysUtc(weekStart, 6);
  const start = weekStart.toLocaleDateString("en-US", DATE_OPTS);
  const endDay = end.getUTCDate();
  return `${start} – ${endDay}, ${end.getUTCFullYear()}`;
}

/** e.g. "Jul 13". */
export function formatDayLabel(date: Date): string {
  return date.toLocaleDateString("en-US", DATE_OPTS);
}

/** e.g. "9:00 AM" (UTC). */
export function formatTimeUtc(iso: string): string {
  return new Date(iso).toLocaleTimeString("en-US", {
    hour: "numeric",
    minute: "2-digit",
    timeZone: "UTC",
  });
}
