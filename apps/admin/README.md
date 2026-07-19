# Social Tool — Admin Panel

Personal social media management panel. Connect accounts, review and approve AI-generated drafts, schedule posts, and track analytics across Bluesky, Mastodon, and Reddit. Nothing is published without manual approval.

## Tech stack

| Layer | Technology |
|---|---|
| Admin panel (this app) | Next.js |
| Core API | Java (Spring Boot) |
| Background workers | Go |
| Database | PostgreSQL |

This app is UI only — all data operations go through the Java core API.

## Running locally

Install dependencies:

```bash
pnpm install
```

Configure environment variables — copy the template and fill it in:

```bash
cp .env.example .env.local
```

| Variable | Description |
|---|---|
| `API_BASE_URL` | Base URL of the Java core API (default `http://localhost:8080`). |
| `API_KEY` | Shared secret sent as `X-API-Key` on every API call. Local dev value: `local-dev-api-key`. |

> Both are **server-only** — the panel is a BFF: the browser only talks to Next's own origin, and Next attaches the API key when calling the core API. Never prefix these with `NEXT_PUBLIC_`, and never call the API directly from client components. `.env.local` is gitignored.

Start the dev server:

```bash
pnpm dev
```

Open [http://localhost:3000](http://localhost:3000).

The panel requires the Java core API to be running. See the root-level README for instructions on starting the full stack.

## Running with Docker

Start the dev server (hot reload via `pnpm dev`, with file sync/rebuild on changes):

```bash
docker compose up nextjs-dev
```

Open [http://localhost:3000](http://localhost:3000).

Use `-d` to run it in the background:

```bash
docker compose up -d nextjs-dev
```

> Use `up`, not `run` — `docker compose run` does not publish the container's ports to the host unless you also pass `--service-ports`.

To build and run the production standalone image instead:

```bash
docker compose up nextjs-prod-standalone
```

Stop and remove the containers:

```bash
docker compose down
```
