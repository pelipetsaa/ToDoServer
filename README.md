# ToDo Server (Java · Spring Boot)

A re-implementation of the Ktor backend from `ToDoList/server` in Java 21 and Spring Boot 4. It serves **the same REST API**
(paths, JSON bodies, status codes, error codes), reads **the same environment variables** and runs on **the same
PostgreSQL schema** — the Flyway migration is byte-identical, password hashes and refresh-token hashes use the same
algorithms, and access tokens carry the same claims. The Android/iOS clients need no changes, and the two servers can be
swapped on a live database.

## Stack

| Concern | Choice |
| --- | --- |
| Language / runtime | Java 21 (records, sealed types, pattern-matching `switch`), virtual threads |
| Framework | Spring Boot 4.1, Spring Framework 7, Spring MVC |
| Persistence | PostgreSQL 16, Spring Data JPA (Hibernate 7), `JdbcClient`, Flyway |
| Security | Spring Security 7 OAuth2 Resource Server (stateless HS256 JWT), bcrypt, Google ID tokens |
| Rate limiting | Bucket4j token buckets per client IP, Caffeine for bounded storage |
| API docs | springdoc-openapi 3 — Swagger UI at `/docs`, spec at `/v3/api-docs` |
| Observability | Actuator health probes, Micrometer + Prometheus, business metrics from domain events, request ids in MDC, structured JSON (ECS) logs |
| Tests | JUnit 6, Testcontainers (real PostgreSQL), ArchUnit, Mockito, AssertJ, JaCoCo |
| Build / delivery | Gradle 9 (Kotlin DSL, version catalog), layered Docker image, Docker Compose, GitHub Actions |

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
  out/persistence/    JPA entities and repositories behind the outbound ports
  out/security/       bcrypt, JWT issuing, refresh tokens, Google verification
  out/metrics/        Micrometer counters fed by domain events after commit
config/               Composition root: typed, validated properties, security chains, OpenAPI
```

## API

| Method | Path | Auth | Notes |
| --- | --- | --- | --- |
| POST | `/api/v1/auth/register` | — | 201 + token pair; rate limited |
| POST | `/api/v1/auth/login` | — | rate limited |
| POST | `/api/v1/auth/google` | — | Google ID token; links an existing email account; rate limited |
| POST | `/api/v1/auth/refresh` | — | single-use rotation |
| POST | `/api/v1/auth/logout` | — | 204 |
| GET | `/api/v1/me` | Bearer | |
| GET | `/api/v1/todos?from=&to=` | Bearer | `dueAt` in `[from, to)`, sorted |
| POST / GET / PUT / DELETE | `/api/v1/todos`, `/api/v1/todos/{id}` | Bearer | another user's todo is `404` |
| GET | `/health`, `/health/ready` | — | liveness / readiness (database) |

Errors are always `{"code": "...", "message": "..."}` with `code` one of `VALIDATION_ERROR`, `UNAUTHORIZED`,
`NOT_FOUND`, `EMAIL_TAKEN`, `TOO_MANY_REQUESTS`, `INTERNAL_ERROR`. The Ktor server's hand-written spec is kept in
[reference.yaml](src/test/resources/openapi/reference.yaml), and
[OpenApiContractTest](src/test/java/com/pelypets/todolist/server/api/OpenApiContractTest.java) checks that the
generated spec has exactly its operations.

### Additions over the Ktor server

- Kubernetes probes at `/actuator/health/liveness` and `/actuator/health/readiness`.
- Prometheus at `/actuator/prometheus` (was `/metrics`), same token rules: open in development, bearer `METRICS_TOKEN`
  when set, absent in production without a token. Adds `todo_users_registered_total{method}` and `todo_todos_created_total`.
- An hourly job deletes expired refresh tokens of users who never sign in again.
- An expired access token sent along with `/auth/refresh` or `/auth/login` is ignored instead of rejected.
- `Retry-After` on every 429.

## Running

Requires JDK 21 and Docker (Docker Desktop or Colima).

```bash
./gradlew bootRun
```

starts PostgreSQL from [compose.yaml](compose.yaml) automatically and serves on <http://localhost:8080> (Swagger UI at
<http://localhost:8080/docs>). Without Compose, against a throwaway Testcontainers database:

```bash
./gradlew bootTestRun
```

The whole stack in containers:

```bash
docker compose --profile app up --build
```

Tests (unit, architecture, and API tests against a real PostgreSQL in Testcontainers):

```bash
./gradlew build
```

## Configuration

| Variable | Default | Meaning |
| --- | --- | --- |
| `PORT` | `8080` | HTTP port |
| `APP_ENV` | `development` | `production` refuses to start without a real `JWT_SECRET` |
| `JWT_SECRET` | dev key | HS256 key, at least 32 characters; required in production |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | `jdbc:postgresql://localhost:5434/todolist`, `todolist`, `todolist` | PostgreSQL |
| `DB_MAX_POOL_SIZE` | `10` | Hikari pool size |
| `GOOGLE_CLIENT_IDS` | the app's client ids | Comma-separated audiences accepted for Google ID tokens |
| `AUTH_RATE_LIMIT_PER_MINUTE` | `20` | Register + login + Google sign-in per client IP |
| `TRUST_PROXY` | `false` | Take the client IP from the last `X-Forwarded-For` hop (enable behind Render or another proxy) |
| `METRICS_TOKEN` | — | Bearer token for `/actuator/prometheus` |
| `LOGGING_STRUCTURED_FORMAT_CONSOLE` | — (`ecs` in the image) | JSON log format |

The Docker image sets `APP_ENV=production`, so deploying it to Render with the existing environment variables works
as before.
