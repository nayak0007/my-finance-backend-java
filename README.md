# Finance Tracker API (Java 21)

Spring Boot 3.4 / Java 21 port of [my-finance-backend](https://github.com/nayak0007/my-finance-backend). Same REST contract, INR integers, Supabase Auth JWTs, PostgreSQL.

## Stack

- Java 21, Spring Boot 3.4 (Web, Security, Data JPA, JDBC)
- Flyway migrations (`src/main/resources/db/migration/V1__init.sql`)
- PostgreSQL (local or Supabase)
- Nimbus JWT (HS256 secret or JWKS)
- Apache PDFBox + heuristic/LLM statement import

## Quick start

```
cp .env.example .env
```

Fill Supabase keys. Optional: `AUTH_REDIRECT_URL` — fallback landing URL used by
password-recovery emails when the app does not send `redirect_url` (e.g.
`myfinancetracker://reset-password` or `https://app.example.com/reset-password`).

`DATABASE_URL` may be either:

```
jdbc:postgresql://localhost:5432/finance
postgres://finance:finance@localhost:5432/finance
```

Libpq-style URLs (including Supabase pooler) are converted automatically.

```
# optional local Postgres
docker compose up -d

mvn flyway:migrate
mvn spring-boot:run -Dspring-boot.run.arguments=--seed
```

API: `http://localhost:3000`  
Health: `GET /health`

Seed is optional (`--seed`). It writes demo ledger rows for `SEED_USER_ID`. Skip it for real signups.

## Auth

Same as the Node API:

| Method | Path |
| --- | --- |
| POST | `/auth/signup` |
| POST | `/auth/login` |
| POST | `/auth/refresh` |
| POST | `/auth/logout` |
| POST | `/auth/forgot-password` |
| POST | `/auth/reset-password` |
| GET | `/auth/me` |
| GET | `/auth/oauth/{google\|apple}` |

`/api/v1/*` requires `Authorization: Bearer <access_token>`.

### Forgot / reset password

```
POST /auth/forgot-password   { "email": "you@example.com" }
POST /auth/reset-password    { "password": "new-pass-1234" }
                             Authorization: Bearer <one-time token from email link>
```

`forgot-password` delegates to Supabase's `recover` endpoint, which emails a
one-time reset link. It always answers `200 { "ok": true }` so the API cannot
be used to probe which emails are registered. The reset link must land
somewhere the client can read the recovery tokens (`#access_token=...&type=recovery`)
from — the app passes its own deep-link URL as `redirect_url` in the request
body, and the server falls back to `AUTH_REDIRECT_URL` when it is omitted.

`reset-password` verifies the bearer token locally and asks Supabase to apply
the new password (min 8 chars). Supabase rejects links that are expired or were
already used. On success all locally tracked sessions for the user are revoked
and the client should ask the user to sign in again.

Make sure the redirect target is allow-listed under Supabase → Authentication →
URL Configuration when using the hosted GoTrue verify page.

## API (`/api/v1`)

- `GET /categories`
- `GET|POST /transactions` `PATCH|DELETE /transactions/:id` `POST /transactions/bulk`
- `GET|POST /accounts` `PATCH|DELETE /accounts/:id` (`?force=true` to delete with transactions)
- `GET /summary/monthly?months=12`
- `GET /summary/spending-by-category?month=YYYY-MM`
- `GET /investments/holdings|history|growth`
- `GET /recurring`
- `GET|POST /goals` `PATCH /goals/:id`
- `GET /insights`
- `POST /import/parse` multipart `file`

Errors: `{ "error": { "code", "message" } }`.

## Tests

Requires Postgres (same `DATABASE_URL` as the app).

```
mvn test
```

## Ollama

No real API key required:

```
OPENAI_BASE_URL=http://localhost:11434/v1
OPENAI_MODEL=llama3.1
OPENAI_API_KEY=ollama
```

## Deploy on Render

This repo includes `render.yaml` (Blueprint) and a production `Dockerfile`. Render has no native Java runtime, so the web service builds the Docker image.

1. Push this repo to GitHub.
2. In the [Render Dashboard](https://dashboard.render.com), click **New > Blueprint**.
3. Select the repo. Render creates web service `finance-tracker-api` (Docker). Data stays on **Supabase Postgres** — no Render database is provisioned.
4. Fill the prompted secrets (`sync: false` in the Blueprint):

| Variable | Notes |
| --- | --- |
| `DATABASE_URL` | Supabase **session pooler** URI (`postgres://postgres.<ref>:...@aws-0-<region>.pooler.supabase.com:5432/postgres`) |
| `SUPABASE_URL` | Auth project URL |
| `SUPABASE_ANON_KEY` | Login / refresh / OAuth |
| `SUPABASE_SERVICE_ROLE_KEY` | Admin signup / password reset |
| `SUPABASE_JWT_SECRET` | Optional HS256; omit to use JWKS |
| `CORS_ORIGINS` | Comma-separated frontend origins (Expo / web) |
| `AUTH_REDIRECT_URL` | Password-recovery deep link or web URL |
| `OPENAI_API_KEY` | Optional; leave blank for heuristic import |

Use the session pooler, not `db.<project>.supabase.co` (IPv6-only and often unreachable from Render). Flyway runs on boot (`baseline-on-migrate`). The process binds `0.0.0.0:$PORT` (Render default `10000`). Health check: `GET /health`.

### Manual web service (no Blueprint)

- **Language:** Docker
- **Dockerfile Path:** `./Dockerfile`
- **Health Check Path:** `/health`
- Set `DATABASE_URL` to the Supabase session pooler URL.

Spring Boot converts libpq URLs to JDBC and enables `sslmode=require` for public remote hosts. Do not also set `DATABASE_USER` / `DATABASE_PASSWORD` unless the URL has no credentials.

Free instances have 512 MB RAM. If the JVM OOMs, upgrade the web service plan (e.g. `0.5c-512mb` or `1c-2g`).

