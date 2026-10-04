"use server";

import { updateTag } from "next/cache";
import type { Platform } from "@/lib/types";
import { ACCOUNTS_TAG, connectAccount, disconnectAccount } from "./accounts";
import { ApiError } from "./client";
import {
  approveDraft,
  createDraft,
  DRAFTS_TAG,
  discardDraft,
  editDraft,
  generateDraft,
  scheduleDraft,
} from "./drafts";
import { toUiAccount, type UiAccount } from "./mappers";
import { SIGNALS_TAG } from "./signals";
import { createTopic, deleteTopic, TOPICS_TAG, updateTopic } from "./topics";
import type {
  CreateDraftCommand,
  CreateTopicCommand,
  CreateVoiceProfileCommand,
  DraftResponse,
  EditDraftCommand,
  GenerateDraftCommand,
  TopicResponse,
  UpdateTopicCommand,
  UpdateVoiceProfileCommand,
  VoiceProfileResponse,
} from "./types";
import {
  createVoiceProfile,
  deleteVoiceProfile,
  updateVoiceProfile,
  VOICE_PROFILES_TAG,
} from "./voiceProfiles";

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

export async function createDraftAction(
  command: CreateDraftCommand,
): Promise<ActionResult<DraftResponse>> {
  try {
    const draft = await createDraft(command);
    updateTag(DRAFTS_TAG);
    return { ok: true, data: draft };
  } catch (err) {
    return toActionError(err, "Failed to create draft.");
  }
}

export async function editDraftAction(
  id: string,
  command: EditDraftCommand,
): Promise<ActionResult<DraftResponse>> {
  try {
    const draft = await editDraft(id, command);
    updateTag(DRAFTS_TAG);
    return { ok: true, data: draft };
  } catch (err) {
    return toActionError(err, "Failed to save draft.");
  }
}

export async function discardDraftAction(
  id: string,
): Promise<ActionResult<DraftResponse>> {
  try {
    const draft = await discardDraft(id);
    updateTag(DRAFTS_TAG);
    return { ok: true, data: draft };
  } catch (err) {
    return toActionError(err, "Failed to discard draft.");
  }
}

export async function approveDraftAction(
  id: string,
): Promise<ActionResult<DraftResponse>> {
  try {
    const draft = await approveDraft(id);
    updateTag(DRAFTS_TAG);
    return { ok: true, data: draft };
  } catch (err) {
    // ApiError message carries the backend reason (e.g. the 422 disclosure text).
    return toActionError(err, "Failed to approve draft.");
  }
}

export async function scheduleDraftAction(
  id: string,
  scheduledAt: string,
): Promise<ActionResult<DraftResponse>> {
  try {
    const draft = await scheduleDraft(id, { scheduledAt });
    updateTag(DRAFTS_TAG);
    return { ok: true, data: draft };
  } catch (err) {
    return toActionError(err, "Failed to schedule draft.");
  }
}

/**
 * `POST /topics`. A duplicate name comes back as a 409 whose message already
 * names the clash ("A topic named X already exists"), so callers surface
 * `message` rather than branching on the status.
 */
export async function createTopicAction(
  command: CreateTopicCommand,
): Promise<ActionResult<TopicResponse>> {
  try {
    const topic = await createTopic(command);
    updateTag(TOPICS_TAG);
    return { ok: true, data: topic };
  } catch (err) {
    return toActionError(err, "Failed to create topic.");
  }
}

/**
 * `PATCH /topics/{id}`. A sparse patch — send only what changed: the enabled
 * toggle sends `{ enabled }` alone, the edit form sends name and queries.
 */
export async function updateTopicAction(
  id: string,
  command: UpdateTopicCommand,
): Promise<ActionResult<TopicResponse>> {
  try {
    const topic = await updateTopic(id, command);
    updateTag(TOPICS_TAG);
    return { ok: true, data: topic };
  } catch (err) {
    return toActionError(err, "Failed to save topic.");
  }
}

/**
 * `DELETE /topics/{id}`. Also expires the signals cache: signals already
 * collected for the topic are kept, but `topicName` is resolved server-side and
 * goes null with the topic, so a cached list would keep rendering a chip for a
 * topic that no longer exists.
 */
export async function deleteTopicAction(id: string): Promise<ActionResult> {
  try {
    await deleteTopic(id);
    updateTag(TOPICS_TAG);
    updateTag(SIGNALS_TAG);
    return { ok: true, data: undefined };
  } catch (err) {
    return toActionError(err, "Failed to delete topic.");
  }
}

/**
 * Re-reads what the poller has already stored. It does **not** trigger a radar
 * run: polling is the Go worker's job, on `RADAR_INTERVAL` (and EventBridge in
 * production), and there is no manual-trigger endpoint to call.
 */
export async function refreshSignalsAction(): Promise<ActionResult> {
  updateTag(SIGNALS_TAG);
  return { ok: true, data: undefined };
}

/**
 * Has Claude write a draft about a signal. Unlike every other action here this
 * one takes tens of seconds — the model reads the linked article first — so the
 * caller must keep a pending state up for the whole transition.
 *
 * The API's own messages are surfaced as-is rather than switched on by status:
 * "Voice profile X is disabled" and "Claude declined to draft a post from this
 * signal (cyber)" already say more than a status-to-string map could.
 */
export async function generateDraftAction(
  command: GenerateDraftCommand,
): Promise<ActionResult<DraftResponse>> {
  try {
    const draft = await generateDraft(command);
    updateTag(DRAFTS_TAG);
    return { ok: true, data: draft };
  } catch (err) {
    return toActionError(err, "Failed to generate a draft.");
  }
}

export async function createVoiceProfileAction(
  command: CreateVoiceProfileCommand,
): Promise<ActionResult<VoiceProfileResponse>> {
  try {
    const profile = await createVoiceProfile(command);
    updateTag(VOICE_PROFILES_TAG);
    return { ok: true, data: profile };
  } catch (err) {
    return toActionError(err, "Failed to create voice profile.");
  }
}

/**
 * `PATCH /voice-profiles/{id}`. A sparse patch — send only what changed: the
 * enabled toggle sends `{ enabled }` alone, the edit form sends name and
 * instructions.
 */
export async function updateVoiceProfileAction(
  id: string,
  command: UpdateVoiceProfileCommand,
): Promise<ActionResult<VoiceProfileResponse>> {
  try {
    const profile = await updateVoiceProfile(id, command);
    updateTag(VOICE_PROFILES_TAG);
    return { ok: true, data: profile };
  } catch (err) {
    return toActionError(err, "Failed to update voice profile.");
  }
}

export async function deleteVoiceProfileAction(
  id: string,
): Promise<ActionResult> {
  try {
    await deleteVoiceProfile(id);
    updateTag(VOICE_PROFILES_TAG);
    return { ok: true, data: undefined };
  } catch (err) {
    return toActionError(err, "Failed to delete voice profile.");
  }
}
