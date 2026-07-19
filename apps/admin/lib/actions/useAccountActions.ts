"use client";

import { useCallback } from "react";
import { useAccountDispatch } from "@/context/account-context";
import type { UiAccount } from "@/lib/api/mappers";

export function useAccountActions() {
  const dispatch = useAccountDispatch();

  const selectAccount = useCallback(
    (id: string) => dispatch({ type: "select", id }),
    [dispatch],
  );

  const addAccount = useCallback(
    (account: UiAccount) => dispatch({ type: "add", account }),
    [dispatch],
  );

  return { selectAccount, addAccount };
}
