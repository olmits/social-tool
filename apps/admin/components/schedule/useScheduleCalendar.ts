"use client";

import { useMemo, useState } from "react";
import { useAccountState } from "@/context/account-context";
import { accountLabel } from "@/lib/api/mappers";
import type { DraftResponse } from "@/lib/api/types";
import {
  addDaysUtc,
  buildWeek,
  formatWeekLabel,
  mondayOfWeekUtc,
} from "./scheduleWeek";

/**
 * Schedule calendar state: the visible week (navigable, defaulting to the
 * current week) with the scheduled drafts grouped into its days, plus the
 * account id → label lookup. The server page fetches all SCHEDULED drafts;
 * grouping happens here.
 */
export function useScheduleCalendar(drafts: DraftResponse[]) {
  const [weekOffset, setWeekOffset] = useState(0);
  const { accounts } = useAccountState();

  const accountNames = useMemo(
    () =>
      new Map(accounts.map((account) => [account.id, accountLabel(account)])),
    [accounts],
  );

  const weekStart = useMemo(
    () => addDaysUtc(mondayOfWeekUtc(new Date()), weekOffset * 7),
    [weekOffset],
  );

  const days = useMemo(() => buildWeek(weekStart, drafts), [weekStart, drafts]);
  const weekLabel = useMemo(() => formatWeekLabel(weekStart), [weekStart]);
  const isEmpty = days.every((day) => day.drafts.length === 0);

  return {
    days,
    weekLabel,
    isEmpty,
    accountNames,
    previousWeek: () => setWeekOffset((offset) => offset - 1),
    nextWeek: () => setWeekOffset((offset) => offset + 1),
  };
}
