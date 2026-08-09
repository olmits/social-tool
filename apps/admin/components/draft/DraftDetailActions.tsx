"use client";

import { Check } from "lucide-react";
import { Button } from "@/components/ui/button";

interface DraftDetailActionsProps {
  onApprove?: () => void;
  onSchedule?: () => void;
  onEdit?: () => void;
  onDiscard?: () => void;
  /** Disables all buttons while a mutation is in flight. */
  pending?: boolean;
}

/**
 * Action row for a draft: discard / edit / schedule / approve. A button is
 * disabled when its handler is absent, which means the action doesn't apply to
 * the draft's current state (edit and discard need `DRAFT` or `APPROVED`, approve
 * needs `DRAFT`, schedule needs `APPROVED`) — or while `pending`.
 */
export function DraftDetailActions({
  onApprove,
  onSchedule,
  onEdit,
  onDiscard,
  pending,
}: DraftDetailActionsProps) {
  return (
    <div className="mt-5 flex items-center gap-2.5">
      <Button
        variant="outline"
        className="text-destructive hover:bg-destructive/10"
        onClick={onDiscard}
        disabled={pending || !onDiscard}
      >
        Discard
      </Button>
      <div className="flex-1" />
      <Button variant="outline" onClick={onEdit} disabled={pending || !onEdit}>
        Edit
      </Button>
      <Button
        variant="outline"
        onClick={onSchedule}
        disabled={pending || !onSchedule}
      >
        Schedule
      </Button>
      <Button onClick={onApprove} disabled={pending || !onApprove}>
        <Check className="size-3.5" strokeWidth={2.5} />
        Approve
      </Button>
    </div>
  );
}
