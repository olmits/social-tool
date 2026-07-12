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

## Client state conventions

For React Context + `useReducer` state (e.g. `AccountProvider`), split by concern across three locations:

- `context/<name>-context.tsx` — the context objects, the `Provider`, and the raw context-accessor hooks (`use<Name>State`, `use<Name>Dispatch`). Nothing else.
- `lib/reducers/<name>Reducer.ts` — state type, action type, initial state, and the pure reducer function. No React imports.
- `lib/actions/use<Name>Actions.ts` — a hook that wraps `use<Name>Dispatch` in `useCallback`-stabilized action creators for ergonomic call sites (e.g. `setAccount(platform, account)` instead of `dispatch({ type: "select", ... })`).

See `context/account-context.tsx`, `lib/reducers/accountReducer.ts`, and `lib/actions/useAccountActions.ts` for the reference implementation.
