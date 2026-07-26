"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useMemo, useState } from "react";
import type { DraftResponse } from "@/lib/api/types";
import { REVIEW_STATUS_TABS, type ReviewFilter } from "./reviewMeta";
import { useReviewAccounts } from "./useReviewAccounts";

function isReviewFilter(value: string | null): value is ReviewFilter {
  return (
    value !== null && (REVIEW_STATUS_TABS as readonly string[]).includes(value)
  );
}

/**
 * Review board state: status filter (from the URL `?status=`) and draft
 * selection over the account-scoped drafts. Account concerns live in
 * {@link useReviewAccounts}; the server page fetches all drafts.
 */
export function useReviewBoard(drafts: DraftResponse[]) {
  const [selectedDraftId, setSelectedDraftId] = useState<string | null>(null);
  // A monotonically increasing signal bumped on every selection, so the detail
  // panel can react to selecting the same draft again (e.g. reopening the mobile
  // dialog). Selection itself is derived below.
  const [selectSignal, setSelectSignal] = useState(0);
  const selectDraft = (id: string) => {
    setSelectedDraftId(id);
    setSelectSignal((signal) => signal + 1);
  };

  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();

  const { hasAccount, accountNames, accountDrafts } = useReviewAccounts(drafts);

  const statusParam = searchParams.get("status");
  const filter: ReviewFilter = isReviewFilter(statusParam)
    ? statusParam
    : "ALL";

  const counts = useMemo(() => {
    const result: Record<string, number> = { ALL: accountDrafts.length };
    for (const draft of accountDrafts) {
      result[draft.status] = (result[draft.status] ?? 0) + 1;
    }
    return result;
  }, [accountDrafts]);

  const filteredDrafts = useMemo(
    () =>
      filter === "ALL"
        ? accountDrafts
        : accountDrafts.filter((draft) => draft.status === filter),
    [accountDrafts, filter],
  );

  // Fall back to the first draft when nothing is selected or the selection was
  // filtered out (drives the desktop pane; the mobile dialog opens on selection).
  const selected =
    filteredDrafts.find((draft) => draft.id === selectedDraftId) ??
    filteredDrafts[0] ??
    null;

  const setFilter = (tab: ReviewFilter) => {
    const params = new URLSearchParams(searchParams);
    if (tab === "ALL") {
      params.delete("status");
    } else {
      params.set("status", tab);
    }
    const query = params.toString();
    router.replace(query ? `${pathname}?${query}` : pathname, {
      scroll: false,
    });
  };

  return {
    hasAccount,
    filter,
    setFilter,
    counts,
    filteredDrafts,
    selected,
    selectDraft,
    selectSignal,
    accountNames,
  };
}
