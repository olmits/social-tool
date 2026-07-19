"use client";

import { useCallback } from "react";
import { useAccountDispatch } from "@/context/account-context";
import type { Platform } from "@/lib/mock-data";

export function useAccountActions() {
  const dispatch = useAccountDispatch();

  const setAccount = useCallback(
    (platform: Platform, account: string) =>
      dispatch({ type: "select", platform, account }),
    [dispatch],
  );

  return { setAccount };
}
