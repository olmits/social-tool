@AGENTS.md

# Admin Panel

Next.js front-end for a personal social media management tool. Single admin user only.

## Purpose

- Connect and manage social accounts (Bluesky, Mastodon, Reddit)
- Review, edit, and approve AI-generated post drafts
- Schedule approved drafts for publishing
- View per-post analytics

All publishing goes through manual approval — nothing is posted automatically from this panel.

## Backend

This app is a UI only. All data operations go through the Java (Spring Boot) core API. No database access from Next.js.

## Draft state machine

`draft → approved → scheduled → published → failed`

## Platforms

| Platform | Multi-account |
|---|---|
| Bluesky | Yes |
| Mastodon | Yes (per instance) |
| Reddit | Single account only |
