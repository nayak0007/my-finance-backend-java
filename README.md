# Finance Tracker API (Java 21)

Spring Boot 3.4 / Java 21 port of [my-finance-backend](https://github.com/nayak0007/my-finance-backend). Same REST contract, INR integers, Neon Auth JWTs, Neon PostgreSQL.

## Stack

- Java 21, Spring Boot 3.4 (Web, Security, Data JPA, JDBC)
- Flyway migrations (`src/main/resources/db/migration/V1__init.sql`)
- PostgreSQL (local Docker or Neon)
- Neon Auth (Managed Better Auth) for signup, login, refresh, OAuth, and password reset
- Nimbus JWT: HS256 (`NEON_JWT_SECRET`) for local tests, or Neon JWKS / EdDSA in production
- Apache PDFBox + heuristic/LLM statement import

## Quick start

```
cp .env.example .env
```

Set `DATABASE_URL` and `NEON_AUTH_URL`. Optional: `AUTH_REDIRECT_URL` — fallback landing URL used by password-recovery emails when the app does not send `redirect_url` (e.g. `myfinancetracker://reset-password` or `https://app.example.com/reset-password`).

`DATABASE_URL` may be either a JDBC URL or a libpq URL:

```
jdbc:postgresql://localhost:5432/finance
postgres://finance:finance@localhost:5432/finance
postgresql://USER:PASSWORD@ep-xxx-pooler.REGION.aws.neon.tech/neondb?sslmode=require
```

Libpq-style URLs (including Neon pooler) are converted to JDBC automatically. Public remote hosts get `sslmode=require`.

```
# optional local Postgres
docker compose up -d

mvn flyway:migrate
mvn spring-boot:run -Dspring-boot.run.arguments=--seed
```

API: `http://localhost:3000`

Health: `GET /health`

Seed is optional (`--seed`). It writes demo ledger rows for `SEED_USER_ID`. Skip it for real signups.

## Environment

| Variable | Required | Notes |
| --- | --- | --- |
| `DATABASE_URL` | yes | Local JDBC URL or Neon pooled URI (`...-pooler...neon.tech`) |
| `DATABASE_USER` | no | Only if `DATABASE_URL` has no `user:password@` |
| `DATABASE_PASSWORD` | no | Only if `DATABASE_URL` has no `user:password@` |
| `NEON_AUTH_URL` | yes (for real auth) | Console → Auth, no trailing slash. Example: `https://ep-xxx.neonauth.REGION.aws.neon.tech/neondb/auth` |
| `NEON_JWT_SECRET` | no | HS256 for local tests. Omit in production so JWTs are verified via JWKS (EdDSA) |
| `AUTH_REDIRECT_URL` | no | Fallback password-reset landing URL |
| `CORS_ORIGINS` | no | Comma-separated frontend origins |
| `OPENAI_API_KEY` | no | Leave blank for heuristic import |
| `SEED_USER_ID` | no | Demo user for `--seed` |

Copy values from Neon Console:

- **Postgres:** Project Dashboard → Connect. Prefer the **pooled** hostname (`-pooler`) for the running app.
- **Auth:** Project → Branch → Auth. Copy the Auth base URL into `NEON_AUTH_URL`. Enable email/password and add trusted domains for your app origin.

## Auth

REST contract is unchanged:

| Method | Path |
| --- | --- |
| POST | `/auth/signup` |
| POST | `/auth/login` |
| POST | `/auth/refresh` |
| POST | `/auth/logout` |
| POST | `/auth/forgot-password` |
| POST | `/auth/reset-password` |
| GET | `/auth/me` |
| GET | `/auth/oauth/{google\|apple\|github}` |

`/api/v1/*` requires `Authorization: Bearer <access_token>`.

Neon Auth issues a short-lived JWT (`access_token`, 15 minutes) and an opaque session token used as `refresh_token`. Production JWTs are EdDSA (Ed25519) and verified against `{NEON_AUTH_URL}/.well-known/jwks.json`.

OAuth: `GET /auth/oauth/google?redirect_to=...` returns `{ "url": "..." }` pointing at Neon Auth `/sign-in/social`. Register `{NEON_AUTH_URL}/callback/{provider}` with the identity provider and add the app origin under Neon → Auth → Trusted domains.

### Forgot / reset password

```
POST /auth/forgot-password   { "email": "you@example.com", "redirect_url": "myfinancetracker://reset-password" }
POST /auth/reset-password    { "password": "new-pass-1234", "token": "<token from email link>" }
                             or Authorization: Bearer <token from email link>
```

`forgot-password` calls Neon Auth `request-password-reset`, which emails a one-time reset link. It always answers `200 { "ok": true }` so the API cannot be used to probe which emails are registered. The reset link must land somewhere the client can read `?token=...`. The app may send `redirect_url`; the server falls back to `AUTH_REDIRECT_URL`.

`reset-password` asks Neon Auth to apply the new password (min 8 chars). Expired or already-used tokens are rejected. On success the client should ask the user to sign in again.

Add the redirect origin under Neon Console → Auth → Trusted domains.

Existing password hashes from another provider cannot be imported (different algorithms). Users must sign up again or use OAuth.

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

Requires Postgres (same `DATABASE_URL` as the app). Auth integration tests use a local HS256 `NEON_JWT_SECRET` and do not call Neon Auth.

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
3. Select the repo. Render creates web service `finance-tracker-api` (Docker). Data and auth stay on **Neon** — no Render database is provisioned.
4. Fill the prompted secrets (`sync: false` in the Blueprint):

| Variable | Notes |
| --- | --- |
| `DATABASE_URL` | Neon **pooled** URI. Prefer `postgresql://USER:PASSWORD@ep-xxx-pooler.REGION.aws.neon.tech/neondb?sslmode=require`. For `jdbc:postgresql://host:5432/neondb` without userinfo, also set `DATABASE_USER` / `DATABASE_PASSWORD`. |
| `DATABASE_USER` | Optional. Used only when `DATABASE_URL` has no `user:password@`. |
| `DATABASE_PASSWORD` | Optional. From Neon → Connect. |
| `NEON_AUTH_URL` | Auth base URL from Neon Console → Auth |
| `NEON_JWT_SECRET` | Leave blank in production (JWKS / EdDSA) |
| `CORS_ORIGINS` | Comma-separated frontend origins (Expo / web) |
| `AUTH_REDIRECT_URL` | Password-recovery deep link or web URL |
| `OPENAI_API_KEY` | Optional; leave blank for heuristic import |

Use the pooled hostname (`-pooler`) for the running app. Flyway runs on boot (`baseline-on-migrate`). Prefer a **direct** (non-pooler) URL if a migration needs session-level Postgres features. The process binds `0.0.0.0:$PORT` (Render default `10000`). Health check: `GET /health`.

### Manual web service (no Blueprint)

- **Language:** Docker
- **Dockerfile Path:** `./Dockerfile`
- **Health Check Path:** `/health`
- Set `DATABASE_URL` to the Neon pooled URL and `NEON_AUTH_URL` to the Auth base URL.
- If the database URL includes `user:password@`, skip `DATABASE_USER` / `DATABASE_PASSWORD`. If it does not, set both.

Spring Boot converts libpq URLs to JDBC and enables `sslmode=require` for public remote hosts. Credentials embedded in `DATABASE_URL` win over `DATABASE_USER` / `DATABASE_PASSWORD`.

Free instances have 512 MB RAM. If the JVM OOMs, upgrade the web service plan (e.g. `0.5c-512mb` or `1c-2g`).
