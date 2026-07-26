"use client";

import { useState, useTransition } from "react";
import { toastManager } from "@/components/ui/toast";
import { useAccountState } from "@/context/account-context";
import { createDraftAction } from "@/lib/api/actions";

/**
 * Compose-draft logic: holds the content and creates a draft for the currently
 * selected account (platform taken from that account). On success the draft
 * lands in the review list as DRAFT via the action's `drafts` tag revalidation.
 */
export function useComposeDraft() {
  const { accounts, selectedId } = useAccountState();
  const account = accounts.find((a) => a.id === selectedId) ?? null;
  const [content, setContent] = useState("");
  const [pending, startTransition] = useTransition();

  const submit = (onSuccess: () => void) => {
    if (!account || !content.trim()) return;
    startTransition(async () => {
      const result = await createDraftAction({
        accountId: account.id,
        platform: account.platform,
        content: content.trim(),
      });
      if (result.ok) {
        toastManager.add({ title: "Draft created" });
        setContent("");
        onSuccess();
      } else {
        toastManager.add({
          title: "Couldn't create draft",
          description: result.message,
          type: "error",
        });
      }
    });
  };

  return { account, content, setContent, pending, submit };
}
