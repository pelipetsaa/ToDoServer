# ToDo Server (Java · Spring Boot)

A re-implementation of the Ktor backend from `ToDoList/server` in Java 25 and Spring Boot 4. It serves **the same REST API**
(paths, JSON bodies, status codes, error codes), reads **the same environment variables** and runs on **the same
PostgreSQL database** — password hashes and refresh-token hashes use the same algorithms, and access tokens carry the
same claims. The Android/iOS clients need no changes, and the two servers can be swapped on a live database (see
[Schema ownership](#schema-ownership)).

## Stack

| Concern | Choice |
| --- | --- |
| Language / runtime | Java 25 (records, sealed types, pattern-matching `switch`), virtual threads |
| Framework | Spring Boot 4.1, Spring Framework 7, Spring MVC |
| Persistence | PostgreSQL 16, `JdbcClient` with hand-written SQL, Flyway |
| Security | Spring Security 7 OAuth2 Resource Server (stateless HS256 JWT), bcrypt, Google ID tokens |
| Rate limiting | Bucket4j token buckets per client IP and per account, Caffeine for bounded storage |
| API docs | springdoc-openapi 3 — Swagger UI at `/docs`, spec at `/v3/api-docs` |
| Observability | Actuator health probes, Micrometer + Prometheus, business metrics from domain events, request ids in MDC, structured JSON (ECS) logs |
| Tests | JUnit 6, Testcontainers (real PostgreSQL), ArchUnit, Mockito, AssertJ, JaCoCo (85% minimum) |
| Build / delivery | Gradle 9 (Kotlin DSL, version catalog), layered Docker image, Docker Compose, GitHub Actions, Dependabot |

## Architecture

Hexagonal (ports & adapters); dependencies point inwards and [ArchitectureTest](src/test/java/com/pelypets/todolist/server/ArchitectureTest.java)
fails the build if they stop doing so.

```
domain/               Pure Java: records, rules, sealed exceptions, domain events. No Spring.
application/
  port/in/            Use cases the outside world calls (AuthUseCase, TodoUseCase, MaintenanceUseCase)
  port/out/           What the use cases need (repositories, hashing, tokens, Google)
  service/            Use-case implementations; transactions live here
adapter/
  in/web/             REST controllers, DTOs, error mapping, rate limiting, request ids, security glue
  in/scheduling/      Hourly purge of expired refresh tokens
  out/persistence/    SQL behind the outbound ports
  out/security/       bcrypt, JWT issuing, refresh tokens, Google verification
  out/metrics/        Micrometer counters fed by domain events after commit
config/               Composition root: typed, validated properties, security chains, OpenAPI
```

Transactions are short. Sign-in hashes the password and verifies Google tokens before it opens one, so a burst of
logins cannot exhaust the connection pool while bcrypt runs.

## API

| Method | Path | Auth | Notes |
| --- | --- | --- | --- |
| POST | `/api/v1/auth/register` | — | 201 + token pair; rate limited |
| POST | `/api/v1/auth/login` | — | rate limited per IP and per account |
| POST | `/api/v1/auth/google` | — | Google ID token; links an existing email account when Google manages the mailbox; rate limited |
| POST | `/api/v1/auth/refresh` | — | single-use rotation; reusing a rotated token revokes its session |
| POST | `/api/v1/auth/logout` | — | 204 |
| GET | `/api/v1/me` | Bearer | |
| DELETE | `/api/v1/me` | Bearer | 204; deletes the account, its todos and sessions |
| GET | `/api/v1/todos?from=&to=` | Bearer | `dueAt` in `[from, to)`, sorted; the range spans at most a year |
| POST / GET / PUT / DELETE | `/api/v1/todos`, `/api/v1/todos/{id}` | Bearer | another user's todo is `404`; `ETag` / `If-Match` versioning |
| GET | `/health`, `/health/ready` | — | liveness / readiness (database) |

Errors are always `{"code": "...", "message": "..."}` with `code` one of `VALIDATION_ERROR`, `UNAUTHORIZED`,
`NOT_FOUND`, `EMAIL_TAKEN`, `CONFLICT`, `TOO_MANY_REQUESTS`, `INTERNAL_ERROR`. The Ktor server's hand-written spec is
kept in [reference.yaml](src/test/resources/openapi/reference.yaml), and
[OpenApiContractTest](src/test/java/com/pelypets/todolist/server/api/OpenApiContractTest.java) checks that the
generated spec has exactly its operations plus the additions below.

### Additions over the Ktor server

- `DELETE /api/v1/me` deletes the account (required by the App Store and GDPR).
- Single-todo responses carry the todo's version as `ETag`; `PUT` with `If-Match` answers `409 CONFLICT` instead of
  overwriting an edit made from another device. Without `If-Match` the last write wins, as before.
- Refresh-token reuse detection: a rotated token presented again revokes the whole session (every token of that
  sign-in) and counts `todo_refresh_tokens_reused_total`.
- At most `MAX_SESSIONS_PER_USER` sessions per user; signing in once more ends the oldest.
- Password sign-in is limited per account as well as per IP, so guessing from many addresses is no faster.
- Google sign-in links an existing password account only for Gmail and Google Workspace addresses, where Google owns
  the mailbox.
- Limits per user: `MAX_TODOS_PER_USER` todos, a year per list request.
- Kubernetes probes at `/actuator/health/liveness` and `/actuator/health/readiness`.
- Prometheus at `/actuator/prometheus` (was `/metrics`), same token rules: open in development, bearer `METRICS_TOKEN`
  when set, absent in production without a token. Adds `todo_users_registered_total{method}`, `todo_todos_created_total`
  and `todo_refresh_tokens_reused_total`.
- An hourly job deletes expired refresh tokens of users who never sign in again; one instance at a time does it.
- An expired access token sent along with `/auth/refresh` or `/auth/login` is ignored instead of rejected.
- `Retry-After` on every 429. Unknown paths answer `404` rather than `401`.

### Schema ownership

This server owns the schema from `V2` on: [V1](src/main/resources/db/migration/V1__init.sql) is the Ktor server's
migration byte for byte, and [V2](src/main/resources/db/migration/V2__sessions_and_versions.sql) adds sessions,
used-token records and todo versions. The Ktor server keeps running on a migrated database — Flyway ignores migrations
it does not know by default, and every new column has a default for the rows it writes — but it neither detects token
reuse nor bumps todo versions, so retire it once this server is live. New migrations go here only.

## Running

Requires Docker (Docker Desktop or Colima). Gradle downloads JDK 25 if the machine does not have it.

```bash
./gradlew bootRun
```

starts PostgreSQL from [compose.yaml](compose.yaml) automatically and serves on <http://localhost:8080> (Swagger UI at
<http://localhost:8080/docs>) with `APP_ENV=development`. Without Compose, against a throwaway Testcontainers database:

```bash
./gradlew bootTestRun
```

The whole stack in containers:

```bash
docker compose --profile app up --build
```

Tests (unit, architecture, and API tests against a real PostgreSQL in Testcontainers) and the coverage check:

```bash
./gradlew build
```

## Configuration

| Variable | Default | Meaning |
| --- | --- | --- |
| `PORT` | `8080` | HTTP port |
| `APP_ENV` | `production` | `development` signs tokens with a key published in this repository; `bootRun` sets it |
| `JWT_SECRET` | — | HS256 key, at least 32 characters; required in production |
| `DB_URL` | `jdbc:postgresql://localhost:5434/todolist` | PostgreSQL |
| `DB_USER`, `DB_PASSWORD` | — | PostgreSQL credentials; `bootRun` and the tests take them from Compose or Testcontainers |
| `DB_MAX_POOL_SIZE` | `10` | Hikari pool size |
| `GOOGLE_CLIENT_IDS` | the app's client ids | Comma-separated audiences accepted for Google ID tokens |
| `AUTH_RATE_LIMIT_PER_MINUTE` | `20` | Register + login + Google sign-in per client IP |
| `LOGIN_RATE_LIMIT_PER_ACCOUNT` | `10` | Password sign-in attempts per account per minute |
| `TRUST_PROXY` | `false` | Take the client IP from `X-Forwarded-For` (enable behind Render or another proxy) |
| `TRUSTED_PROXY_HOPS` | `1` | Proxies that append to `X-Forwarded-For`, e.g. `2` for a CDN in front of Render |
| `MAX_SESSIONS_PER_USER` | `10` | Refresh tokens a user may hold at once |
| `MAX_TODOS_PER_USER` | `10000` | Todos one user may store |
| `METRICS_TOKEN` | — | Bearer token for `/actuator/prometheus` |
| `API_DOCS_ENABLED` | `true` (`false` in the image) | Serve Swagger UI and the OpenAPI spec |
| `LOGGING_STRUCTURED_FORMAT_CONSOLE` | — (`ecs` in the image) | JSON log format |

Rate limits are kept in each instance's memory, so with several instances each enforces them on its own share of the
traffic.

The Docker image sets `APP_ENV=production`, so deploying it to Render with the existing environment variables works
as before.
