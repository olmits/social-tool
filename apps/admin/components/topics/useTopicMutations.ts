"use client";

import { useState, useTransition } from "react";
import { toastManager } from "@/components/ui/toast";
import { deleteTopicAction, updateTopicAction } from "@/lib/api/actions";
import type { TopicResponse } from "@/lib/api/types";

/**
 * The list-level topic mutations: the enabled toggle and delete. Create and edit
 * belong to the form (`useTopicForm`), which owns their validation and surfaces
 * failures inline rather than as a toast.
 *
 * `pendingId` is the topic mid-flight, so a row can disable just its own toggle
 * instead of freezing the whole list.
 */
export function useTopicMutations() {
  const [pendingId, setPendingId] = useState<string | null>(null);
  const [, startTransition] = useTransition();

  const setEnabled = (topic: TopicResponse, enabled: boolean) => {
    setPendingId(topic.id);
    startTransition(async () => {
      // A sparse patch: name and queries are left untouched, so a concurrent
      // edit of either isn't overwritten by flipping this flag.
      const result = await updateTopicAction(topic.id, { enabled });
      setPendingId(null);
      if (!result.ok) {
        toastManager.add({
          title: "Couldn't update topic",
          description: result.message,
          type: "error",
        });
      }
    });
  };

  const remove = (topic: TopicResponse, onDone: () => void) => {
    setPendingId(topic.id);
    startTransition(async () => {
      const result = await deleteTopicAction(topic.id);
      setPendingId(null);
      if (result.ok) {
        toastManager.add({
          title: "Topic deleted",
          description: `The radar no longer polls for ${topic.name}.`,
        });
        onDone();
      } else {
        toastManager.add({
          title: "Couldn't delete topic",
          description: result.message,
          type: "error",
        });
      }
    });
  };

  return { setEnabled, remove, pendingId };
}
