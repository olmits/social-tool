import type { Platform } from "@/lib/mock-data";

export interface AccountState {
  platform: Platform;
  account: string;
}

export type AccountAction = { type: "select"; platform: Platform; account: string };

export const initialAccountState: AccountState = { platform: "Bluesky", account: "@maya.dev" };

export function accountReducer(state: AccountState, action: AccountAction): AccountState {
  switch (action.type) {
    case "select":
      return { platform: action.platform, account: action.account };
    default:
      return state;
  }
}
