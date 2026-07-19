import { ChevronLeft, ChevronRight } from "lucide-react";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { PLATFORM_META, WEEK } from "@/lib/mock-data";
import { cn } from "@/lib/utils";

export default function SchedulePage() {
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
              className="flex h-full w-8 items-center justify-center text-muted-foreground hover:bg-muted"
            >
              <ChevronLeft className="size-3.5" />
            </button>
            <span className="flex h-full items-center border-x border-border px-3.5 text-[13px] font-semibold">
              Jul 13 – 19, 2026
            </span>
            <button
              type="button"
              className="flex h-full w-8 items-center justify-center text-muted-foreground hover:bg-muted"
            >
              <ChevronRight className="size-3.5" />
            </button>
          </div>
          <Button variant="outline" size="sm" className="hidden md:inline-flex">
            Week
          </Button>
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
          {WEEK.map((day) => (
            <div key={day.dow} className="flex min-h-[420px] flex-col gap-2.5">
              <div className="flex items-baseline justify-between border-b border-border pb-1">
                <span className="text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">
                  {day.dow}
                </span>
                <span className="text-[15px] font-semibold">{day.date}</span>
              </div>
              {day.events.map((ev) => {
                const meta = PLATFORM_META[ev.platform];
                return (
                  <div
                    key={`${ev.time}-${ev.account}`}
                    className="cursor-pointer rounded-lg border border-border border-l-[3px] bg-background p-2.5 hover:shadow-sm"
                    style={{ borderLeftColor: meta.dot }}
                  >
                    <div className="mb-1.5 flex items-center gap-1.5">
                      <span className="text-[11px] font-bold tabular-nums">
                        {ev.time}
                      </span>
                      <div className="flex-1" />
                      <span
                        className={cn(
                          "size-2 rounded-sm",
                          ev.status === "approved"
                            ? "bg-green-500"
                            : "bg-blue-500",
                        )}
                      />
                    </div>
                    <div className="line-clamp-3 text-[11.5px] leading-snug text-neutral-600 dark:text-neutral-400">
                      {ev.title}
                    </div>
                    <div className="mt-2 flex items-center gap-1.5">
                      <span
                        className="size-1.5 rounded-full"
                        style={{ background: meta.dot }}
                      />
                      <span className="text-[10.5px] text-muted-foreground">
                        {ev.account}
                      </span>
                    </div>
                  </div>
                );
              })}
              {day.events.length === 0 && (
                <div className="min-h-[60px] flex-1 rounded-lg border border-dashed border-border" />
              )}
            </div>
          ))}
        </div>

        <div className="flex flex-col md:hidden">
          {WEEK.filter((day) => day.events.length > 0).map((day) => (
            <div key={day.dow}>
              <div className="flex items-center gap-2 pb-2 pt-4 first:pt-0">
                <span className="text-[13px] font-bold">{day.dow}</span>
                <span className="text-xs text-muted-foreground">
                  Jul {day.date}
                </span>
                <div className="h-px flex-1 bg-border" />
              </div>
              {day.events.map((ev) => {
                const meta = PLATFORM_META[ev.platform];
                return (
                  <div
                    key={`${ev.time}-${ev.account}`}
                    className="mb-2.5 flex gap-3 rounded-xl border border-border border-l-[3px] bg-background p-3.5"
                    style={{ borderLeftColor: meta.dot }}
                  >
                    <div className="w-[54px] shrink-0">
                      <div className="text-[13px] font-bold tabular-nums">
                        {ev.time}
                      </div>
                    </div>
                    <div className="min-w-0 flex-1">
                      <div className="mb-2 line-clamp-2 text-[13px] leading-snug text-neutral-600 dark:text-neutral-400">
                        {ev.title}
                      </div>
                      <div className="flex items-center gap-1.5">
                        <span
                          className="size-1.5 rounded-full"
                          style={{ background: meta.dot }}
                        />
                        <span className="flex-1 truncate text-[11.5px] text-muted-foreground">
                          {ev.account}
                        </span>
                        <span
                          className={cn(
                            "shrink-0 rounded-full px-2 text-[10.5px] font-semibold leading-5",
                            ev.status === "approved"
                              ? "bg-green-50 text-green-700 dark:bg-green-950/40 dark:text-green-400"
                              : "bg-blue-50 text-blue-700 dark:bg-blue-950/40 dark:text-blue-400",
                          )}
                        >
                          {ev.status === "approved" ? "Approved" : "Scheduled"}
                        </span>
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          ))}
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
          <span className="text-[11.5px] font-medium text-muted-foreground">
            Status
          </span>
          <span className="flex items-center gap-1.5 text-xs text-neutral-600 dark:text-neutral-400">
            <span className="size-2 rounded-sm bg-blue-500" />
            Scheduled
          </span>
          <span className="flex items-center gap-1.5 text-xs text-neutral-600 dark:text-neutral-400">
            <span className="size-2 rounded-sm bg-green-500" />
            Approved
          </span>
        </div>
      </div>
    </div>
  );
}
