"use client";

import { TriangleAlert } from "lucide-react";
import { ControlledCheckbox } from "@/components/form/ControlledCheckbox";
import { ControlledTextArea } from "@/components/form/ControlledTextArea";
import { ControlledTextField } from "@/components/form/ControlledTextField";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogBackdrop,
  DialogDescription,
  DialogPopup,
  DialogPortal,
  DialogTitle,
} from "@/components/ui/dialog";
import type { DraftResponse, EditDraftCommand } from "@/lib/api/types";
import { cn } from "@/lib/utils";
import type { EditDraftValues } from "./useEditDraftForm";
import { useEditDraftForm } from "./useEditDraftForm";

interface EditDraftDialogProps {
  draft: DraftResponse;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onConfirm: (command: EditDraftCommand) => void;
  pending: boolean;
}

/**
 * Edits a draft's body during review. Mount with `key={draft.id}` — the form seeds
 * its `defaultValues` from the draft once, on mount.
 */
export function EditDraftDialog({
  draft,
  open,
  onOpenChange,
  onConfirm,
  pending,
}: EditDraftDialogProps) {
  const { control, submit, chars, limit, disclosureMissing, contentRules } =
    useEditDraftForm(draft);

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogPortal>
        <DialogBackdrop />
        <DialogPopup>
          <div className="mb-4 flex flex-col gap-1">
            <DialogTitle>Edit draft</DialogTitle>
            <DialogDescription>
              {draft.status === "APPROVED"
                ? "This draft is approved — saving sends it back for re-review."
                : "Changes are saved to the draft, not published."}
            </DialogDescription>
          </div>

          <form onSubmit={submit(onConfirm)} className="flex flex-col gap-4">
            <ControlledTextArea<EditDraftValues, "content">
              control={control}
              name="content"
              label="Content"
              placeholder="What do you want to post?"
              rules={contentRules}
            />
            <div
              className={cn(
                "-mt-2 text-right text-xs tabular-nums",
                chars > limit ? "text-destructive" : "text-muted-foreground",
              )}
            >
              {chars} / {limit} chars
            </div>

            <ControlledTextField<EditDraftValues, "affiliateLinks">
              control={control}
              name="affiliateLinks"
              label="Affiliate link"
              hint="Leave empty if this post promotes nothing."
              placeholder="https://example.com/ref?tag=you"
            />

            <ControlledCheckbox<EditDraftValues, "disclosureIncluded">
              control={control}
              name="disclosureIncluded"
              label="Disclosure included"
              hint="Required before approving a post with an affiliate link."
            />

            {disclosureMissing && (
              <div className="flex items-start gap-2 rounded-lg border border-amber-200 bg-amber-50 p-2.5 text-xs text-amber-700 dark:border-amber-900 dark:bg-amber-950/30 dark:text-amber-400">
                <TriangleAlert className="mt-px size-3.5 shrink-0" />
                <span>
                  You can save this, but approval will be blocked until the
                  disclosure is ticked.
                </span>
              </div>
            )}

            <div className="mt-1 flex justify-end gap-2.5">
              <Button
                type="button"
                variant="outline"
                onClick={() => onOpenChange(false)}
              >
                Cancel
              </Button>
              <Button type="submit" disabled={pending}>
                Save changes
              </Button>
            </div>
          </form>
        </DialogPopup>
      </DialogPortal>
    </Dialog>
  );
}
