# Interview Training Platform Backend

## Overview
This repository contains the backend for an interview training platform.

The product loop is:
1. user configures profile and resume
2. system selects a daily interview question
3. user submits an answer
4. backend scores the answer
5. low-scoring answers are scheduled for retry
6. strong answers are archived
7. progress accumulates over time

## MVP Scope
- user profile and settings
- target companies
- resume versioning
- question catalog
- daily card API
- answer attempt API
- scoring and feedback
- retry queue
- archive API
- feed API

## Source Documents
- docs/01-product-overview.md
- docs/02-backend-architecture.md
- docs/03-db-schema.md
- docs/04-api-contracts.md
- docs/05-implementation-plan.md
- docs/06-acceptance-criteria.md

## Suggested Stack
- Kotlin + Spring Boot
- PostgreSQL
- Flyway
- Spring Data JPA
- Gradle Kotlin DSL

## Local Development Setup

### 1) Start local PostgreSQL
```bash
docker compose up -d postgres
```

Default local database settings:
- host: `localhost`
- port: `5432`
- database: `iterview`
- username: `iterview`
- password: `iterview`

You can override these with environment variables used by `docker-compose.yml`:
- `POSTGRES_DB`
- `POSTGRES_USER`
- `POSTGRES_PASSWORD`

### 2) Start the API
```bash
./gradlew bootRun
```

The app defaults to the `local` profile (`spring.profiles.default=local`), so no extra profile flag is required for local runs.

API defaults:
- base URL: `http://localhost:8080`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Swagger UI: `http://localhost:8080/swagger-ui.html`

### 3) Optional: load dummy data
```bash
PGPASSWORD=iterview psql -h localhost -p 5432 -U iterview -d iterview -f scripts/seed_dummy_data.sql
```

Demo login after seeding:
- email: `demo@example.com`
- password: `password123`

## Spring Profile Configuration

- `application.yml`: shared defaults (JPA/Flyway/common settings)
- `application-local.yml`: local developer defaults (datasource, local JWT secret, Swagger enabled, local CORS defaults)
- `application-prod.yml`: production-oriented config with required environment variables and no local DB defaults

Activate production profile explicitly:
```bash
SPRING_PROFILES_ACTIVE=prod ./gradlew bootRun
```

## Environment Variables

### Local (optional overrides)
- `DB_URL` (default: `jdbc:postgresql://localhost:5432/iterview`)
- `DB_USERNAME` (default: `iterview`)
- `DB_PASSWORD` (default: `iterview`)
- `AUTH_TOKEN_SECRET` (default: `dev-only-secret-change-me`)
- `AUTH_TOKEN_TTL_SECONDS` (default: `86400`)
- `SERVER_PORT` (default: `8080`)
- `APP_CORS_ALLOWED_ORIGINS` (default: empty; use for explicit fixed origins)
- `APP_CORS_ALLOWED_ORIGIN_PATTERNS` (default: `http://localhost:[*],http://127.0.0.1:[*],https://localhost:[*],https://127.0.0.1:[*]`)
- `SWAGGER_UI_ENABLED` (default: `true` in `local`)

### Required in `prod` profile
- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `AUTH_TOKEN_SECRET`
- `APP_CORS_ALLOWED_ORIGINS` or `APP_CORS_ALLOWED_ORIGIN_PATTERNS`

In `prod`, the application now fails fast at startup if neither CORS setting is provided.

## Migrations and Seed Data

- Flyway runs automatically on startup (`spring.flyway.enabled=true`).
- Schema is managed by Flyway migrations in `src/main/resources/db/migration`.
- Reference seed data is applied via Flyway migration `V2__seed_reference_data.sql`.
- Seed statements use idempotent `ON CONFLICT` patterns, so local re-runs are safe.
- Hibernate DDL auto-generation is disabled as a source of truth (`ddl-auto=validate`).

## Developer Startup Steps

1. `docker compose up -d postgres`
2. `./gradlew bootRun`
3. Verify health: `curl http://localhost:8080/api/health`
4. Use Swagger UI: `http://localhost:8080/swagger-ui.html`

## CI

GitHub Actions workflow: [`.github/workflows/ci.yml`](/Users/hammac/Projects/iterview-api/.github/workflows/ci.yml)

- triggers on pull requests and pushes to `main`
- uses Java 21 (Temurin)
- uses Gradle dependency/build caching
- runs:
  - `./gradlew --no-daemon build`
  - `./gradlew --no-daemon test`

CI test execution relies on Testcontainers for PostgreSQL-backed integration tests, so Docker support on the runner is expected.
