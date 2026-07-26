"use client";

import { useTransition } from "react";
import { toastManager } from "@/components/ui/toast";
import { approveDraftAction, scheduleDraftAction } from "@/lib/api/actions";

/**
 * Approve / schedule mutations for the review board. Each calls its server
 * action (which revalidates the `drafts` tag for read-your-writes), toasts the
 * outcome, and reports a shared `pending` flag for disabling the action buttons.
 * The API error message (e.g. the 422 disclosure text) is surfaced on failure.
 */
export function useReviewMutations() {
  const [pending, startTransition] = useTransition();

  const approve = (draftId: string) => {
    startTransition(async () => {
      const result = await approveDraftAction(draftId);
      if (result.ok) {
        toastManager.add({ title: "Draft approved" });
      } else {
        toastManager.add({
          title: "Couldn't approve draft",
          description: result.message,
          type: "error",
        });
      }
    });
  };

  const schedule = (draftId: string, scheduledAt: string) => {
    startTransition(async () => {
      const result = await scheduleDraftAction(draftId, scheduledAt);
      if (result.ok) {
        toastManager.add({ title: "Draft scheduled" });
      } else {
        toastManager.add({
          title: "Couldn't schedule draft",
          description: result.message,
          type: "error",
        });
      }
    });
  };

  return { approve, schedule, pending };
}
