import { Radar, SearchX, Tags } from "lucide-react";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import type { RadarEmptyReason } from "./radarDisplay";

/**
 * Why the list is empty — three genuinely different situations, and telling them
 * apart is the whole point:
 *
 * - `no-topics`: the radar polls from the topic list, so with no topic it stores
 *   nothing at all. Empty by design, and the fix is a page away.
 * - `no-signals`: topics exist but the poller hasn't stored anything for them yet.
 * - `filtered`: there are signals, just none matching the current filters.
 */
export function RadarEmpty({ reason }: { reason: RadarEmptyReason }) {
  if (reason === "filtered") {
    return (
      <div className="flex flex-col items-center rounded-xl border border-dashed border-border px-6 py-10 text-center">
        <SearchX className="mb-3 size-5 text-muted-foreground" />
        <p className="text-sm text-muted-foreground">
          No signals match your filters.
        </p>
      </div>
    );
  }

  if (reason === "no-signals") {
    return (
      <div className="flex flex-col items-center rounded-xl border border-dashed border-border px-6 py-12 text-center">
        <div className="mb-4 flex size-12 items-center justify-center rounded-2xl bg-muted">
          <Radar className="size-6 text-muted-foreground" strokeWidth={1.8} />
        </div>
        <h2 className="mb-1.5 text-[15px] font-semibold tracking-tight">
          Nothing collected yet
        </h2>
        <p className="max-w-[420px] text-[13px] text-muted-foreground">
          Your topics are set up, but the poller hasn&apos;t stored anything for
          them yet. It runs on a schedule — check back after the next pass.
        </p>
      </div>
    );
  }

  return (
    <div className="flex flex-col items-center rounded-xl border border-dashed border-border px-6 py-12 text-center">
      <div className="mb-4 flex size-12 items-center justify-center rounded-2xl bg-muted">
        <Tags className="size-6 text-muted-foreground" strokeWidth={1.8} />
      </div>
      <h2 className="mb-1.5 text-[15px] font-semibold tracking-tight">
        No topics to watch
      </h2>
      <p className="mb-5 max-w-[420px] text-[13px] text-muted-foreground">
        The radar polls per topic, so it collects nothing until there is one.
        This isn&apos;t a fault — add a topic and signals arrive on the next
        run.
      </p>
      <Button render={<Link href="/topics" />} nativeButton={false}>
        Add a topic
      </Button>
    </div>
  );
}
