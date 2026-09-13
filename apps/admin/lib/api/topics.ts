import { apiFetch } from "./client";
import type {
  CreateTopicCommand,
  TopicResponse,
  UpdateTopicCommand,
} from "./types";

/** Cache tag for all topic reads. Invalidate via `updateTag` after mutations. */
export const TOPICS_TAG = "topics";

/** `GET /topics` — every topic, enabled or not. Tag-cached for revalidation. */
export function listTopics(): Promise<TopicResponse[]> {
  return apiFetch<TopicResponse[]>("/topics", {
    next: { tags: [TOPICS_TAG] },
  });
}

/** `GET /topics/{id}`. Throws {@link ApiError} with status 404 if not found. */
export function getTopic(id: string): Promise<TopicResponse> {
  return apiFetch<TopicResponse>(`/topics/${id}`, {
    next: { tags: [TOPICS_TAG, `topic:${id}`] },
  });
}

/**
 * `POST /topics`. 400 on a blank or over-long name, 409 on a duplicate name
 * (case-insensitive).
 */
export function createTopic(
  command: CreateTopicCommand,
): Promise<TopicResponse> {
  return apiFetch<TopicResponse>("/topics", {
    method: "POST",
    body: command,
  });
}

/**
 * `PATCH /topics/{id}`. A **sparse** patch — see {@link UpdateTopicCommand}:
 * omitted fields are left unchanged, and a present `queries` map replaces the
 * topic's queries wholesale. 404 if not found, 409 on a duplicate name, 400 on
 * an invalid one.
 */
export function updateTopic(
  id: string,
  command: UpdateTopicCommand,
): Promise<TopicResponse> {
  return apiFetch<TopicResponse>(`/topics/${id}`, {
    method: "PATCH",
    body: command,
  });
}

/**
 * `DELETE /topics/{id}`. Resolves on 204; throws 404 if not found. Signals
 * already collected for the topic are kept, with a null `topicId`.
 */
export function deleteTopic(id: string): Promise<void> {
  return apiFetch<void>(`/topics/${id}`, { method: "DELETE" });
}
