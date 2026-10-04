import { apiFetch } from "./client";
import type {
  CreateVoiceProfileCommand,
  UpdateVoiceProfileCommand,
  VoiceProfileResponse,
} from "./types";

/** Cache tag for all voice-profile reads. Invalidate via `updateTag` after mutations. */
export const VOICE_PROFILES_TAG = "voice-profiles";

/** `GET /voice-profiles` — every profile, enabled or not, sorted by name. */
export function listVoiceProfiles(): Promise<VoiceProfileResponse[]> {
  return apiFetch<VoiceProfileResponse[]>("/voice-profiles", {
    next: { tags: [VOICE_PROFILES_TAG] },
  });
}

/** `GET /voice-profiles/{id}`. Throws {@link ApiError} with status 404 if not found. */
export function getVoiceProfile(id: string): Promise<VoiceProfileResponse> {
  return apiFetch<VoiceProfileResponse>(`/voice-profiles/${id}`, {
    next: { tags: [VOICE_PROFILES_TAG, `voice-profile:${id}`] },
  });
}

/**
 * `POST /voice-profiles`. 400 on a blank name or blank/over-long instructions,
 * 409 on a duplicate name (case-insensitive).
 */
export function createVoiceProfile(
  command: CreateVoiceProfileCommand,
): Promise<VoiceProfileResponse> {
  return apiFetch<VoiceProfileResponse>("/voice-profiles", {
    method: "POST",
    body: command,
  });
}

/**
 * `PATCH /voice-profiles/{id}`. A **sparse** patch — see
 * {@link UpdateVoiceProfileCommand}: omitted fields are left unchanged. Setting
 * `isDefault` clears it on whichever profile held it before.
 */
export function updateVoiceProfile(
  id: string,
  command: UpdateVoiceProfileCommand,
): Promise<VoiceProfileResponse> {
  return apiFetch<VoiceProfileResponse>(`/voice-profiles/${id}`, {
    method: "PATCH",
    body: command,
  });
}

/**
 * `DELETE /voice-profiles/{id}`. Resolves on 204; throws 404 if not found.
 * Drafts already written in the voice are untouched — there is no FK to them.
 */
export function deleteVoiceProfile(id: string): Promise<void> {
  return apiFetch<void>(`/voice-profiles/${id}`, { method: "DELETE" });
}
