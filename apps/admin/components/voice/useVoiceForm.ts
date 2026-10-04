"use client";

import { useTransition } from "react";
import { type Control, type RegisterOptions, useForm } from "react-hook-form";
import { toastManager } from "@/components/ui/toast";
import {
  createVoiceProfileAction,
  updateVoiceProfileAction,
} from "@/lib/api/actions";
import type { VoiceProfileResponse } from "@/lib/api/types";
import { VOICE_INSTRUCTIONS_MAX, VOICE_NAME_MAX } from "./voiceDisplay";

export interface VoiceFormValues {
  name: string;
  instructions: string;
}

interface VoiceFieldRules {
  name: RegisterOptions<VoiceFormValues, "name">;
  instructions: RegisterOptions<VoiceFormValues, "instructions">;
}

export interface UseVoiceForm {
  control: Control<VoiceFormValues>;
  onSubmit: (event?: React.BaseSyntheticEvent) => void;
  rules: VoiceFieldRules;
  isPending: boolean;
  /** Server-side failure (409 duplicate name, 400 invalid), if any. */
  rootError?: string;
}

/**
 * Create/edit voice form: field setup, validation mirroring the API's own rules,
 * and the submit that calls the right action for the mode. Pass the profile
 * being edited, or null to create.
 */
export function useVoiceForm(
  profile: VoiceProfileResponse | null,
  onClose: () => void,
): UseVoiceForm {
  const [isPending, startTransition] = useTransition();

  const {
    control,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<VoiceFormValues>({
    mode: "onBlur",
    defaultValues: {
      name: profile?.name ?? "",
      instructions: profile?.instructions ?? "",
    },
  });

  const onSubmit = handleSubmit((values) => {
    const name = values.name.trim();
    const instructions = values.instructions.trim();

    startTransition(async () => {
      // Create sends `enabled: null`, which the API reads as enabled — a voice
      // is created to be used — and `isDefault: null`, which is false: the first
      // profile is not silently promoted, because "I chose this one" and "it was
      // the only one at the time" should not look the same later.
      //
      // Edit leaves both out: the list's toggle and Make default own them, and a
      // sparse patch is what keeps this form from reverting either.
      const result = profile
        ? await updateVoiceProfileAction(profile.id, { name, instructions })
        : await createVoiceProfileAction({
            name,
            instructions,
            enabled: null,
            isDefault: null,
          });

      if (!result.ok) {
        setError("root", { message: result.message });
        return;
      }
      toastManager.add({
        title: profile ? "Voice saved" : "Voice created",
        description: result.data.name,
      });
      onClose();
    });
  });

  const rules: VoiceFieldRules = {
    name: {
      required: "Name is required",
      maxLength: {
        value: VOICE_NAME_MAX,
        message: `Name must be at most ${VOICE_NAME_MAX} characters`,
      },
    },
    instructions: {
      required: "Instructions are required",
      maxLength: {
        value: VOICE_INSTRUCTIONS_MAX,
        message: `Instructions must be at most ${VOICE_INSTRUCTIONS_MAX} characters`,
      },
    },
  };

  return {
    control,
    onSubmit,
    rules,
    isPending,
    rootError: errors.root?.message,
  };
}
