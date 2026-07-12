"use client";

import { createContext, useContext, useReducer, type Dispatch, type ReactNode } from "react";
import { accountReducer, initialAccountState, type AccountAction, type AccountState } from "@/lib/reducers/accountReducer";

const AccountStateContext = createContext<AccountState | null>(null);
const AccountDispatchContext = createContext<Dispatch<AccountAction> | null>(null);

export function AccountProvider({ children }: { children: ReactNode }) {
  const [state, dispatch] = useReducer(accountReducer, initialAccountState);

  return (
    <AccountStateContext.Provider value={state}>
      <AccountDispatchContext.Provider value={dispatch}>{children}</AccountDispatchContext.Provider>
    </AccountStateContext.Provider>
  );
}

export function useAccountState() {
  const ctx = useContext(AccountStateContext);
  if (!ctx) throw new Error("useAccountState must be used within AccountProvider");
  return ctx;
}

export function useAccountDispatch() {
  const ctx = useContext(AccountDispatchContext);
  if (!ctx) throw new Error("useAccountDispatch must be used within AccountProvider");
  return ctx;
}
