import { Link2, RefreshCw, Sparkles } from "lucide-react";
import { statusLabel } from "@/lib/api/mappers";
import type { DraftResponse } from "@/lib/api/types";
import { cn } from "@/lib/utils";
import {
  draftChars,
  draftHasAffiliate,
  draftLimit,
  STATE_MACHINE_STEPS,
} from "./draftDisplay";

/**
 * The middle of the draft detail panel: the state-machine stepper, the content
 * preview with a length meter, and the affiliate/disclosure box. Presentational
 * — the header and action buttons are separate components.
 */
export function DraftDetail({ draft }: { draft: DraftResponse }) {
  // FAILED isn't a step on the DRAFT→PUBLISHED track; show progress up to SCHEDULED.
  const curIdx = Math.max(
    0,
    STATE_MACHINE_STEPS.indexOf(
      draft.status === "FAILED" ? "SCHEDULED" : draft.status,
    ),
  );

  const chars = draftChars(draft);
  const limit = draftLimit(draft);
  const affiliate = draftHasAffiliate(draft);
  const disclosure = draft.disclosureIncluded;

  const charPct = Math.min(100, Math.round((chars / limit) * 100));
  const charBarColor =
    charPct > 95
      ? "bg-red-500"
      : charPct > 80
        ? "bg-amber-500"
        : "bg-green-500";

  const affiliateBoxClass = !affiliate
    ? "border-border bg-background"
    : disclosure
      ? "border-green-200 bg-green-50 dark:border-green-900 dark:bg-green-950/30"
      : "border-amber-200 bg-amber-50 dark:border-amber-900 dark:bg-amber-950/30";

  const discPillClass = !affiliate
    ? "bg-muted text-muted-foreground"
    : disclosure
      ? "bg-green-100 text-green-700 dark:bg-green-900/50 dark:text-green-400"
      : "bg-amber-100 text-amber-700 dark:bg-amber-900/50 dark:text-amber-400";

  return (
    <>
      <div className="mb-5 flex items-center rounded-xl border border-border bg-background px-3 py-3.5 sm:px-4.5">
        {STATE_MACHINE_STEPS.map((step, i) => {
          const done = i <= curIdx;
          return (
            <div
              key={step}
              className="flex shrink-0 items-center sm:flex-1 sm:last:flex-none"
            >
              <div className="flex items-center gap-1.5 sm:gap-2">
                <div
                  className={cn(
                    "flex size-5 shrink-0 items-center justify-center rounded-full",
                    done
                      ? "bg-primary"
                      : "border-2 border-border bg-background",
                  )}
                >
                  {done && (
                    <span className="size-1.5 rounded-full bg-primary-foreground" />
                  )}
                </div>
                <span
                  className={cn(
                    "text-[11px] sm:text-[12.5px]",
                    done
                      ? "font-semibold"
                      : "font-medium text-muted-foreground",
                  )}
                >
                  {statusLabel(step)}
                </span>
              </div>
              {i < STATE_MACHINE_STEPS.length - 1 && (
                <div
                  className={cn(
                    "mx-1.5 h-0.5 w-3 shrink-0 sm:mx-2 sm:w-auto sm:flex-1",
                    i < curIdx ? "bg-primary" : "bg-border",
                  )}
                />
              )}
            </div>
          );
        })}
      </div>

      <div className="mb-4 overflow-hidden rounded-xl border border-border bg-background">
        <div className="flex items-center gap-2.5 border-b border-border px-4 py-2.5">
          {draft.aiGenerated ? (
            <span className="flex items-center gap-1.5 rounded-full border border-violet-200 bg-violet-50 px-2.5 py-1 text-[11px] font-semibold text-violet-700 dark:border-violet-900 dark:bg-violet-950/40 dark:text-violet-400">
              <Sparkles className="size-3" />
              AI generated
            </span>
          ) : (
            <span className="rounded-full border border-border bg-muted px-2.5 py-1 text-[11px] font-semibold text-muted-foreground">
              Manual
            </span>
          )}
          <div className="flex-1" />
          <button
            type="button"
            title="Regenerate"
            className="flex size-7 items-center justify-center rounded-lg border border-border text-muted-foreground hover:bg-muted"
          >
            <RefreshCw className="size-3.5" />
          </button>
        </div>
        <div className="min-h-[120px] whitespace-pre-wrap px-4 pb-2 pt-4 text-[14.5px] leading-relaxed">
          {draft.content}
        </div>
        <div className="flex flex-wrap items-center gap-2.5 border-t border-border px-4 py-2.5">
          <span className="text-xs tabular-nums text-muted-foreground">
            {chars} / {limit} chars
          </span>
          <div className="h-1 max-w-[180px] flex-1 overflow-hidden rounded-full bg-muted">
            <div
              className={cn("h-full rounded-full", charBarColor)}
              style={{ width: `${charPct}%` }}
            />
          </div>
          <span className="w-full font-mono text-[11.5px] text-muted-foreground sm:w-auto sm:flex-1 sm:text-right">
            {draft.signalId ? "from a trend signal" : "manual draft"}
          </span>
        </div>
      </div>

      <div className={cn("rounded-xl border p-3.5", affiliateBoxClass)}>
        <div className="flex items-center gap-2.5">
          <Link2
            className={cn(
              "size-4 shrink-0",
              affiliate
                ? disclosure
                  ? "text-green-600"
                  : "text-amber-600"
                : "text-muted-foreground",
            )}
          />
          <div className="flex-1">
            <div className="text-[13px] font-semibold">
              {affiliate ? "Affiliate link attached" : "No affiliate links"}
            </div>
            <div className="text-xs text-muted-foreground">
              {affiliate
                ? disclosure
                  ? "Disclosure present — cleared to publish."
                  : "Publisher will block this post until a disclosure is added."
                : "Add a program link if this post promotes a product."}
            </div>
          </div>
          <span
            className={cn(
              "shrink-0 rounded-full px-2.5 py-1 text-[11px] font-semibold",
              discPillClass,
            )}
          >
            {affiliate
              ? disclosure
                ? "Disclosed"
                : "Disclosure required"
              : "N/A"}
          </span>
        </div>
      </div>
    </>
  );
}
