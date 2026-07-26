@AGENTS.md

# Admin Panel

Next.js front-end for a personal social media management tool. Single admin user only.

## Tooling

Package manager is **pnpm** — use `pnpm` / `pnpm dlx`, never `npm` or `yarn`. `npm install` fails against this workspace's `pnpm-lock.yaml`.

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

## Component & form conventions

- **Keep components small.** If a component grows past ~150–200 lines, split it into smaller, focused components (presentational children + an orchestrator that composes them).
- **Business logic lives in custom hooks**, not in component bodies. Data fetching, mutations, form submission handlers, and derived state belong in a `use<Feature>` hook; the component just renders what the hook returns.
- **Arrow functions for in-body callbacks.** Define handlers/helpers inside a component or hook body as `const foo = () => …`, not `function foo() {}`.
- **react-hook-form: prefer `useController` over `register`.** Bind inputs through a `useController`-based controlled field component rather than spreading `register(...)`. Reserve `register` for cases `useController` can't express.
- **Don't assign JSX to a variable to reuse it.** Render the component directly at each usage site — even if that repeats the props. Don't do this:
  ```tsx
  const actions = <DraftDetailActions onApprove={onApprove} onDiscard={onDiscard} />;
  return <>{actions} … {actions}</>;
  ```
  Write `<DraftDetailActions … />` inline at each location instead. Reusing a stored JSX element hides that it's a component and obscures where it renders.

### File naming

- **Component files: `PascalCase`** — one component per file, filename matches the export (e.g. `ConnectAccountForm.tsx`, `PlatformPicker.tsx`).
- **Hook files: `camelCase`** — filename matches the hook (e.g. `useConnectAccountForm.ts`, `useAccountActions.ts`).
- **Exception — `components/ui/`:** these are shadcn-style primitives and stay `lowercase` (`button.tsx`, `input.tsx`, `dialog.tsx`) to match the shadcn ecosystem convention already in that folder.
- Next.js route files keep their framework names (`page.tsx`, `layout.tsx`, `error.tsx`, `default.tsx`).

See `components/connect-account/` for the reference implementation: `useConnectAccountForm.ts` (logic hook), `ControlledTextField.tsx` / `CredentialField.tsx` (`useController` fields), `PlatformPicker.tsx` / `FormError.tsx` (presentational), and `ConnectAccountForm.tsx` (orchestrator).
