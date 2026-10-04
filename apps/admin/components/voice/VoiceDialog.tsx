"use client";

import { Loader2 } from "lucide-react";
import { ControlledTextArea } from "@/components/form/ControlledTextArea";
import { ControlledTextField } from "@/components/form/ControlledTextField";
import { FormError } from "@/components/form/FormError";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogBackdrop,
  DialogDescription,
  DialogPopup,
  DialogPortal,
  DialogTitle,
} from "@/components/ui/dialog";
import type { VoiceProfileResponse } from "@/lib/api/types";
import { useVoiceForm } from "./useVoiceForm";
import { INSTRUCTIONS_HINT, INSTRUCTIONS_PLACEHOLDER } from "./voiceDisplay";

/**
 * Create/edit a voice. There is no platform field: a voice is how you sound,
 * while how long a post may be is the platform's business and drafting already
 * knows that from the account.
 *
 * Mount this only while open, keyed by profile, so the form picks up fresh
 * defaults instead of holding the previous profile's values.
 */
export function VoiceDialog({
  profile,
  onClose,
}: {
  /** The profile being edited, or null to create one. */
  profile: VoiceProfileResponse | null;
  onClose: () => void;
}) {
  const { control, rules, onSubmit, isPending, rootError } = useVoiceForm(
    profile,
    onClose,
  );

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogPortal>
        <DialogBackdrop />
        <DialogPopup>
          <div className="mb-4 flex flex-col gap-1">
            <DialogTitle>{profile ? "Edit voice" : "New voice"}</DialogTitle>
            <DialogDescription>
              How drafts should sound. Keep several and pick one each time you
              draft from a signal.
            </DialogDescription>
          </div>

          <form onSubmit={onSubmit} className="flex flex-col gap-4">
            <ControlledTextField
              control={control}
              name="name"
              label="Name"
              placeholder="Technical"
              rules={rules.name}
            />

            <ControlledTextArea
              control={control}
              name="instructions"
              label="Instructions"
              hint={INSTRUCTIONS_HINT}
              placeholder={INSTRUCTIONS_PLACEHOLDER}
              rows={10}
              rules={rules.instructions}
            />

            <FormError message={rootError} />

            <div className="mt-1 flex justify-end gap-2">
              <Button
                type="button"
                variant="ghost"
                onClick={onClose}
                disabled={isPending}
              >
                Cancel
              </Button>
              <Button type="submit" disabled={isPending}>
                {isPending && <Loader2 className="size-4 animate-spin" />}
                {profile ? "Save voice" : "Create voice"}
              </Button>
            </div>
          </form>
        </DialogPopup>
      </DialogPortal>
    </Dialog>
  );
}
