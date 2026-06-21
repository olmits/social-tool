# CLAUDE.md — social-api (Java / Spring Boot)

This file is for Claude Code. It describes how to work in this service.
See the root PLAN.md for architecture intent and the full project context.

## What this service does

The core API for the social media tool. Responsible for:
- Account and credential lifecycle (connect, store, select)
- Draft state machine (`draft → approved → scheduled → published → failed`)
- Claude API orchestration (AI drafting)
- Persistence (PostgreSQL via JPA + Flyway)
- Outbound HTTP to platform APIs (Bluesky, Mastodon, Reddit) via WebClient

It does not publish. Publishing is handled by the Go workers. This service
enqueues approved drafts to SQS; the Go publisher acts on them.

## Stack

- Java 21, Spring Boot 4.1.x
- Maven
- PostgreSQL (RDS in prod, Testcontainers in tests)
- Flyway for migrations
- AWS SDK v2 — Secrets Manager (credentials), SQS (publish queue)

## Commands

```bash
# Build
./mvnw clean package -DskipTests

# Run locally (requires local Postgres — see below)
./mvnw spring-boot:run

# Run tests (spins up Postgres via Testcontainers — requires Docker)
./mvnw test

# Run a single test class
./mvnw test -Dtest=DraftServiceTest

# Check dependency resolution
./mvnw dependency:resolve
```

## Local development

The app expects a running Postgres instance for `spring-boot:run`.
Integration tests use Testcontainers and manage their own container — no manual
setup required for tests, only Docker.

Environment variables required locally (copy from a `.env.local` or export manually):

```
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/socialapi
SPRING_DATASOURCE_USERNAME=...
SPRING_DATASOURCE_PASSWORD=...
```

Credentials for platform adapters and the Claude API are read from AWS Secrets
Manager at call time in prod. Locally, set them as environment variables:

```
CLAUDE_API_KEY=...
BLUESKY_APP_PASSWORD=...
```

Do not commit credentials. Do not store them in `application.yml`.

## Package structure

```
src/main/java/com/omits/socialapi/
├── account/      # Account entity, repo, service, OAuth flows
├── adapter/      # PlatformAdapter interface + bluesky/, mastodon/, reddit/
├── draft/        # Draft entity, state machine, DraftService
├── drafting/     # Claude API client, prompt building, DraftingService
├── affiliate/    # AffiliateLinkService, disclosure enforcement
└── config/       # SecurityConfig, WebClientConfig, AwsConfig
```

Organized by domain slice, not by layer. A new platform adapter goes in
`adapter/<platform>/` — no changes to core logic required.

## Key conventions

**Adapters.** All platform adapters implement a common `PlatformAdapter`
interface (`post`, `read`, `reply`, `fetchMetrics`). Each is constructed with
the credentials of the currently selected account. Do not add platform-specific
logic outside the adapter package.

**Credentials.** Credentials are never stored in the database or in environment
variables in production. The `accounts` table holds a `credential_ref` (a
Secrets Manager secret name). The adapter fetches the actual value from Secrets
Manager at call time. Locally, fall back to environment variables.

**Draft state machine.** State transitions are enforced in `DraftService`.
The only valid sequence is `draft → approved → scheduled → published → failed`.
No component other than `DraftService` should write to `drafts.status`.

**Disclosure flag.** Any draft containing an affiliate link must have
`disclosure_included = true` before it can be approved. This is enforced in
`DraftService.approve()`. Do not bypass this check.

**No direct publishing.** This service never calls a platform adapter to
publish. It enqueues to SQS. Publishing is the Go worker's responsibility.

**Reddit is one account.** Reddit's Responsible Builder Policy bars multiple
accounts for the same use case. The Reddit adapter is configured for a single
account; there is no account-switching for Reddit.

## Database migrations

Migrations live in `src/main/resources/db/migration/` as plain SQL files,
named `V{n}__{description}.sql` (e.g. `V1__accounts.sql`).

- One migration per logical change.
- Never edit a migration that has already run in any environment.
- Add a new migration file instead.

## Testing approach

- Unit tests: plain JUnit 5 + Mockito, no Spring context.
- Integration tests: `@SpringBootTest` + Testcontainers Postgres.
- Tag integration tests with `@Tag("integration")` so they can be excluded
  from fast feedback runs: `./mvnw test -Dgroups='!integration'`

## Dependency notes

- `spring-boot-starter-web` — MVC server (Tomcat). Serves the REST API.
- `spring-boot-starter-webflux` — WebClient only, for outbound HTTP calls.
  The server is not reactive; do not return `Mono`/`Flux` from controllers.
- Testcontainers 2.x artifact names use the `testcontainers-` prefix
  (e.g. `testcontainers-postgresql`, `testcontainers-junit-jupiter`).
  The 1.x names (`postgresql`, `junit-jupiter`) will not resolve under Boot 4.x.
- AWS SDK version is pinned via the AWS BOM in `dependencyManagement`.
  Boot 4.x manages Testcontainers; no Testcontainers BOM entry needed.