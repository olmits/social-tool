import type { UiAccount } from "@/lib/api/mappers";

export interface AccountState {
  /** All connected accounts, as fetched from the API and mapped to UI shape. */
  accounts: UiAccount[];
  /** Currently selected account id, or null when there are no accounts. */
  selectedId: string | null;
}

export type AccountAction =
  | { type: "hydrate"; accounts: UiAccount[] }
  | { type: "select"; id: string }
  | { type: "add"; account: UiAccount };

/** Builds initial state, defaulting the selection to the first account. */
export function createInitialAccountState(accounts: UiAccount[]): AccountState {
  return {
    accounts,
    selectedId: accounts[0]?.id ?? null,
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
      };
    }
    case "add": {
      // Optimistically insert + select the just-connected account. The next
      // server `hydrate` (after updateTag) reconciles it with the canonical list.
      const exists = state.accounts.some((a) => a.id === action.account.id);
      return {
        accounts: exists ? state.accounts : [...state.accounts, action.account],
        selectedId: action.account.id,
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
