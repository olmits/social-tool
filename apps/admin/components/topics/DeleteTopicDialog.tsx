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

/**
 * Confirms deleting a topic. Unlike discarding a draft this is a real delete —
 * signals already collected survive, but lose their topic. Switching the topic off
 * is the reversible way to stop polling, and the copy says so.
 */
export function DeleteTopicDialog({
  name,
  onCancel,
  onConfirm,
  pending,
}: {
  name: string;
  onCancel: () => void;
  onConfirm: () => void;
  pending: boolean;
}) {
  return (
    <Dialog open onOpenChange={(open) => !open && onCancel()}>
      <DialogPortal>
        <DialogBackdrop />
        <DialogPopup>
          <div className="mb-4 flex flex-col gap-1">
            <DialogTitle>Delete “{name}”?</DialogTitle>
            <DialogDescription>
              The radar stops polling for it. Signals it already found are kept,
              but they lose their topic. To pause it instead, switch it off.
            </DialogDescription>
          </div>
          <div className="mt-5 flex justify-end gap-2.5">
            <Button variant="outline" onClick={onCancel} disabled={pending}>
              Cancel
            </Button>
            <Button
              variant="destructive"
              onClick={onConfirm}
              disabled={pending}
            >
              Delete
            </Button>
          </div>
        </DialogPopup>
      </DialogPortal>
    </Dialog>
  );
}
