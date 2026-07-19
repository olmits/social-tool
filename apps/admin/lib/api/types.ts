// TypeScript mirrors of the Java core API DTOs (services/api). `Platform` shares
// the backend's UPPERCASE enum values, so it flows through untouched — see
// lib/api/mappers.ts for display formatting only.

import type { Platform } from "@/lib/types";

export type AccountStatus = "ACTIVE" | "DISCONNECTED";

export interface AccountResponse {
  id: string;
  platform: Platform;
  /** Bare handle, no leading `@` (e.g. "me.bsky.social"). */
  handle: string;
  /** Present for MASTODON, null otherwise. */
  instance: string | null;
  status: AccountStatus;
  createdAt: string;
  updatedAt: string;
}

export interface ConnectAccountCommand {
  platform: Platform;
  handle: string;
  /** Raw secret (app password / token). The API stores it and never returns it. */
  credentialValue: string;
  /** Required for MASTODON, must be null otherwise (API returns 400 if wrong). */
  instance: string | null;
}

/** Shape of the API's error body: `{ "message": string }`. */
export interface ApiErrorBody {
  message: string;
}
