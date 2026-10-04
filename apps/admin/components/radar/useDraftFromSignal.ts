"use client";

import { useRouter } from "next/navigation";
import { useState, useTransition } from "react";
import { toastManager } from "@/components/ui/toast";
import { useAccountState } from "@/context/account-context";
import { generateDraftAction } from "@/lib/api/actions";
import type { SignalResponse, VoiceProfileResponse } from "@/lib/api/types";

/**
 * Generating a draft from a signal: which account and voice it will use, whether
 * it can run at all, and the call itself.
 *
 * The platform is taken from the selected account and never chosen separately —
 * the API rejects a mismatch with a 409, so offering the choice would only be a
 * way to get that error.
 */
export function useDraftFromSignal(voiceProfiles: VoiceProfileResponse[]) {
  const router = useRouter();
  const { accounts, selectedId } = useAccountState();
  const account =
    accounts.find((candidate) => candidate.id === selectedId) ?? null;

  // Only enabled profiles can be drafted with; a disabled one is a 409.
  const usableVoices = voiceProfiles.filter((profile) => profile.enabled);
  const defaultVoice =
    usableVoices.find((profile) => profile.isDefault) ??
    usableVoices[0] ??
    null;

  const [voiceId, setVoiceId] = useState<string | null>(
    defaultVoice?.id ?? null,
  );
  const [pending, startTransition] = useTransition();

  /**
   * Why the Draft button can't be used, or null when it can. Both reasons are
   * fixed on another screen, so the text names it rather than just greying out.
   */
  const disabledReason = !account
    ? "Connect an account before drafting"
    : usableVoices.length === 0
      ? "Create a voice profile before drafting"
      : null;

  const generate = (signal: SignalResponse, onSuccess: () => void) => {
    if (!account) return;

    startTransition(async () => {
      const result = await generateDraftAction({
        accountId: account.id,
        signalId: signal.id,
        platform: account.platform,
        voiceProfileId: voiceId,
      });

      if (!result.ok) {
        toastManager.add({
          title: "Couldn't draft from this signal",
          description: result.message,
          type: "error",
        });
        return;
      }

      toastManager.add({
        title: "Draft written",
        description: "Waiting for you in Review.",
      });
      onSuccess();
      // The one place in the panel that navigates after a mutation: a draft you
      // cannot see is not obviously a result, and Review is where it is acted on.
      router.push("/review");
    });
  };

  return {
    account,
    usableVoices,
    voiceId,
    setVoiceId,
    disabledReason,
    pending,
    generate,
  };
}
