import "server-only";

import type { ApiErrorBody } from "./types";

/**
 * Thrown on any non-2xx API response. Carries the HTTP status and the API's
 * `{ message }` body so callers (server actions, layouts) can react by status.
 */
export class ApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

type ApiRequestOptions = Omit<RequestInit, "body"> & {
  /** JSON-serializable request body. Sets `Content-Type: application/json`. */
  body?: unknown;
};

function baseUrl(): string {
  const url = process.env.API_BASE_URL;
  if (!url) {
    throw new Error("API_BASE_URL is not set (see .env.example)");
  }
  return url.replace(/\/+$/, "");
}

function apiKey(): string {
  const key = process.env.API_KEY;
  if (!key) {
    throw new Error("API_KEY is not set (see .env.example)");
  }
  return key;
}

/**
 * Server-only typed fetch wrapper for the Java core API. Prefixes API_BASE_URL,
 * attaches the `X-API-Key` shared secret (never reaches the browser), serializes
 * JSON bodies, and throws {@link ApiError} on non-2xx by reading the `{ message }`
 * body. `T` is the parsed JSON response (`void` for 204).
 */
export async function apiFetch<T>(
  path: string,
  options: ApiRequestOptions = {},
): Promise<T> {
  const { body, headers, ...rest } = options;

  const res = await fetch(`${baseUrl()}${path}`, {
    ...rest,
    headers: {
      "X-API-Key": apiKey(),
      ...(body !== undefined ? { "Content-Type": "application/json" } : {}),
      ...headers,
    },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  if (!res.ok) {
    let message = `API request to ${path} failed (${res.status})`;
    try {
      const data = (await res.json()) as Partial<ApiErrorBody>;
      if (data?.message) {
        message = data.message;
      }
    } catch {
      // Non-JSON error body — keep the default message.
    }
    throw new ApiError(res.status, message);
  }

  // 204 No Content (e.g. DELETE) has no body to parse.
  if (res.status === 204) {
    return undefined as T;
  }
  return (await res.json()) as T;
}
