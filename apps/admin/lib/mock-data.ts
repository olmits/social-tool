// Prototype fixtures for the admin UI. Replace with API calls once the
// Java core API and Go workers are wired up (see /PLAN.md).

import type { Platform } from "@/lib/types";

export interface PlatformMeta {
  dot: string;
  badgeBg: string;
  badgeText: string;
}

export const PLATFORM_META: Record<Platform, PlatformMeta> = {
  BLUESKY: {
    dot: "#0a7aff",
    badgeBg: "bg-blue-50 dark:bg-blue-950/40",
    badgeText: "text-blue-700 dark:text-blue-400",
  },
  MASTODON: {
    dot: "#6364ff",
    badgeBg: "bg-indigo-50 dark:bg-indigo-950/40",
    badgeText: "text-indigo-700 dark:text-indigo-400",
  },
  REDDIT: {
    dot: "#ff4500",
    badgeBg: "bg-orange-50 dark:bg-orange-950/40",
    badgeText: "text-orange-700 dark:text-orange-400",
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
