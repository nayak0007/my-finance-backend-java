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
