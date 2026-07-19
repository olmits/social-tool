"use server";

import { updateTag } from "next/cache";
import type { Platform } from "@/lib/types";
import { ACCOUNTS_TAG, connectAccount, disconnectAccount } from "./accounts";
import { ApiError } from "./client";
import { toUiAccount, type UiAccount } from "./mappers";

/** Discriminated result so client callers can render errors without an error boundary. */
export type ActionResult<T = undefined> =
  | { ok: true; data: T }
  | { ok: false; message: string };

function toActionError(
  err: unknown,
  fallback: string,
): {
  ok: false;
  message: string;
} {
  return {
    ok: false,
    message: err instanceof ApiError ? err.message : fallback,
  };
}

export async function disconnectAccountAction(
  id: string,
): Promise<ActionResult> {
  try {
    await disconnectAccount(id);
    // Read-your-own-writes: expire the accounts cache so the next render is fresh.
    updateTag(ACCOUNTS_TAG);
    return { ok: true, data: undefined };
  } catch (err) {
    return toActionError(err, "Failed to disconnect account.");
  }
}

export interface ConnectAccountInput {
  platform: Platform;
  handle: string;
  credentialValue: string;
  instance: string | null;
}

/**
 * NOTE: not yet wired to the UI — `POST /accounts` needs the API's credential
 * store (Secrets Manager), unavailable locally today. See API_INTEGRATION_PLAN.md
 * phase 4. Kept here so the connect form is a UI-only change once that lands.
 */
export async function connectAccountAction(
  input: ConnectAccountInput,
): Promise<ActionResult<UiAccount>> {
  try {
    const dto = await connectAccount({
      platform: input.platform,
      handle: input.handle,
      credentialValue: input.credentialValue,
      instance: input.instance,
    });
    updateTag(ACCOUNTS_TAG);
    return { ok: true, data: toUiAccount(dto) };
  } catch (err) {
    return toActionError(err, "Failed to connect account.");
  }
}
