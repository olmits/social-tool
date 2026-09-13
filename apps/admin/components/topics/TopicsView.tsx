"use client";

import { Plus } from "lucide-react";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import type { TopicResponse } from "@/lib/api/types";
import { DeleteTopicDialog } from "./DeleteTopicDialog";
import { TopicDialog } from "./TopicDialog";
import { TopicList } from "./TopicList";
import { TopicsEmpty } from "./TopicsEmpty";
import { useTopicMutations } from "./useTopicMutations";

/**
 * Topics screen: the list, its two dialogs, and the header. Topics configure the
 * radar — the poller works from this list — so this sits above Trend Radar in the
 * nav, and an empty list is the reason an empty radar is not a bug.
 */
export function TopicsView({ topics }: { topics: TopicResponse[] }) {
  // `null` closed; `{ topic: null }` creating; `{ topic }` editing. A plain
  // `TopicResponse | null` couldn't tell "create" from "closed".
  const [form, setForm] = useState<{ topic: TopicResponse | null } | null>(
    null,
  );
  const [deleting, setDeleting] = useState<TopicResponse | null>(null);
  const { setEnabled, remove, pendingId } = useTopicMutations();

  const enabledCount = topics.filter((topic) => topic.enabled).length;

  return (
    <div>
      <header className="sticky top-0 z-20 flex items-end justify-between gap-4 border-b border-border bg-background/85 px-4 py-4.5 backdrop-blur-sm md:px-7">
        <div>
          <h1 className="text-xl font-semibold tracking-tight">Topics</h1>
          <p className="mt-1 hidden text-[13px] text-muted-foreground sm:block">
            What the radar looks for, and how each source is queried for it.
          </p>
        </div>
        <div className="flex items-center gap-2.5">
          <span className="hidden font-mono text-[11.5px] text-muted-foreground sm:block">
            {enabledCount} of {topics.length} on
          </span>
          <Button size="sm" onClick={() => setForm({ topic: null })}>
            <Plus className="size-3.5" />
            <span className="hidden sm:inline">New topic</span>
          </Button>
        </div>
      </header>

      <div className="max-w-[1180px] px-4 py-5.5 pb-10 md:px-7">
        {topics.length === 0 ? (
          <TopicsEmpty onCreate={() => setForm({ topic: null })} />
        ) : (
          <TopicList
            topics={topics}
            onToggle={setEnabled}
            onEdit={(topic) => setForm({ topic })}
            onDelete={setDeleting}
            pendingId={pendingId}
          />
        )}
      </div>

      {/* Mounted only while open, and keyed by topic, so the form takes fresh
          defaults rather than the previously edited topic's values. */}
      {form && (
        <TopicDialog
          key={form.topic?.id ?? "new"}
          topic={form.topic}
          onClose={() => setForm(null)}
        />
      )}

      {deleting && (
        <DeleteTopicDialog
          name={deleting.name}
          onCancel={() => setDeleting(null)}
          onConfirm={() => remove(deleting, () => setDeleting(null))}
          pending={pendingId === deleting.id}
        />
      )}
    </div>
  );
}
