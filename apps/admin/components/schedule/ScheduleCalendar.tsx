"use client";

import { ChevronLeft, ChevronRight } from "lucide-react";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import type { DraftResponse } from "@/lib/api/types";
import { PLATFORM_META } from "@/lib/mock-data";
import { EventCard } from "./EventCard";
import { formatDayLabel } from "./scheduleWeek";
import { useScheduleCalendar } from "./useScheduleCalendar";

export function ScheduleCalendar({ drafts }: { drafts: DraftResponse[] }) {
  const { days, weekLabel, isEmpty, accountNames, previousWeek, nextWeek } =
    useScheduleCalendar(drafts);

  const nameFor = (accountId: string) =>
    accountNames.get(accountId) ?? accountId;

  return (
    <div>
      <header className="sticky top-0 z-20 flex flex-wrap items-center justify-between gap-3 border-b border-border bg-background/85 px-4 py-4.5 backdrop-blur-sm md:px-7">
        <div>
          <h1 className="text-xl font-semibold tracking-tight">Scheduling</h1>
          <p className="mt-1 hidden text-[13px] text-muted-foreground sm:block">
            Approved posts queued for publish. The worker publishes at the due
            time.
          </p>
        </div>
        <div className="flex items-center gap-2.5">
          <div className="flex h-8.5 items-center overflow-hidden rounded-lg border border-border">
            <button
              type="button"
              onClick={previousWeek}
              className="flex h-full w-8 items-center justify-center text-muted-foreground hover:bg-muted"
            >
              <ChevronLeft className="size-3.5" />
            </button>
            <span className="flex h-full items-center border-x border-border px-3.5 text-[13px] font-semibold">
              {weekLabel}
            </span>
            <button
              type="button"
              onClick={nextWeek}
              className="flex h-full w-8 items-center justify-center text-muted-foreground hover:bg-muted"
            >
              <ChevronRight className="size-3.5" />
            </button>
          </div>
          <Button
            size="sm"
            render={<Link href="/review" />}
            nativeButton={false}
          >
            <span className="hidden sm:inline">Queue a post</span>
            <span className="sm:hidden">Queue</span>
          </Button>
        </div>
      </header>

      <div className="px-4 py-5 pb-10 md:px-7">
        <div className="hidden grid-cols-7 gap-3 md:grid">
          {days.map((day) => (
            <div
              key={day.date.toISOString()}
              className="flex min-h-[420px] flex-col gap-2.5"
            >
              <div className="flex items-baseline justify-between border-b border-border pb-1">
                <span className="text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">
                  {day.dow}
                </span>
                <span className="text-[15px] font-semibold">
                  {day.dayOfMonth}
                </span>
              </div>
              {day.drafts.map((draft) => (
                <EventCard
                  key={draft.id}
                  draft={draft}
                  accountName={nameFor(draft.accountId)}
                />
              ))}
              {day.drafts.length === 0 && (
                <div className="min-h-[60px] flex-1 rounded-lg border border-dashed border-border" />
              )}
            </div>
          ))}
        </div>

        <div className="flex flex-col md:hidden">
          {days
            .filter((day) => day.drafts.length > 0)
            .map((day) => (
              <div key={day.date.toISOString()}>
                <div className="flex items-center gap-2 pb-2 pt-4 first:pt-0">
                  <span className="text-[13px] font-bold">{day.dow}</span>
                  <span className="text-xs text-muted-foreground">
                    {formatDayLabel(day.date)}
                  </span>
                  <div className="h-px flex-1 bg-border" />
                </div>
                {day.drafts.map((draft) => (
                  <div key={draft.id} className="mb-2.5">
                    <EventCard
                      draft={draft}
                      accountName={nameFor(draft.accountId)}
                    />
                  </div>
                ))}
              </div>
            ))}
          {isEmpty && (
            <div className="py-10 text-center text-sm text-muted-foreground">
              No posts scheduled this week.
            </div>
          )}
        </div>

        <div className="mt-5.5 flex flex-wrap items-center gap-5 border-t border-border pt-4">
          <span className="text-[11.5px] font-medium text-muted-foreground">
            Platforms
          </span>
          <span className="flex items-center gap-1.5 text-xs text-neutral-600 dark:text-neutral-400">
            <span
              className="size-2 rounded-full"
              style={{ background: PLATFORM_META.BLUESKY.dot }}
            />
            Bluesky
          </span>
          <span className="flex items-center gap-1.5 text-xs text-neutral-600 dark:text-neutral-400">
            <span
              className="size-2 rounded-full"
              style={{ background: PLATFORM_META.MASTODON.dot }}
            />
            Mastodon
          </span>
          <span className="flex items-center gap-1.5 text-xs text-neutral-600 dark:text-neutral-400">
            <span
              className="size-2 rounded-full"
              style={{ background: PLATFORM_META.REDDIT.dot }}
            />
            Reddit
          </span>
          <div className="h-4 w-px bg-border" />
          <span className="flex items-center gap-1.5 text-xs text-neutral-600 dark:text-neutral-400">
            <span className="size-2 rounded-sm bg-blue-500" />
            Scheduled
          </span>
        </div>
      </div>
    </div>
  );
}
