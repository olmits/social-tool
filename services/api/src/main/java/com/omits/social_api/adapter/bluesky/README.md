# Bluesky adapter — factory & adapter pairing

How `BlueskyAdapterFactory` and `BlueskyAdapter` work together with the credential store
(Secrets Manager in prod).

## The problem this solves

Two facts collide:

1. **Every Bluesky call must be authenticated.** To act as an account you need its access
   token (`accessJwt`) and its DID — obtained by logging in via `createSession` with the
   account's handle + app password.
2. **The account row doesn't hold the password.** `accounts` stores only a `credential_ref`
   (a pointer to Secrets Manager), never the secret. Many accounts can be connected, each
   with its own password, DID, and token.

So before a single post can happen, something must: read the pointer → fetch the real
password → log in → get a session → *then* build an adapter wired to that session. That
recipe is the **factory**.

## Two objects, opposite lifetimes

|                         | `BlueskyAdapterFactory` | `BlueskyAdapter`          |
| ----------------------- | ----------------------- | ------------------------- |
| Spring bean?            | ✅ one singleton        | ❌ `new`-ed on demand     |
| Bound to an account?    | ❌ works for any        | ✅ one specific account   |
| Holds a session/token?  | ❌ stateless            | ✅ holds the login session |
| Lifetime                | whole app               | short-lived, per operation |

A Spring singleton can't *be* the adapter (a singleton is shared app-wide, but an adapter
must be tied to one account's credentials). So the singleton is the **builder**, and the
per-account thing it builds is created fresh each time. The adapter stays dumb — it only
makes calls; it knows nothing about secret storage or logging in.

## What `forAccount()` produces

```
   Account (just a handle + a pointer)
   ┌─────────────────────────────────┐
   │ handle: "me.bsky.social"        │
   │ credentialRef: "social-api/     │   ← NOT the password, just a pointer
   │                 accounts/abc123" │
   └─────────────────────────────────┘
                  │
                  │  factory.forAccount(account)
                  ▼
   BlueskyAdapter (authenticated, ready to use)
   ┌─────────────────────────────────┐
   │ webClient  → bsky.social         │
   │ session    → did + accessJwt     │   ← knows WHO it is and can prove it
   └─────────────────────────────────┘
```

## The flow, step by step

```
Caller            BlueskyAdapterFactory        CredentialStore     Secrets Mgr    Bluesky
  │                       │                          │                 │            │
  │ forAccount(account)──▶│                          │                 │            │
  │                       │                          │                 │            │
  │              ┌── (1) account.getCredentialRef()  │                 │            │
  │              │    → "social-api/accounts/abc123" │                 │            │
  │              │                                   │                 │            │
  │              │  (2) resolve(credentialRef) ─────▶│                 │            │
  │              │                                   │── getSecret ───▶│            │
  │              │                                   │◀── secret ──────│            │
  │              │◀──────── "app-password" ──────────│                 │            │
  │              │                                   │                 │            │
  │              │  (3) createSession(handle, app-password) ──────────────────────▶│
  │              │       POST /xrpc/com.atproto.server.createSession               │
  │              │◀────────── {did, accessJwt, refreshJwt} ─────────────────────── │
  │              │                                                                  │
  │              │  (4) new BlueskyAdapter(webClient, session)                      │
  │◀── BlueskyAdapter ────│                                                         │
  │  (bound to THIS account)                                                        │
```

1. **Read the pointer** — pull `credentialRef` off the account.
2. **Resolve the secret** — `CredentialStore.resolve(...)` turns the pointer into the real
   app password (prod reads Secrets Manager; locally, env-var fallback).
3. **Log in** — `createSession` sends handle + password to Bluesky, gets back `did` +
   `accessJwt`, wrapped in a `BlueskySession`.
4. **Build the adapter** — hand the shared `WebClient` + that session to a new `BlueskyAdapter`.

## Using the adapter

```
adapter.post("hello")
   └─▶ POST /xrpc/com.atproto.repo.createRecord
        repo:          session.did                 ← who's posting
        Authorization: Bearer session.accessJwt    ← proof it's allowed
```

## Analogy

The factory is a **hotel front desk**:

- You show your **booking reference** (`credential_ref`) — not your ID, just a reference.
- The desk **looks up your reservation** (`resolve` the secret) and **checks you in**
  (`createSession` → issues a room key = `accessJwt`).
- It hands you a **key card** (the `BlueskyAdapter`) that opens only *your* room.

The front desk is always there and serves every guest (singleton, stateless). Each key card
is issued per guest and is what you actually use to get into your room (per-account, does the
real work).
