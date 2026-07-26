"use client";

import { useMemo } from "react";
import { useAccountState } from "@/context/account-context";
import { accountLabel } from "@/lib/api/mappers";
import type { DraftResponse } from "@/lib/api/types";

/**
 * Account concerns for the review board: whether an account is selected, the
 * id → display-label lookup, and the drafts scoped to the selected account.
 * Keeps account/selection logic out of useReviewBoard, which owns status
 * filtering and draft selection.
 */
export function useReviewAccounts(drafts: DraftResponse[]) {
  const { accounts, selectedId } = useAccountState();

  const accountNames = useMemo(
    () =>
      new Map(accounts.map((account) => [account.id, accountLabel(account)])),
    [accounts],
  );

  const accountDrafts = useMemo(
    () =>
      selectedId
        ? drafts.filter((draft) => draft.accountId === selectedId)
        : [],
    [drafts, selectedId],
  );

  return {
    hasAccount: selectedId !== null,
    accountNames,
    accountDrafts,
  };
}
