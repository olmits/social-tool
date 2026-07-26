"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogBackdrop,
  DialogDescription,
  DialogPopup,
  DialogPortal,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";

interface ScheduleDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Called with an ISO-8601 instant (UTC) when the user confirms. */
  onConfirm: (scheduledAt: string) => void;
  pending: boolean;
}

/**
 * Picks a publish time for an approved draft. The native `datetime-local` value
 * is local time; it's converted to an ISO instant for the API.
 */
export function ScheduleDialog({
  open,
  onOpenChange,
  onConfirm,
  pending,
}: ScheduleDialogProps) {
  const [value, setValue] = useState("");

  const submit = () => {
    if (!value) return;
    onConfirm(new Date(value).toISOString());
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogPortal>
        <DialogBackdrop />
        <DialogPopup>
          <div className="mb-4 flex flex-col gap-1">
            <DialogTitle>Schedule post</DialogTitle>
            <DialogDescription>
              Pick when this approved draft should publish. Time is your local
              timezone.
            </DialogDescription>
          </div>
          <Input
            type="datetime-local"
            value={value}
            onChange={(event) => setValue(event.target.value)}
          />
          <div className="mt-5 flex justify-end gap-2.5">
            <Button variant="outline" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
            <Button onClick={submit} disabled={pending || !value}>
              Schedule
            </Button>
          </div>
        </DialogPopup>
      </DialogPortal>
    </Dialog>
  );
}
