"use client";

import { LayoutGrid, List, Search } from "lucide-react";
import type { SignalSource, TopicResponse } from "@/lib/api/types";
import { cn } from "@/lib/utils";
import { SourceChips } from "./SourceChips";
import { TopicFilter } from "./TopicFilter";
import type { RadarLayout } from "./useRadarFilters";

/**
 * The filter row: search, topic, source, and the table/cards toggle. Topic and
 * source navigate (server-side filtering); search and layout are local.
 */
export function RadarFilters({
  topics,
  topicId,
  source,
  query,
  onQueryChange,
  onTopicChange,
  onSourceChange,
  layout,
  onLayoutChange,
  pending,
}: {
  topics: TopicResponse[];
  topicId: string | null;
  source: SignalSource | null;
  query: string;
  onQueryChange: (query: string) => void;
  onTopicChange: (topicId: string | null) => void;
  onSourceChange: (source: SignalSource | null) => void;
  layout: RadarLayout;
  onLayoutChange: (layout: RadarLayout) => void;
  pending: boolean;
}) {
  return (
    <div className="mb-3.5 flex flex-wrap items-center gap-2.5">
      <div
        className="flex h-8.5 max-w-[340px] flex-1 items-center gap-2 rounded-lg border border-border bg-background px-2.5"
        style={{ minWidth: 220 }}
      >
        <Search className="size-3.5 text-muted-foreground" />
        <input
          value={query}
          onChange={(e) => onQueryChange(e.target.value)}
          placeholder="Search signals…"
          className="w-full bg-transparent text-[13px] outline-none placeholder:text-muted-foreground"
        />
      </div>

      <TopicFilter
        topics={topics}
        value={topicId}
        onChange={onTopicChange}
        disabled={pending}
      />

      <SourceChips
        value={source}
        onChange={onSourceChange}
        disabled={pending}
      />

      <div className="flex-1" />
      <div className="hidden items-center overflow-hidden rounded-lg border border-border md:flex">
        <button
          type="button"
          onClick={() => onLayoutChange("table")}
          className={cn(
            "flex h-8.5 items-center gap-1.5 px-2.5 text-xs font-medium",
            layout === "table" ? "bg-muted" : "hover:bg-muted/60",
          )}
        >
          <List className="size-3.5" /> Table
        </button>
        <button
          type="button"
          onClick={() => onLayoutChange("cards")}
          className={cn(
            "flex h-8.5 items-center gap-1.5 border-l border-border px-2.5 text-xs font-medium",
            layout === "cards" ? "bg-muted" : "hover:bg-muted/60",
          )}
        >
          <LayoutGrid className="size-3.5" /> Cards
        </button>
      </div>
    </div>
  );
}
