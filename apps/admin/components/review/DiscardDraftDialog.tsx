"use client";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogBackdrop,
  DialogDescription,
  DialogPopup,
  DialogPortal,
  DialogTitle,
} from "@/components/ui/dialog";

interface DiscardDraftDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onConfirm: () => void;
  pending: boolean;
}

/** Confirms discarding a draft. The draft is kept, marked `DISCARDED`, not deleted. */
export function DiscardDraftDialog({
  open,
  onOpenChange,
  onConfirm,
  pending,
}: DiscardDraftDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogPortal>
        <DialogBackdrop />
        <DialogPopup>
          <div className="mb-4 flex flex-col gap-1">
            <DialogTitle>Discard draft?</DialogTitle>
            <DialogDescription>
              It leaves the review queue and can&apos;t be approved or
              scheduled. You&apos;ll still find it under the Discarded filter.
            </DialogDescription>
          </div>
          <div className="mt-5 flex justify-end gap-2.5">
            <Button variant="outline" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
            <Button
              variant="destructive"
              onClick={onConfirm}
              disabled={pending}
            >
              Discard
            </Button>
          </div>
        </DialogPopup>
      </DialogPortal>
    </Dialog>
  );
}
