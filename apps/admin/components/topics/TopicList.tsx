"use client";

import type { TopicResponse } from "@/lib/api/types";
import { TopicRow } from "./TopicRow";

/** The topic rows in a bordered card. Empty handled by the caller (`TopicsEmpty`). */
export function TopicList({
  topics,
  onToggle,
  onEdit,
  onDelete,
  pendingId,
}: {
  topics: TopicResponse[];
  onToggle: (topic: TopicResponse, enabled: boolean) => void;
  onEdit: (topic: TopicResponse) => void;
  onDelete: (topic: TopicResponse) => void;
  pendingId: string | null;
}) {
  return (
    <div className="overflow-hidden rounded-xl border border-border">
      {topics.map((topic) => (
        <TopicRow
          key={topic.id}
          topic={topic}
          onToggle={(enabled) => onToggle(topic, enabled)}
          onEdit={() => onEdit(topic)}
          onDelete={() => onDelete(topic)}
          pending={pendingId === topic.id}
        />
      ))}
    </div>
  );
}
