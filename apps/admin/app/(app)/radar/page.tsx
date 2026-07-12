"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { LayoutGrid, List, RefreshCw, Search } from "lucide-react";
import { Button } from "@/components/ui/button";
import { RADAR_STATS, SIGNALS, SOURCE_CHIPS, SOURCE_META } from "@/lib/mock-data";
import { cn } from "@/lib/utils";

export default function RadarPage() {
  const [query, setQuery] = useState("");
  const [activeSource, setActiveSource] = useState<string>("All");
  const [layout, setLayout] = useState<"table" | "cards">("table");

  const signals = useMemo(() => {
    return SIGNALS.filter(
      (s) =>
        (activeSource === "All" || s.source === activeSource) &&
        s.title.toLowerCase().includes(query.toLowerCase())
    ).sort((a, b) => b.match - a.match);
  }, [query, activeSource]);

  return (
    <div>
      <header className="sticky top-0 z-20 flex items-end justify-between gap-4 border-b border-border bg-background/85 px-4 py-4.5 backdrop-blur-sm md:px-7">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-xl font-semibold tracking-tight">Trend Radar</h1>
            <span className="flex items-center gap-1.5 rounded-full border border-green-200 bg-green-50 px-2.5 py-0.5 text-[11px] font-medium text-green-700 dark:border-green-900 dark:bg-green-950/40 dark:text-green-400">
              <span className="size-1.5 rounded-full bg-green-500" />
              Live
            </span>
          </div>
          <p className="mt-1 hidden text-[13px] text-muted-foreground sm:block">
            Signals from free-API sources, scored against your voice profile.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <div className="hidden font-mono text-[11.5px] text-muted-foreground sm:block">updated 2m ago</div>
          <Button variant="outline" size="sm">
            <RefreshCw className="size-3.5" />
            <span className="hidden sm:inline">Refresh</span>
          </Button>
        </div>
      </header>

      <div className="max-w-[1180px] px-4 py-5.5 pb-10 md:px-7">
        <div className="mb-5.5 grid grid-cols-2 gap-3.5 md:grid-cols-4">
          {RADAR_STATS.map((st) => (
            <div key={st.label} className="rounded-xl border border-border bg-background p-4">
              <div className="mb-2 text-xs text-muted-foreground">{st.label}</div>
              <div className="flex items-baseline gap-1.5">
                <div className="text-2xl font-semibold tracking-tight">{st.value}</div>
                <div className={cn("text-[11.5px] font-semibold", st.up ? "text-green-600 dark:text-green-400" : "text-muted-foreground")}>
                  {st.delta}
                </div>
              </div>
            </div>
          ))}
        </div>

        <div className="mb-3.5 flex flex-wrap items-center gap-2.5">
          <div className="flex h-8.5 max-w-[340px] flex-1 items-center gap-2 rounded-lg border border-border bg-background px-2.5" style={{ minWidth: 220 }}>
            <Search className="size-3.5 text-muted-foreground" />
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search signals…"
              className="w-full bg-transparent text-[13px] outline-none placeholder:text-muted-foreground"
            />
          </div>
          <div className="flex w-full flex-nowrap gap-1.5 overflow-x-auto sm:w-auto md:flex-wrap md:overflow-visible">
            {SOURCE_CHIPS.map((label) => {
              const on = activeSource === label;
              const dot = label === "All" ? "#a3a3a3" : SOURCE_META[label]?.dot;
              return (
                <button
                  key={label}
                  onClick={() => setActiveSource(label)}
                  className={cn(
                    "flex h-7.5 shrink-0 items-center gap-1.5 rounded-full border px-2.5 text-xs font-medium",
                    on ? "border-foreground bg-foreground text-background" : "border-border bg-background text-foreground/80 hover:bg-muted"
                  )}
                >
                  <span className="size-1.5 rounded-full" style={{ background: on ? "currentColor" : dot }} />
                  {label}
                </button>
              );
            })}
          </div>
          <div className="flex-1" />
          <div className="hidden items-center overflow-hidden rounded-lg border border-border md:flex">
            <button
              onClick={() => setLayout("table")}
              className={cn("flex h-8.5 items-center gap-1.5 px-2.5 text-xs font-medium", layout === "table" ? "bg-muted" : "hover:bg-muted/60")}
            >
              <List className="size-3.5" /> Table
            </button>
            <button
              onClick={() => setLayout("cards")}
              className={cn("flex h-8.5 items-center gap-1.5 border-l border-border px-2.5 text-xs font-medium", layout === "cards" ? "bg-muted" : "hover:bg-muted/60")}
            >
              <LayoutGrid className="size-3.5" /> Cards
            </button>
          </div>
        </div>

        <div className="hidden md:block">
        {layout === "table" ? (
          <div className="overflow-hidden rounded-xl border border-border">
            <div className="flex min-w-[760px] items-center gap-3.5 border-b border-border bg-muted/40 px-4.5 py-2 text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">
              <div className="min-w-[180px] flex-1">Signal</div>
              <div className="w-[120px] shrink-0">Topic</div>
              <div className="w-[158px] shrink-0">Engagement</div>
              <div className="w-[128px] shrink-0">Match</div>
              <div className="w-[60px] shrink-0">Age</div>
              <div className="w-[80px] shrink-0" />
            </div>
            {signals.map((sig) => {
              const src = SOURCE_META[sig.source];
              return (
                <div
                  key={sig.id}
                  className="flex min-w-[760px] items-center gap-3.5 border-b border-border px-4.5 py-3.5 last:border-b-0 hover:bg-muted/30"
                >
                  <div className="min-w-[180px] flex-1">
                    <span className={cn("mb-1.5 inline-flex items-center gap-1.5 rounded px-1.5 py-0.5 text-[10.5px] font-semibold", src.badgeBg, src.badgeText)}>
                      <span className="size-1.5 rounded-full" style={{ background: src.dot }} />
                      {sig.source}
                    </span>
                    <div className="text-[13.5px] font-semibold leading-snug">{sig.title}</div>
                  </div>
                  <div className="w-[120px] shrink-0">
                    <span className="rounded-md bg-muted px-2.5 py-0.5 text-[11.5px] font-medium text-muted-foreground">{sig.topic}</span>
                  </div>
                  <div className="w-[158px] shrink-0 text-[12.5px] tabular-nums text-muted-foreground">{sig.engagement}</div>
                  <div className="w-[128px] shrink-0">
                    <div className="flex items-center gap-2">
                      <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-muted">
                        <div className="h-full rounded-full bg-primary" style={{ width: `${sig.match}%` }} />
                      </div>
                      <span className="w-8 text-right text-xs font-semibold tabular-nums">{sig.match}%</span>
                    </div>
                  </div>
                  <div className="w-[60px] shrink-0 font-mono text-xs text-muted-foreground">{sig.age}</div>
                  <div className="flex w-[80px] shrink-0 justify-end">
                    <Button size="sm" render={<Link href="/review" />} nativeButton={false}>
                      Draft
                    </Button>
                  </div>
                </div>
              );
            })}
            {signals.length === 0 && (
              <div className="px-4.5 py-8 text-center text-sm text-muted-foreground">No signals match your filters.</div>
            )}
          </div>
        ) : (
          <div className="grid grid-cols-2 gap-3.5">
            {signals.map((sig) => {
              const src = SOURCE_META[sig.source];
              return (
                <div key={sig.id} className="flex flex-col gap-3 rounded-xl border border-border bg-background p-4.5 hover:shadow-sm">
                  <div className="flex items-center gap-2">
                    <span className={cn("inline-flex items-center gap-1.5 rounded px-1.5 py-0.5 text-[10.5px] font-semibold", src.badgeBg, src.badgeText)}>
                      <span className="size-1.5 rounded-full" style={{ background: src.dot }} />
                      {sig.source}
                    </span>
                    <span className="rounded-md bg-muted px-2.5 py-0.5 text-[11.5px] font-medium text-muted-foreground">{sig.topic}</span>
                    <div className="flex-1" />
                    <span className="font-mono text-[11.5px] text-muted-foreground">{sig.age}</span>
                  </div>
                  <div className="text-[14.5px] font-semibold leading-snug">{sig.title}</div>
                  <div className="text-[12.5px] text-muted-foreground">{sig.engagement}</div>
                  <div className="mt-auto flex items-center gap-2.5">
                    <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-muted">
                      <div className="h-full rounded-full bg-primary" style={{ width: `${sig.match}%` }} />
                    </div>
                    <span className="text-xs font-semibold tabular-nums">{sig.match}% match</span>
                    <Button size="sm" render={<Link href="/review" />} nativeButton={false}>
                      Draft
                    </Button>
                  </div>
                </div>
              );
            })}
            {signals.length === 0 && (
              <div className="col-span-2 rounded-xl border border-dashed border-border py-8 text-center text-sm text-muted-foreground">
                No signals match your filters.
              </div>
            )}
          </div>
        )}
        </div>

        <div className="flex flex-col gap-3 md:hidden">
          {signals.map((sig) => {
            const src = SOURCE_META[sig.source];
            return (
              <div key={sig.id} className="rounded-2xl border border-border bg-background p-3.5 shadow-sm">
                <div className="mb-2.5 flex items-center gap-2">
                  <span className={cn("inline-flex items-center gap-1.5 rounded px-1.5 py-0.5 text-[10.5px] font-semibold", src.badgeBg, src.badgeText)}>
                    <span className="size-1.5 rounded-full" style={{ background: src.dot }} />
                    {sig.source}
                  </span>
                  <span className="rounded-md bg-muted px-2 py-0.5 text-[11px] font-medium text-muted-foreground">{sig.topic}</span>
                  <div className="flex-1" />
                  <span className="font-mono text-[11px] text-muted-foreground">{sig.age}</span>
                </div>
                <div className="mb-2.5 text-[15px] font-semibold leading-snug">{sig.title}</div>
                <div className="mb-3 text-[12.5px] text-muted-foreground">{sig.engagement}</div>
                <div className="flex items-center gap-2.5">
                  <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-muted">
                    <div className="h-full rounded-full bg-primary" style={{ width: `${sig.match}%` }} />
                  </div>
                  <span className="w-[62px] shrink-0 text-[12.5px] font-semibold tabular-nums">{sig.match}% match</span>
                  <Button size="sm" render={<Link href="/review" />} nativeButton={false}>
                    Draft
                  </Button>
                </div>
              </div>
            );
          })}
          {signals.length === 0 && (
            <div className="rounded-xl border border-dashed border-border py-8 text-center text-sm text-muted-foreground">
              No signals match your filters.
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
