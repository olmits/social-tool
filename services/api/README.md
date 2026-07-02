# social-api

Core REST API for the social media publishing tool. Handles account management, draft lifecycle, AI-assisted drafting via the Claude API, and enqueuing approved posts to SQS for the Go publisher workers.

**Stack:** Java 21 · Spring Boot 4.1 · PostgreSQL · Flyway · AWS SDK v2

---

## Prerequisites

- Java 21
- Maven (or use the included `./mvnw` wrapper)
- PostgreSQL 16 (local instance or Docker)
- Docker (for containerised runs and integration tests)

---

## Running locally

### 1. Start a local database

```bash
docker run -d \
  --name socialapi-db \
  -e POSTGRES_DB=socialapi \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  postgres:16
```

### 2. Set environment variables

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/socialapi
export SPRING_DATASOURCE_USERNAME=postgres
export SPRING_DATASOURCE_PASSWORD=postgres

# Optional — required for AI drafting and platform adapters
export CLAUDE_API_KEY=...
export BLUESKY_APP_PASSWORD=...
```

Or use the local profile (create `src/main/resources/application-local.yml` — already gitignored — with the values above) and activate it:

```bash
export SPRING_PROFILES_ACTIVE=local
```

### 3. Run the application

```bash
./mvnw spring-boot:run
```

The API starts on `http://localhost:8080`.

### Running tests

Integration tests spin up their own Postgres container via Testcontainers — Docker must be running, no manual DB setup needed.

```bash
# All tests
./mvnw test

# Unit tests only (skip integration)
./mvnw test -Dgroups='!integration'

# Single test class
./mvnw test -Dtest=DraftServiceTest
```

---

## Running with Docker Compose

The included `docker-compose.yml` starts both the API and a PostgreSQL instance.

```bash
# Build the image and start all services
docker compose up --build

# Run in the background
docker compose up --build -d

# Stop and remove containers
docker compose down

# Stop and also remove the database volume
docker compose down -v
```

The API will be available at `http://localhost:8080` once the database health check passes.

### Passing credentials

Platform credentials are not in `docker-compose.yml`. Pass them at runtime:

```bash
CLAUDE_API_KEY=sk-... BLUESKY_APP_PASSWORD=... docker compose up --build
```

Or create a `.env` file in this directory (gitignored) and Docker Compose will pick it up automatically:

```env
CLAUDE_API_KEY=sk-...
BLUESKY_APP_PASSWORD=...
```

---

## Health check

```
GET /actuator/health
```

---

## Project layout

```
src/main/java/com/omits/social_api/
├── account/      # Account entity, repo, service, OAuth flows
├── adapter/      # PlatformAdapter interface + bluesky/, mastodon/, reddit/
├── draft/        # Draft entity, state machine, DraftService
├── drafting/     # Claude API client, prompt building, DraftingService
├── affiliate/    # AffiliateLinkService, disclosure enforcement
└── config/       # SecurityConfig, WebClientConfig, AwsConfig

src/main/resources/
├── application.yml           # Base config (env-var driven)
├── application-local.yml     # Local dev overrides (gitignored)
└── db/migration/             # Flyway SQL migrations
```
