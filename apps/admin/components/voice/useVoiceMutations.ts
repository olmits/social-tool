"use client";

import { useState, useTransition } from "react";
import { toastManager } from "@/components/ui/toast";
import {
  deleteVoiceProfileAction,
  updateVoiceProfileAction,
} from "@/lib/api/actions";
import type { VoiceProfileResponse } from "@/lib/api/types";

/**
 * The list-level voice mutations: the enabled toggle, making one the default,
 * and delete. Create and edit belong to the form (`useVoiceForm`), which owns
 * their validation and surfaces failures inline rather than as a toast.
 *
 * `pendingId` is the profile mid-flight, so a row can disable just its own
 * controls instead of freezing the whole list.
 */
export function useVoiceMutations() {
  const [pendingId, setPendingId] = useState<string | null>(null);
  const [, startTransition] = useTransition();

  const setEnabled = (profile: VoiceProfileResponse, enabled: boolean) => {
    setPendingId(profile.id);
    startTransition(async () => {
      // A sparse patch: name and instructions are left untouched, so a
      // concurrent edit of either isn't overwritten by flipping this flag.
      const result = await updateVoiceProfileAction(profile.id, { enabled });
      setPendingId(null);
      if (!result.ok) {
        toastManager.add({
          title: "Couldn't update voice",
          description: result.message,
          type: "error",
        });
      }
    });
  };

  /**
   * Only ever sets the default, never clears it: the API moves it off whichever
   * profile held it, so there is no state where the user has to clear first.
   */
  const makeDefault = (profile: VoiceProfileResponse) => {
    setPendingId(profile.id);
    startTransition(async () => {
      const result = await updateVoiceProfileAction(profile.id, {
        isDefault: true,
      });
      setPendingId(null);
      if (result.ok) {
        toastManager.add({
          title: "Default voice set",
          description: `New drafts use ${profile.name} unless you pick another.`,
        });
      } else {
        toastManager.add({
          title: "Couldn't set the default",
          description: result.message,
          type: "error",
        });
      }
    });
  };

  const remove = (profile: VoiceProfileResponse, onDone: () => void) => {
    setPendingId(profile.id);
    startTransition(async () => {
      const result = await deleteVoiceProfileAction(profile.id);
      setPendingId(null);
      if (result.ok) {
        toastManager.add({
          title: "Voice deleted",
          description: `Drafts already written in ${profile.name} are kept.`,
        });
        onDone();
      } else {
        toastManager.add({
          title: "Couldn't delete voice",
          description: result.message,
          type: "error",
        });
      }
    });
  };

  return { setEnabled, makeDefault, remove, pendingId };
}
