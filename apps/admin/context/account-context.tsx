"use client";

import {
  createContext,
  type Dispatch,
  type ReactNode,
  useContext,
  useEffect,
  useReducer,
  useRef,
} from "react";
import type { UiAccount } from "@/lib/api/mappers";
import {
  type AccountAction,
  type AccountState,
  accountReducer,
  createInitialAccountState,
} from "@/lib/reducers/accountReducer";

const AccountStateContext = createContext<AccountState | null>(null);
const AccountDispatchContext = createContext<Dispatch<AccountAction> | null>(
  null,
);

export function AccountProvider({
  initialAccounts,
  initialError = null,
  children,
}: {
  initialAccounts: UiAccount[];
  initialError?: string | null;
  children: ReactNode;
}) {
  const [state, dispatch] = useReducer(
    accountReducer,
    { initialAccounts, initialError },
    (arg) => createInitialAccountState(arg.initialAccounts, arg.initialError),
  );

  // Re-hydrate when the server sends a fresh list (e.g. after a disconnect
  // revalidates the "accounts" tag). Keyed on content so navigations that pass a
  // new-but-equal array reference don't dispatch needlessly. Skips the first run
  // since useReducer already seeded from these props.
  const dataKey = `${JSON.stringify(initialAccounts)}|${initialError ?? ""}`;
  const isFirst = useRef(true);
  // initialAccounts/initialError are intentionally captured via `dataKey` (their
  // serialized content) rather than by reference, so a new-but-equal array from a
  // server re-render doesn't re-dispatch.
  // biome-ignore lint/correctness/useExhaustiveDependencies: dataKey encodes both inputs.
  useEffect(() => {
    if (isFirst.current) {
      isFirst.current = false;
      return;
    }
    dispatch({
      type: "hydrate",
      accounts: initialAccounts,
      error: initialError,
    });
  }, [dataKey]);

  return (
    <AccountStateContext.Provider value={state}>
      <AccountDispatchContext.Provider value={dispatch}>
        {children}
      </AccountDispatchContext.Provider>
    </AccountStateContext.Provider>
  );
}

export function useAccountState() {
  const ctx = useContext(AccountStateContext);
  if (!ctx)
    throw new Error("useAccountState must be used within AccountProvider");
  return ctx;
}

export function useAccountDispatch() {
  const ctx = useContext(AccountDispatchContext);
  if (!ctx)
    throw new Error("useAccountDispatch must be used within AccountProvider");
  return ctx;
}
