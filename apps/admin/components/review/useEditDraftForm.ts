"use client";

import { useForm } from "react-hook-form";
import { charLimit } from "@/lib/api/mappers";
import type { DraftResponse, EditDraftCommand } from "@/lib/api/types";

export interface EditDraftValues {
  content: string;
  affiliateLinks: string;
  disclosureIncluded: boolean;
}

/**
 * Form state for editing a draft's body, seeded from the draft itself. The caller
 * owns the mutation — `submit` hands back an {@link EditDraftCommand} rather than
 * calling the server action, so the review board's shared `pending` transition
 * still drives every action button.
 *
 * The dialog is remounted per draft (`key={draft.id}`), so `defaultValues` are
 * enough — no `reset` on selection change.
 */
export function useEditDraftForm(draft: DraftResponse) {
  const { control, handleSubmit, watch } = useForm<EditDraftValues>({
    defaultValues: {
      content: draft.content,
      affiliateLinks: draft.affiliateLinks ?? "",
      disclosureIncluded: draft.disclosureIncluded,
    },
  });

  const limit = charLimit(draft.platform);
  const content = watch("content");
  const affiliateLinks = watch("affiliateLinks");
  const disclosureIncluded = watch("disclosureIncluded");

  // Mirrors the API's approve-time gate so the reviewer sees it before approving.
  // Saving in this state is legal — only approving is blocked (422).
  const disclosureMissing =
    Boolean(affiliateLinks.trim()) && !disclosureIncluded;

  const contentRules = {
    required: "Content is required.",
    validate: (value: string) =>
      value.length <= limit || `Over the ${limit}-character limit.`,
  };

  const submit = (onValid: (command: EditDraftCommand) => void) =>
    handleSubmit((values) =>
      onValid({
        content: values.content,
        // Blank means "no affiliate links" — send null so the API clears them.
        affiliateLinks: values.affiliateLinks.trim() || null,
        disclosureIncluded: values.disclosureIncluded,
      }),
    );

  return {
    control,
    submit,
    chars: content.length,
    limit,
    disclosureMissing,
    contentRules,
  };
}
