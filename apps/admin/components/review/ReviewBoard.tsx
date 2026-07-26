"use client";

import { useState } from "react";
import { DraftDetailPanel } from "@/components/draft/DraftDetailPanel";
import { draftMeta } from "@/components/draft/draftDisplay";
import { StatusBadge } from "@/components/draft/StatusBadge";
import { statusLabel } from "@/lib/api/mappers";
import type { DraftResponse } from "@/lib/api/types";
import { PLATFORM_META } from "@/lib/mock-data";
import { cn } from "@/lib/utils";
import { ComposeDraft } from "./ComposeDraft";
import { REVIEW_STATUS_TABS } from "./reviewMeta";
import { ScheduleDialog } from "./ScheduleDialog";
import { useReviewBoard } from "./useReviewBoard";
import { useReviewMutations } from "./useReviewMutations";

export function ReviewBoard({ drafts }: { drafts: DraftResponse[] }) {
  const [scheduleOpen, setScheduleOpen] = useState(false);
  const {
    hasAccount,
    filter,
    setFilter,
    counts,
    filteredDrafts,
    selected,
    selectDraft,
    selectSignal,
    accountNames,
  } = useReviewBoard(drafts);
  const { approve, schedule, pending } = useReviewMutations();

  if (!hasAccount) {
    return (
      <div className="flex h-full items-center justify-center p-8 text-center text-sm text-muted-foreground">
        Select or connect an account to review its drafts.
      </div>
    );
  }

  // Approve applies only to DRAFT, schedule only to APPROVED — guarded here (the
  // backend enforces it too).
  const onApprove = () => {
    if (selected?.status === "DRAFT") {
      approve(selected.id);
    }
  };

  const onSchedule = () => {
    if (selected?.status === "APPROVED") {
      setScheduleOpen(true);
    }
  };

  return (
    <div className="flex h-full min-h-0">
      <div className="flex h-full w-full shrink-0 flex-col border-border md:w-[352px] md:border-r">
        <div className="border-b border-border px-5 pb-3.5 pt-4.5">
          <div className="mb-0.5 flex items-center justify-between gap-2">
            <h1 className="text-lg font-semibold tracking-tight">
              Review &amp; Approve
            </h1>
            <ComposeDraft />
          </div>
          <p className="mb-3.5 text-[12.5px] text-muted-foreground">
            Nothing publishes without your approval.
          </p>
          <div className="flex flex-nowrap gap-1.5 overflow-x-auto md:flex-wrap md:overflow-visible">
            {REVIEW_STATUS_TABS.map((tab) => {
              const on = filter === tab;
              return (
                <button
                  type="button"
                  key={tab}
                  onClick={() => setFilter(tab)}
                  className={cn(
                    "flex h-7 shrink-0 items-center gap-1.5 rounded-lg border px-2.5 text-[11.5px] font-medium",
                    on
                      ? "border-foreground bg-foreground text-background"
                      : "border-border bg-background text-foreground/80 hover:bg-muted",
                  )}
                >
                  {tab === "ALL" ? "All" : statusLabel(tab)}
                  <span
                    className={cn(
                      "rounded-full px-1.5 text-[10.5px] font-semibold leading-4",
                      on
                        ? "bg-background/20 text-background"
                        : "bg-muted text-muted-foreground",
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
            const active = selected?.id === d.id;
            return (
              <button
                type="button"
                key={d.id}
                onClick={() => selectDraft(d.id)}
                className={cn(
                  "mb-0.5 block w-full rounded-xl border p-3 text-left",
                  active
                    ? "border-border bg-background shadow-sm"
                    : "border-transparent hover:bg-muted/50",
                )}
              >
                <div className="mb-1.5 flex items-center gap-2">
                  <span
                    className={cn(
                      "flex size-4.5 shrink-0 items-center justify-center rounded",
                      meta.badgeBg,
                    )}
                  >
                    <span
                      className="size-1.5 rounded-full"
                      style={{ background: meta.dot }}
                    />
                  </span>
                  <span className="min-w-0 flex-1 truncate text-[11.5px] font-semibold text-neutral-600 dark:text-neutral-400">
                    {accountNames.get(d.accountId) ?? d.accountId}
                  </span>
                  <StatusBadge status={d.status} />
                </div>
                <div className="line-clamp-2 text-[12.5px] leading-relaxed text-neutral-600 dark:text-neutral-400">
                  {d.content.replace(/\n+/g, " ")}
                </div>
                <div className="mt-2 font-mono text-[11px] text-muted-foreground">
                  {draftMeta(d)}
                </div>
              </button>
            );
          })}
          {filteredDrafts.length === 0 && (
            <div className="px-3 py-8 text-center text-sm text-muted-foreground">
              No drafts in this state.
            </div>
          )}
        </div>
      </div>

      <DraftDetailPanel
        draft={selected}
        accountName={
          selected
            ? (accountNames.get(selected.accountId) ?? selected.accountId)
            : ""
        }
        selectSignal={selectSignal}
        pending={pending}
        onApprove={onApprove}
        onSchedule={onSchedule}
      />

      <ScheduleDialog
        open={scheduleOpen}
        onOpenChange={setScheduleOpen}
        pending={pending}
        onConfirm={(scheduledAt) => {
          if (selected) schedule(selected.id, scheduledAt);
          setScheduleOpen(false);
        }}
      />
    </div>
  );
}
