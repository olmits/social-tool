"use client";

import { RefreshCw } from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

/**
 * Radar title bar. The Refresh button re-reads stored signals — it does not make
 * the poller run — and `updated` is the newest `fetchedAt` in the list, so an
 * empty list simply has nothing to report.
 */
export function RadarHeader({
  updatedLabel,
  onRefresh,
  pending,
}: {
  updatedLabel: string | null;
  onRefresh: () => void;
  pending: boolean;
}) {
  return (
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
          What your topics turned up, ranked by how well each one matches.
        </p>
      </div>
      <div className="flex items-center gap-2">
        {updatedLabel && (
          <div className="hidden font-mono text-[11.5px] text-muted-foreground sm:block">
            {updatedLabel}
          </div>
        )}
        <Button
          variant="outline"
          size="sm"
          onClick={onRefresh}
          disabled={pending}
        >
          <RefreshCw className={cn("size-3.5", pending && "animate-spin")} />
          <span className="hidden sm:inline">Refresh</span>
        </Button>
      </div>
    </header>
  );
}
