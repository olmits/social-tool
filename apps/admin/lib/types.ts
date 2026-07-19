// Shared domain types used across the UI, the API layer, and mock fixtures.
// Keep this free of runtime values, React, and server-only imports.

/**
 * Social platform. Values mirror the backend `Platform` enum
 * (services/api …/account/model/Platform.java) exactly, so they cross the API
 * boundary untouched — see lib/api/mappers.ts for display formatting only.
 */
export type Platform = "BLUESKY" | "MASTODON" | "REDDIT";
