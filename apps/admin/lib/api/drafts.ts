import { apiFetch } from "./client";
import type {
  CreateDraftCommand,
  DraftResponse,
  DraftStatus,
  EditDraftCommand,
  ScheduleDraftCommand,
} from "./types";

/** Cache tag for all draft reads. Invalidate via `updateTag` after mutations. */
export const DRAFTS_TAG = "drafts";

/**
 * `GET /drafts`, optionally narrowed by account and/or status (both filters are
 * optional). Tag-cached for revalidation.
 */
export function listDrafts(
  accountId?: string,
  status?: DraftStatus,
): Promise<DraftResponse[]> {
  const params = new URLSearchParams();
  if (accountId) {
    params.set("accountId", accountId);
  }
  if (status) {
    params.set("status", status);
  }
  const query = params.toString();
  return apiFetch<DraftResponse[]>(`/drafts${query ? `?${query}` : ""}`, {
    next: { tags: [DRAFTS_TAG] },
  });
}

/** `GET /drafts/{id}`. Throws {@link ApiError} with status 404 if not found. */
export function getDraft(id: string): Promise<DraftResponse> {
  return apiFetch<DraftResponse>(`/drafts/${id}`, {
    next: { tags: [DRAFTS_TAG, `draft:${id}`] },
  });
}

/** `POST /drafts`. Creates a draft in `DRAFT` status. 400 on bad input. */
export function createDraft(
  command: CreateDraftCommand,
): Promise<DraftResponse> {
  return apiFetch<DraftResponse>("/drafts", {
    method: "POST",
    body: command,
  });
}

/**
 * `PATCH /drafts/{id}`. Replaces the draft's editable body. 404 if not found,
 * 409 if the draft has left review (`SCHEDULED` onwards), 400 on blank content.
 * Editing an `APPROVED` draft returns it in `DRAFT` — the API re-opens it for review.
 */
export function editDraft(
  id: string,
  command: EditDraftCommand,
): Promise<DraftResponse> {
  return apiFetch<DraftResponse>(`/drafts/${id}`, {
    method: "PATCH",
    body: command,
  });
}

/**
 * `PATCH /drafts/{id}/discard`. 404 if not found, 409 unless the draft is in
 * `DRAFT` or `APPROVED`.
 */
export function discardDraft(id: string): Promise<DraftResponse> {
  return apiFetch<DraftResponse>(`/drafts/${id}/discard`, {
    method: "PATCH",
  });
}

/**
 * `PATCH /drafts/{id}/approve`. 404 if not found, 409 if not in `DRAFT`, 422 if
 * the draft carries affiliate links without a disclosure.
 */
export function approveDraft(id: string): Promise<DraftResponse> {
  return apiFetch<DraftResponse>(`/drafts/${id}/approve`, {
    method: "PATCH",
  });
}

/**
 * `PATCH /drafts/{id}/schedule`. 404 if not found, 409 if not in `APPROVED`,
 * 400 if `scheduledAt` is missing.
 */
export function scheduleDraft(
  id: string,
  command: ScheduleDraftCommand,
): Promise<DraftResponse> {
  return apiFetch<DraftResponse>(`/drafts/${id}/schedule`, {
    method: "PATCH",
    body: command,
  });
}
