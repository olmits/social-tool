"use client";

import { ChevronDown } from "lucide-react";
import type { TopicResponse } from "@/lib/api/types";

/**
 * Narrows the radar to one topic. Drives the `topicId` query param, so the
 * filtering happens server-side and the filtered view is a linkable URL.
 */
export function TopicFilter({
  topics,
  value,
  onChange,
  disabled,
}: {
  topics: TopicResponse[];
  value: string | null;
  onChange: (topicId: string | null) => void;
  disabled: boolean;
}) {
  return (
    <div className="relative">
      <select
        aria-label="Filter by topic"
        value={value ?? ""}
        disabled={disabled}
        onChange={(e) => onChange(e.target.value || null)}
        className="h-8.5 appearance-none rounded-lg border border-border bg-background pl-2.5 pr-7 text-[13px] outline-none focus:border-neutral-400 disabled:opacity-50 dark:focus:border-neutral-500"
      >
        <option value="">All topics</option>
        {topics.map((topic) => (
          <option key={topic.id} value={topic.id}>
            {topic.name}
            {topic.enabled ? "" : " (off)"}
          </option>
        ))}
      </select>
      <ChevronDown className="pointer-events-none absolute right-2 top-1/2 size-3.5 -translate-y-1/2 text-muted-foreground" />
    </div>
  );
}
