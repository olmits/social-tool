import type { Platform } from "@/lib/types";
import { apiFetch } from "./client";
import type { AccountResponse, ConnectAccountCommand } from "./types";

/** Cache tag for all account reads. Invalidate via `updateTag` after mutations. */
export const ACCOUNTS_TAG = "accounts";

/** `GET /accounts` (optionally filtered by platform). Tag-cached for revalidation. */
export function listAccounts(platform?: Platform): Promise<AccountResponse[]> {
  const query = platform ? `?platform=${platform}` : "";
  return apiFetch<AccountResponse[]>(`/accounts${query}`, {
    next: { tags: [ACCOUNTS_TAG] },
  });
}

/** `GET /accounts/{id}`. Throws {@link ApiError} with status 404 if not found. */
export function getAccount(id: string): Promise<AccountResponse> {
  return apiFetch<AccountResponse>(`/accounts/${id}`, {
    next: { tags: [ACCOUNTS_TAG, `account:${id}`] },
  });
}

/** `POST /accounts`. 400 on bad input, 409 on duplicate / reddit single-account limit. */
export function connectAccount(
  command: ConnectAccountCommand,
): Promise<AccountResponse> {
  return apiFetch<AccountResponse>("/accounts", {
    method: "POST",
    body: command,
  });
}

/** `DELETE /accounts/{id}`. Resolves on 204; throws 404 if not found. */
export function disconnectAccount(id: string): Promise<void> {
  return apiFetch<void>(`/accounts/${id}`, { method: "DELETE" });
}
