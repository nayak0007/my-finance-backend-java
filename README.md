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

Fill Supabase keys. `DATABASE_URL` may be either:

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
| GET | `/auth/me` |
| GET | `/auth/oauth/{google\|apple}` |

`/api/v1/*` requires `Authorization: Bearer <access_token>`.

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
