// Per-platform copy + field shape for the connect-account form. Kept separate
// from PLATFORM_META (colors) so the form's labels/help live in one place.

import type { Platform } from "@/lib/types";

export interface PlatformConnectFields {
  /** Order/label for the platform picker. */
  label: string;
  handleLabel: string;
  handlePlaceholder: string;
  /** Mastodon needs a server instance; others must not send one. */
  needsInstance: boolean;
  instancePlaceholder?: string;
  credentialLabel: string;
  credentialPlaceholder: string;
  /** One-liner shown under the credential field: where to get it. */
  credentialHint: string;
  /** Reddit is single-account only (see CLAUDE.md). */
  singleAccount: boolean;
}

export const PLATFORM_CONNECT_ORDER: Platform[] = [
  "BLUESKY",
  "MASTODON",
  "REDDIT",
];

export const PLATFORM_CONNECT_FIELDS: Record<Platform, PlatformConnectFields> =
  {
    BLUESKY: {
      label: "Bluesky",
      handleLabel: "Handle",
      handlePlaceholder: "you.bsky.social",
      needsInstance: false,
      credentialLabel: "App password",
      credentialPlaceholder: "xxxx-xxxx-xxxx-xxxx",
      credentialHint:
        "Create one under Settings → Privacy & security → App passwords.",
      singleAccount: false,
    },
    MASTODON: {
      label: "Mastodon",
      handleLabel: "Username",
      handlePlaceholder: "you",
      needsInstance: true,
      instancePlaceholder: "fosstodon.org",
      credentialLabel: "Access token",
      credentialPlaceholder: "Paste your access token",
      credentialHint:
        "Create an application under Preferences → Development to get a token.",
      singleAccount: false,
    },
    REDDIT: {
      label: "Reddit",
      handleLabel: "Username",
      handlePlaceholder: "you",
      needsInstance: false,
      credentialLabel: "Password or token",
      credentialPlaceholder: "Paste your credential",
      credentialHint: "Reddit allows a single connected account.",
      singleAccount: true,
    },
  };

/** Loose hostname check for the Mastodon instance field (e.g. `fosstodon.org`). */
export const INSTANCE_PATTERN =
  /^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9-]+)+$/i;
