"use client";

import { Pencil, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Switch } from "@/components/ui/switch";
import { SOURCE_META, sourceLabel } from "@/lib/api/mappers";
import type { SignalSource, TopicResponse } from "@/lib/api/types";
import { cn } from "@/lib/utils";

/** One topic: its name, the sources it's queried on, and its enabled toggle. */
export function TopicRow({
  topic,
  onToggle,
  onEdit,
  onDelete,
  pending,
}: {
  topic: TopicResponse;
  onToggle: (enabled: boolean) => void;
  onEdit: () => void;
  onDelete: () => void;
  pending: boolean;
}) {
  const sources = Object.keys(topic.queries) as SignalSource[];

  return (
    <div
      className={cn(
        "flex flex-wrap items-center gap-x-3.5 gap-y-2.5 border-b border-border px-4.5 py-3.5 last:border-b-0 hover:bg-muted/30",
        !topic.enabled && "opacity-60",
      )}
    >
      <div className="min-w-[160px] flex-1">
        <div className="text-[13.5px] font-semibold leading-snug">
          {topic.name}
        </div>
        <div className="mt-1.5 flex flex-wrap items-center gap-1.5">
          {sources.length === 0 ? (
            <span className="text-[11.5px] text-muted-foreground">
              No queries yet — nothing is polled for this topic.
            </span>
          ) : (
            sources.map((source) => (
              <span
                key={source}
                title={topic.queries[source]}
                className={cn(
                  "inline-flex items-center gap-1.5 rounded px-1.5 py-0.5 text-[10.5px] font-semibold",
                  SOURCE_META[source].badgeBg,
                  SOURCE_META[source].badgeText,
                )}
              >
                <span
                  className="size-1.5 rounded-full"
                  style={{ background: SOURCE_META[source].dot }}
                />
                {sourceLabel(source)}
                <span className="font-normal opacity-70">
                  {topic.queries[source]}
                </span>
              </span>
            ))
          )}
        </div>
      </div>

      <div className="flex items-center gap-2.5">
        <div className="flex items-center gap-2 text-[11.5px] text-muted-foreground">
          <Switch
            checked={topic.enabled}
            onCheckedChange={onToggle}
            disabled={pending}
            aria-label={`Poll for ${topic.name}`}
          />
          {topic.enabled ? "On" : "Off"}
        </div>
        <Button
          variant="ghost"
          size="icon-sm"
          onClick={onEdit}
          title="Edit topic"
        >
          <Pencil className="size-3.5" />
        </Button>
        <Button
          variant="ghost"
          size="icon-sm"
          onClick={onDelete}
          title="Delete topic"
          className="text-muted-foreground hover:text-destructive"
        >
          <Trash2 className="size-3.5" />
        </Button>
      </div>
    </div>
  );
}
