// Prototype fixtures for the admin UI. Replace with API calls once the
// Java core API and Go workers are wired up (see /PLAN.md).

export type Platform = "Bluesky" | "Mastodon" | "Reddit";
export type DraftStatus =
  | "draft"
  | "approved"
  | "scheduled"
  | "published"
  | "failed";

export interface PlatformMeta {
  dot: string;
  badgeBg: string;
  badgeText: string;
}

export const PLATFORM_META: Record<Platform, PlatformMeta> = {
  Bluesky: {
    dot: "#0a7aff",
    badgeBg: "bg-blue-50 dark:bg-blue-950/40",
    badgeText: "text-blue-700 dark:text-blue-400",
  },
  Mastodon: {
    dot: "#6364ff",
    badgeBg: "bg-indigo-50 dark:bg-indigo-950/40",
    badgeText: "text-indigo-700 dark:text-indigo-400",
  },
  Reddit: {
    dot: "#ff4500",
    badgeBg: "bg-orange-50 dark:bg-orange-950/40",
    badgeText: "text-orange-700 dark:text-orange-400",
  },
};

export const ACCOUNTS_BY_PLATFORM: Record<Platform, string[]> = {
  Bluesky: ["@maya.dev", "@maya-labs.bsky.social"],
  Mastodon: ["@maya@fosstodon.org", "@maya@hachyderm.io"],
  Reddit: ["u/maya_builds"],
};

export interface StatusMeta {
  badgeBg: string;
  badgeText: string;
  dot: string;
}

export const STATUS_META: Record<DraftStatus, StatusMeta> = {
  draft: {
    badgeBg: "bg-muted",
    badgeText: "text-muted-foreground",
    dot: "bg-neutral-400",
  },
  approved: {
    badgeBg: "bg-green-50 dark:bg-green-950/40",
    badgeText: "text-green-700 dark:text-green-400",
    dot: "bg-green-500",
  },
  scheduled: {
    badgeBg: "bg-blue-50 dark:bg-blue-950/40",
    badgeText: "text-blue-700 dark:text-blue-400",
    dot: "bg-blue-500",
  },
  published: {
    badgeBg: "bg-violet-50 dark:bg-violet-950/40",
    badgeText: "text-violet-700 dark:text-violet-400",
    dot: "bg-violet-500",
  },
  failed: {
    badgeBg: "bg-red-50 dark:bg-red-950/40",
    badgeText: "text-red-700 dark:text-red-400",
    dot: "bg-red-500",
  },
};

export interface SourceMeta {
  dot: string;
  badgeBg: string;
  badgeText: string;
}

export const SOURCE_META: Record<string, SourceMeta> = {
  "Hacker News": {
    dot: "#ff6600",
    badgeBg: "bg-orange-50 dark:bg-orange-950/40",
    badgeText: "text-orange-700 dark:text-orange-400",
  },
  GitHub: {
    dot: "#111827",
    badgeBg: "bg-neutral-100 dark:bg-neutral-800",
    badgeText: "text-neutral-900 dark:text-neutral-100",
  },
  Reddit: {
    dot: "#ff4500",
    badgeBg: "bg-orange-50 dark:bg-orange-950/40",
    badgeText: "text-orange-700 dark:text-orange-400",
  },
  "dev.to": {
    dot: "#0a0a0a",
    badgeBg: "bg-neutral-100 dark:bg-neutral-800",
    badgeText: "text-neutral-900 dark:text-neutral-100",
  },
  "Product Hunt": {
    dot: "#da552f",
    badgeBg: "bg-red-50 dark:bg-red-950/40",
    badgeText: "text-red-700 dark:text-red-400",
  },
};

export const SOURCE_CHIPS = [
  "All",
  "Hacker News",
  "GitHub",
  "Reddit",
  "dev.to",
  "Product Hunt",
];

export interface Signal {
  id: number;
  source: string;
  title: string;
  topic: string;
  engagement: string;
  match: number;
  age: string;
}

export const SIGNALS: Signal[] = [
  {
    id: 1,
    source: "Hacker News",
    title:
      "Show HN: A local-first sync engine written in Rust with on-device CRDT merge",
    topic: "Local-first",
    engagement: "842 pts · 310 comments",
    match: 92,
    age: "2h",
  },
  {
    id: 2,
    source: "GitHub",
    title: "bun v1.3 ships a built-in SQL client and native S3 bindings",
    topic: "Runtimes",
    engagement: "1.2k ★ today",
    match: 88,
    age: "5h",
  },
  {
    id: 3,
    source: "Reddit",
    title: "Why we migrated from microservices back to a modular monolith",
    topic: "Architecture",
    engagement: "2.1k ↑ · 540 comments",
    match: 84,
    age: "6h",
  },
  {
    id: 4,
    source: "dev.to",
    title:
      "Stop reaching for useEffect to fetch data — a practical alternative",
    topic: "React",
    engagement: "456 reactions",
    match: 81,
    age: "3h",
  },
  {
    id: 5,
    source: "Hacker News",
    title: "PostgreSQL 17.2 released with faster incremental backups",
    topic: "Databases",
    engagement: "621 pts · 188 comments",
    match: 79,
    age: "1h",
  },
  {
    id: 6,
    source: "Product Hunt",
    title: "Postgres-backed vector search with no extensions required",
    topic: "Databases",
    engagement: "380 ↑",
    match: 76,
    age: "8h",
  },
  {
    id: 7,
    source: "dev.to",
    title: "A practical guide to OpenTelemetry tracing in Go services",
    topic: "Observability",
    engagement: "289 reactions",
    match: 71,
    age: "4h",
  },
  {
    id: 8,
    source: "GitHub",
    title: "zig 0.14 release notes: incremental compilation lands",
    topic: "Languages",
    engagement: "900 ★ today",
    match: 66,
    age: "7h",
  },
];

export interface RadarStat {
  label: string;
  value: string;
  delta: string;
  up: boolean;
}

export const RADAR_STATS: RadarStat[] = [
  { label: "New signals (24h)", value: "24", delta: "+6", up: true },
  { label: "Sources active", value: "5", delta: "all up", up: false },
  { label: "Drafted today", value: "3", delta: "+2", up: true },
  { label: "Avg match score", value: "78%", delta: "+4", up: true },
];

export interface Draft {
  id: number;
  platform: Platform;
  account: string;
  status: DraftStatus;
  meta: string;
  chars: number;
  limit: number;
  affiliate: boolean;
  disclosure: boolean;
  signal: string;
  content: string;
}

export const DRAFTS: Draft[] = [
  {
    id: 1,
    platform: "Bluesky",
    account: "@maya.dev",
    status: "draft",
    meta: "edited 8m ago",
    chars: 247,
    limit: 300,
    affiliate: false,
    disclosure: false,
    signal: "Show HN: local-first sync engine",
    content:
      "Local-first is having a moment.\n\nNew Rust sync engine does CRDT merge on-device, syncs in the background, and works fully offline — no server round-trip to read your own data.\n\nThis is the architecture more apps should be copying.",
  },
  {
    id: 2,
    platform: "Mastodon",
    account: "@maya@fosstodon.org",
    status: "approved",
    meta: "approved 22m ago",
    chars: 233,
    limit: 500,
    affiliate: false,
    disclosure: false,
    signal: "micro→monolith migration",
    content:
      "Migrating from microservices back to a monolith isn't failure — it's right-sizing.\n\nFewer network hops, simpler deploys, one place to reason about state. Distributed systems are a cost you pay, not a badge you earn.",
  },
  {
    id: 3,
    platform: "Bluesky",
    account: "@maya.dev",
    status: "scheduled",
    meta: "scheduled Jul 13, 9:00 AM",
    chars: 198,
    limit: 300,
    affiliate: false,
    disclosure: false,
    signal: "PostgreSQL 17.2 released",
    content:
      "Postgres 17.2 is out and the incremental backup story keeps getting better.\n\nSmaller backup windows, faster restores. Boring infrastructure that just gets quietly better every release.",
  },
  {
    id: 4,
    platform: "Reddit",
    account: "u/maya_builds",
    status: "draft",
    meta: "edited 1h ago",
    chars: 612,
    limit: 40000,
    affiliate: true,
    disclosure: false,
    signal: "OpenTelemetry in Go",
    content:
      "I kept bouncing off OpenTelemetry in Go until I stopped trying to trace everything at once.\n\nStart with one span at the request boundary, get it exporting, then push context down. The moment traces show up in your backend, the mental model clicks.\n\nWrote up the exact minimal setup I use — collector config, the three lines of SDK boilerplate, and where the context propagation actually breaks.",
  },
  {
    id: 5,
    platform: "Mastodon",
    account: "@maya@hachyderm.io",
    status: "published",
    meta: "published Jul 9, 10:04 AM",
    chars: 210,
    limit: 500,
    affiliate: false,
    disclosure: false,
    signal: "bun v1.3 SQL client",
    content:
      "bun v1.3 shipping a built-in SQL client is the kind of batteries-included move that quietly removes three dependencies from your project.\n\nNative S3 bindings too. The runtime is turning into a platform.",
  },
  {
    id: 6,
    platform: "Bluesky",
    account: "@maya.dev",
    status: "failed",
    meta: "failed 2h ago · rate limited",
    chars: 190,
    limit: 300,
    affiliate: false,
    disclosure: false,
    signal: "zig 0.14 release",
    content:
      "zig 0.14 landing incremental compilation is a big deal for iteration speed. The comptime story stays weird and wonderful. Worth a weekend if you've been curious.",
  },
];

export interface ScheduleEvent {
  time: string;
  platform: Platform;
  account: string;
  title: string;
  status: "scheduled" | "approved";
}

export interface ScheduleDay {
  dow: string;
  date: number;
  events: ScheduleEvent[];
}

export const WEEK: ScheduleDay[] = [
  {
    dow: "Mon",
    date: 13,
    events: [
      {
        time: "9:00 AM",
        platform: "Bluesky",
        account: "@maya.dev",
        title: "Postgres 17.2 incremental backups keep getting better…",
        status: "scheduled",
      },
      {
        time: "2:30 PM",
        platform: "Mastodon",
        account: "@maya@fosstodon.org",
        title: "Right-sizing beats microservices dogma.",
        status: "scheduled",
      },
    ],
  },
  {
    dow: "Tue",
    date: 14,
    events: [
      {
        time: "11:00 AM",
        platform: "Reddit",
        account: "u/maya_builds",
        title: "Minimal OpenTelemetry setup for Go services",
        status: "approved",
      },
    ],
  },
  {
    dow: "Wed",
    date: 15,
    events: [
      {
        time: "8:00 AM",
        platform: "Bluesky",
        account: "@maya.dev",
        title: "Local-first sync is the architecture to copy.",
        status: "scheduled",
      },
    ],
  },
  { dow: "Thu", date: 16, events: [] },
  {
    dow: "Fri",
    date: 17,
    events: [
      {
        time: "10:00 AM",
        platform: "Bluesky",
        account: "@maya.dev",
        title: "bun v1.3 removes three dependencies from your project.",
        status: "scheduled",
      },
      {
        time: "1:00 PM",
        platform: "Mastodon",
        account: "@maya@hachyderm.io",
        title: "zig 0.14 incremental compilation is worth a weekend.",
        status: "approved",
      },
    ],
  },
  { dow: "Sat", date: 18, events: [] },
  { dow: "Sun", date: 19, events: [] },
];

export const REVIEW_STATUS_TABS: Array<DraftStatus | "all"> = [
  "all",
  "draft",
  "approved",
  "scheduled",
  "published",
  "failed",
];

export const STATE_MACHINE_STEPS = [
  "draft",
  "approved",
  "scheduled",
  "published",
] as const;
