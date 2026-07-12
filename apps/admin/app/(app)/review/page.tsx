"use client";

import { useMemo, useState } from "react";
import { CalendarDays, Check, ChevronLeft, Link2, RefreshCw, Sparkles, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  DRAFTS,
  PLATFORM_META,
  REVIEW_STATUS_TABS,
  STATE_MACHINE_STEPS,
  STATUS_META,
  type Draft,
  type DraftStatus,
} from "@/lib/mock-data";
import { cn } from "@/lib/utils";

const STEP_LABEL: Record<(typeof STATE_MACHINE_STEPS)[number], string> = {
  draft: "Draft",
  approved: "Approved",
  scheduled: "Scheduled",
  published: "Published",
};

function StatusBadge({ status, size = "sm" }: { status: DraftStatus; size?: "sm" | "lg" }) {
  const meta = STATUS_META[status];
  return (
    <span
      className={cn(
        "inline-flex shrink-0 items-center gap-1.5 rounded-full font-semibold capitalize",
        meta.badgeBg,
        meta.badgeText,
        size === "lg" ? "px-2.5 py-1 text-[11.5px]" : "px-2 py-0.5 text-[10.5px]"
      )}
    >
      {status}
    </span>
  );
}

function DraftDetailBody({ selected }: { selected: Draft }) {
  const curIdx = Math.max(
    0,
    STATE_MACHINE_STEPS.indexOf(selected.status === "failed" ? "scheduled" : (selected.status as (typeof STATE_MACHINE_STEPS)[number]))
  );

  const charPct = Math.min(100, Math.round((selected.chars / selected.limit) * 100));
  const charBarColor = charPct > 95 ? "bg-red-500" : charPct > 80 ? "bg-amber-500" : "bg-green-500";

  const affiliateBoxClass = !selected.affiliate
    ? "border-border bg-background"
    : selected.disclosure
      ? "border-green-200 bg-green-50 dark:border-green-900 dark:bg-green-950/30"
      : "border-amber-200 bg-amber-50 dark:border-amber-900 dark:bg-amber-950/30";

  const discPillClass = !selected.affiliate
    ? "bg-muted text-muted-foreground"
    : selected.disclosure
      ? "bg-green-100 text-green-700 dark:bg-green-900/50 dark:text-green-400"
      : "bg-amber-100 text-amber-700 dark:bg-amber-900/50 dark:text-amber-400";

  return (
    <>
      <div className="mb-5 flex items-center rounded-xl border border-border bg-background px-3 py-3.5 sm:px-4.5">
        {STATE_MACHINE_STEPS.map((step, i) => {
          const done = i <= curIdx;
          return (
            <div key={step} className="flex shrink-0 items-center sm:flex-1 sm:last:flex-none">
              <div className="flex items-center gap-1.5 sm:gap-2">
                <div
                  className={cn(
                    "flex size-5 shrink-0 items-center justify-center rounded-full",
                    done ? "bg-primary" : "border-2 border-border bg-background"
                  )}
                >
                  {done && <Check className="size-2.5 text-primary-foreground" strokeWidth={3.5} />}
                </div>
                <span className={cn("text-[11px] sm:text-[12.5px]", done ? "font-semibold" : "font-medium text-muted-foreground")}>{STEP_LABEL[step]}</span>
              </div>
              {i < STATE_MACHINE_STEPS.length - 1 && (
                <div className={cn("mx-1.5 h-0.5 w-3 shrink-0 sm:mx-2 sm:w-auto sm:flex-1", i < curIdx ? "bg-primary" : "bg-border")} />
              )}
            </div>
          );
        })}
      </div>

      <div className="mb-4 overflow-hidden rounded-xl border border-border bg-background">
        <div className="flex items-center gap-2.5 border-b border-border px-4 py-2.5">
          <span className="flex items-center gap-1.5 rounded-full border border-violet-200 bg-violet-50 px-2.5 py-1 text-[11px] font-semibold text-violet-700 dark:border-violet-900 dark:bg-violet-950/40 dark:text-violet-400">
            <Sparkles className="size-3" />
            AI generated
          </span>
          <span className="hidden text-[11.5px] text-muted-foreground sm:inline">Claude · voice: &ldquo;builder, dry, concrete&rdquo;</span>
          <div className="flex-1" />
          <button
            title="Regenerate"
            className="flex size-7 items-center justify-center rounded-lg border border-border text-muted-foreground hover:bg-muted"
          >
            <RefreshCw className="size-3.5" />
          </button>
        </div>
        <div className="min-h-[120px] whitespace-pre-wrap px-4 pb-2 pt-4 text-[14.5px] leading-relaxed">{selected.content}</div>
        <div className="flex flex-wrap items-center gap-2.5 border-t border-border px-4 py-2.5">
          <span className="text-xs tabular-nums text-muted-foreground">
            {selected.chars} / {selected.limit} chars
          </span>
          <div className="h-1 max-w-[180px] flex-1 overflow-hidden rounded-full bg-muted">
            <div className={cn("h-full rounded-full", charBarColor)} style={{ width: `${charPct}%` }} />
          </div>
          <span className="w-full font-mono text-[11.5px] text-muted-foreground sm:w-auto sm:flex-1 sm:text-right">from signal: {selected.signal}</span>
        </div>
      </div>

      <div className={cn("rounded-xl border p-3.5", affiliateBoxClass)}>
        <div className="flex items-center gap-2.5">
          <Link2 className={cn("size-4 shrink-0", selected.affiliate ? (selected.disclosure ? "text-green-600" : "text-amber-600") : "text-muted-foreground")} />
          <div className="flex-1">
            <div className="text-[13px] font-semibold">{selected.affiliate ? "1 affiliate link attached" : "No affiliate links"}</div>
            <div className="text-xs text-muted-foreground">
              {selected.affiliate
                ? selected.disclosure
                  ? "Disclosure present — cleared to publish."
                  : "Publisher will block this post until a disclosure is added."
                : "Add a program link if this post promotes a product."}
            </div>
          </div>
          <span className={cn("shrink-0 rounded-full px-2.5 py-1 text-[11px] font-semibold", discPillClass)}>
            {selected.affiliate ? (selected.disclosure ? "Disclosed" : "Disclosure required") : "N/A"}
          </span>
        </div>
      </div>
    </>
  );
}

export default function ReviewPage() {
  const [filter, setFilter] = useState<DraftStatus | "all">("all");
  const [selectedId, setSelectedId] = useState(DRAFTS[0].id);
  const [mobileOpen, setMobileOpen] = useState(false);

  const counts = useMemo(() => {
    const c: Record<string, number> = { all: DRAFTS.length };
    for (const d of DRAFTS) c[d.status] = (c[d.status] ?? 0) + 1;
    return c;
  }, []);

  const filteredDrafts = useMemo(
    () => (filter === "all" ? DRAFTS : DRAFTS.filter((d) => d.status === filter)),
    [filter]
  );

  const selected = DRAFTS.find((d) => d.id === selectedId) ?? DRAFTS[0];
  const selectedPlatform = PLATFORM_META[selected.platform];

  return (
    <div className="flex h-full min-h-0">
      <div className="flex h-full w-full shrink-0 flex-col border-border md:w-[352px] md:border-r">
        <div className="border-b border-border px-5 pb-3.5 pt-4.5">
          <h1 className="mb-0.5 text-lg font-semibold tracking-tight">Review &amp; Approve</h1>
          <p className="mb-3.5 text-[12.5px] text-muted-foreground">Nothing publishes without your approval.</p>
          <div className="flex flex-nowrap gap-1.5 overflow-x-auto md:flex-wrap md:overflow-visible">
            {REVIEW_STATUS_TABS.map((tab) => {
              const on = filter === tab;
              return (
                <button
                  key={tab}
                  onClick={() => setFilter(tab)}
                  className={cn(
                    "flex h-7 shrink-0 items-center gap-1.5 rounded-lg border px-2.5 text-[11.5px] font-medium capitalize",
                    on ? "border-foreground bg-foreground text-background" : "border-border bg-background text-foreground/80 hover:bg-muted"
                  )}
                >
                  {tab}
                  <span
                    className={cn(
                      "rounded-full px-1.5 text-[10.5px] font-semibold leading-4",
                      on ? "bg-background/20 text-background" : "bg-muted text-muted-foreground"
                    )}
                  >
                    {counts[tab] ?? 0}
                  </span>
                </button>
              );
            })}
          </div>
        </div>
        <div className="flex-1 overflow-auto p-2">
          {filteredDrafts.map((d) => {
            const meta = PLATFORM_META[d.platform];
            const active = selected.id === d.id;
            return (
              <button
                key={d.id}
                onClick={() => {
                  setSelectedId(d.id);
                  setMobileOpen(true);
                }}
                className={cn(
                  "mb-0.5 block w-full rounded-xl border p-3 text-left",
                  active ? "border-border bg-background shadow-sm" : "border-transparent hover:bg-muted/50"
                )}
              >
                <div className="mb-1.5 flex items-center gap-2">
                  <span className={cn("flex size-4.5 shrink-0 items-center justify-center rounded", meta.badgeBg)}>
                    <span className="size-1.5 rounded-full" style={{ background: meta.dot }} />
                  </span>
                  <span className="min-w-0 flex-1 truncate text-[11.5px] font-semibold text-neutral-600 dark:text-neutral-400">{d.account}</span>
                  <StatusBadge status={d.status} />
                </div>
                <div className="line-clamp-2 text-[12.5px] leading-relaxed text-neutral-600 dark:text-neutral-400">
                  {d.content.replace(/\n+/g, " ")}
                </div>
                <div className="mt-2 font-mono text-[11px] text-muted-foreground">{d.meta}</div>
              </button>
            );
          })}
          {filteredDrafts.length === 0 && (
            <div className="px-3 py-8 text-center text-sm text-muted-foreground">No drafts in this state.</div>
          )}
        </div>
      </div>

      <div className="hidden flex-1 overflow-auto bg-muted/30 md:block">
        <div className="mx-auto max-w-[720px] px-8 py-6.5 pb-11">
          <div className="mb-4.5 flex items-center gap-3">
            <span className={cn("flex size-9.5 shrink-0 items-center justify-center rounded-xl", selectedPlatform.badgeBg)}>
              <span className="size-2.5 rounded-full" style={{ background: selectedPlatform.dot }} />
            </span>
            <div className="flex-1">
              <div className="text-[15px] font-semibold">{selected.account}</div>
              <div className="text-[12.5px] text-muted-foreground">
                {selected.platform} · {selected.platform === "Reddit" ? "single account" : "switchable"}
              </div>
            </div>
            <StatusBadge status={selected.status} size="lg" />
          </div>

          <DraftDetailBody selected={selected} />

          <div className="mt-5 flex items-center gap-2.5">
            <Button variant="outline" className="text-destructive hover:bg-destructive/10">
              Discard
            </Button>
            <div className="flex-1" />
            <Button variant="outline">Save edits</Button>
            <Button variant="outline">Schedule</Button>
            <Button>
              <Check className="size-3.5" strokeWidth={2.5} />
              Approve
            </Button>
          </div>
        </div>
      </div>

      <div
        className={cn(
          "fixed inset-0 z-40 flex-col bg-background md:hidden",
          mobileOpen ? "flex" : "hidden"
        )}
      >
        <div className="flex items-center gap-2.5 border-b border-border px-3 py-3">
          <button
            onClick={() => setMobileOpen(false)}
            className="flex size-9 shrink-0 items-center justify-center rounded-full bg-muted text-foreground"
          >
            <ChevronLeft className="size-[18px]" strokeWidth={2.2} />
          </button>
          <div className="min-w-0 flex-1">
            <div className="truncate text-[15px] font-semibold">{selected.account}</div>
            <div className="text-xs text-muted-foreground">
              {selected.platform} · {selected.platform === "Reddit" ? "single account" : "switchable"}
            </div>
          </div>
          <StatusBadge status={selected.status} size="lg" />
        </div>

        <div className="flex-1 overflow-auto px-4 pb-32 pt-4">
          <DraftDetailBody selected={selected} />
        </div>

        <div className="absolute inset-x-0 bottom-0 flex gap-2.5 border-t border-border bg-background/95 px-4 pb-8 pt-3.5 backdrop-blur-sm">
          <Button variant="outline" size="icon-lg" className="shrink-0 text-destructive hover:bg-destructive/10">
            <Trash2 className="size-[18px]" />
          </Button>
          <Button variant="outline" size="lg" className="flex-1">
            <CalendarDays className="size-4" />
            Schedule
          </Button>
          <Button size="lg" className="flex-1">
            <Check className="size-4" strokeWidth={2.5} />
            Approve
          </Button>
        </div>
      </div>
    </div>
  );
}
