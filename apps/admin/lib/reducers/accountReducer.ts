import type { UiAccount } from "@/lib/api/mappers";

export interface AccountState {
  /** All connected accounts, as fetched from the API and mapped to UI shape. */
  accounts: UiAccount[];
  /** Currently selected account id, or null when there are no accounts. */
  selectedId: string | null;
  /** Set when the server-side account fetch failed; null on success. */
  error: string | null;
}

export type AccountAction =
  | { type: "hydrate"; accounts: UiAccount[]; error?: string | null }
  | { type: "select"; id: string };

/** Builds initial state, defaulting the selection to the first account. */
export function createInitialAccountState(
  accounts: UiAccount[],
  error: string | null = null,
): AccountState {
  return {
    accounts,
    selectedId: accounts[0]?.id ?? null,
    error,
  };
}

export const initialAccountState: AccountState = createInitialAccountState([]);

export function accountReducer(
  state: AccountState,
  action: AccountAction,
): AccountState {
  switch (action.type) {
    case "hydrate": {
      const { accounts } = action;
      // Preserve the current selection if it still exists, otherwise fall back
      // to the first account (handles the selected account being disconnected).
      const keepSelection =
        state.selectedId !== null &&
        accounts.some((a) => a.id === state.selectedId);
      return {
        accounts,
        selectedId: keepSelection
          ? state.selectedId
          : (accounts[0]?.id ?? null),
        error: action.error ?? null,
      };
    }
    case "select":
      return state.accounts.some((a) => a.id === action.id)
        ? { ...state, selectedId: action.id }
        : state;
    default:
      return state;
  }
}
