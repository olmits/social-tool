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
 * Confirms deleting a voice. A real delete, but a narrow one: drafts already
 * written in it are untouched, since nothing links them back. Switching the
 * voice off is the reversible way to retire it, and the copy says so.
 */
export function DeleteVoiceDialog({
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
              Drafts already written in it are kept. To stop using it without
              losing what it says, switch it off instead.
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
