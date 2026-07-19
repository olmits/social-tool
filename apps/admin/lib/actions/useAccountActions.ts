"use client";

import { useCallback } from "react";
import { useAccountDispatch } from "@/context/account-context";

export function useAccountActions() {
  const dispatch = useAccountDispatch();

  const selectAccount = useCallback(
    (id: string) => dispatch({ type: "select", id }),
    [dispatch],
  );

  return { selectAccount };
}
