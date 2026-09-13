"use client";

import { Plus, Radar } from "lucide-react";
import { Button } from "@/components/ui/button";

/**
 * First thing a new install sees. The radar works from the topic list — with no
 * topic there is nothing to poll and no signal is ever stored — so this says that
 * outright rather than leaving an empty Radar page looking broken.
 */
export function TopicsEmpty({ onCreate }: { onCreate: () => void }) {
  return (
    <div className="flex flex-col items-center rounded-xl border border-dashed border-border px-6 py-12 text-center">
      <div className="mb-4 flex size-12 items-center justify-center rounded-2xl bg-muted">
        <Radar className="size-6 text-muted-foreground" strokeWidth={1.8} />
      </div>
      <h2 className="mb-1.5 text-[15px] font-semibold tracking-tight">
        No topics yet
      </h2>
      <p className="mb-5 max-w-[420px] text-[13px] text-muted-foreground">
        The trend radar polls per topic, so it finds nothing until there is one.
        Add a topic with a query or two and signals start arriving on the next
        run.
      </p>
      <Button onClick={onCreate}>
        <Plus className="size-4" />
        New topic
      </Button>
    </div>
  );
}
