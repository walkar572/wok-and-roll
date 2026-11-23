# wok-and-roll-back — Backend (Java, Spring Boot)

**Owners:** Abdulaziz (`Sanakoor`), Timur (`idroxoos`), Nodir (`nodirmakhkamov`) · **Reviewer:** Maksudbek

One Spring Boot app for the whole team. Everyone works in this folder, each on their own feature package.

## Who builds what

| Package | What | Owner | Issues |
|---|---|---|---|
| `config`, `common` | Skeleton, Swagger, global error handler | Timur | [#31](https://github.com/ITPU-Teamwork-2026/t12-wok-and-roll/issues/31) |
| `db/migration` | Flyway schema + seed data for 5 countries | Abdulaziz | [#32](https://github.com/ITPU-Teamwork-2026/t12-wok-and-roll/issues/32), [#33](https://github.com/ITPU-Teamwork-2026/t12-wok-and-roll/issues/33) |
| `country`, `catalog` | Countries, categories, products, regional prices | Abdulaziz | [#50](https://github.com/ITPU-Teamwork-2026/t12-wok-and-roll/issues/50), [#51](https://github.com/ITPU-Teamwork-2026/t12-wok-and-roll/issues/51) |
| `catalog` (modifiers) | Modifiers API | Nodir | [#52](https://github.com/ITPU-Teamwork-2026/t12-wok-and-roll/issues/52) |
| `cart`, `pricing`, `order` | Cart quote, pricing service, orders | Timur | [#34](https://github.com/ITPU-Teamwork-2026/t12-wok-and-roll/issues/34), [#53](https://github.com/ITPU-Teamwork-2026/t12-wok-and-roll/issues/53)–[#57](https://github.com/ITPU-Teamwork-2026/t12-wok-and-roll/issues/57) |
| `auth`, `account`, `admin` | JWT, roles, order history, admin | Nodir | [#35](https://github.com/ITPU-Teamwork-2026/t12-wok-and-roll/issues/35), [#62](https://github.com/ITPU-Teamwork-2026/t12-wok-and-roll/issues/62)–[#64](https://github.com/ITPU-Teamwork-2026/t12-wok-and-roll/issues/64) |
| `delivery`, `booking`, RBAC | Pickup, delivery, time slots, booking, roles | Abdulaziz | [#58](https://github.com/ITPU-Teamwork-2026/t12-wok-and-roll/issues/58)–[#61](https://github.com/ITPU-Teamwork-2026/t12-wok-and-roll/issues/61), [#65](https://github.com/ITPU-Teamwork-2026/t12-wok-and-roll/issues/65) |
| `report` | Aggregates API, PDF | Nodir | [#66](https://github.com/ITPU-Teamwork-2026/t12-wok-and-roll/issues/66)–[#68](https://github.com/ITPU-Teamwork-2026/t12-wok-and-roll/issues/68) |
| `report` (CSV) | CSV export | Timur | [#69](https://github.com/ITPU-Teamwork-2026/t12-wok-and-roll/issues/69) |
| Docker | `docker-compose.yml` (app + PostgreSQL) | Nodir | [#36](https://github.com/ITPU-Teamwork-2026/t12-wok-and-roll/issues/36) |

## Rules

- **Layers:** `controller → service → repository`. DTOs in the API. One error format: `{code, message, fields, requestId}`.
- **Money:** `BigDecimal`, half-up, 2 decimals. Prices are calculated **only** in `pricing`; the frontend shows API numbers.
- **Style:** Google Java Style (Spotless/Checkstyle). CI must be green.
- **Tests:** unit tests for business logic. Pricing tests use the shared file in [`../wok-and-roll-qa/test-data/`](../wok-and-roll-qa/test-data/).
- **Swagger:** update it in the same PR as the endpoint.

## How to work

1. Branch from `dev`: `feature/<issue#>-short-name`.
2. Change files only in this folder (and `test-data` if you add price examples).
3. PR into `dev` with `Refs #<issue#>`, 1 approval. See [HOW-WE-WORK.md](../HOW-WE-WORK.md).


>## How to run (added in #31)

Requirements: Java 21, Maven 3.9+, PostgreSQL 16 (or Docker).

1. Start a local database (until the shared docker-compose from #36 exists):

   ```bash
   docker run --name wokroll-db -e POSTGRES_USER=wokroll -e POSTGRES_PASSWORD=wokroll \
     -e POSTGRES_DB=wokroll -p 5432:5432 -d postgres:16
   ```

2. Run the app from this folder:

   ```bash
   mvn spring-boot:run
   ```

3. Open:
    - Swagger UI: http://localhost:8080/swagger-ui.html
    - OpenAPI JSON: http://localhost:8080/v3/api-docs
    - Health check: http://localhost:8080/api/health

Settings come from environment variables (defaults in `application.yml`):
`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `SERVER_PORT`.

### Run everything with Docker Compose (#36)

From the repo root (one level above this folder):

```
docker compose up --build
```

This starts PostgreSQL 16 and the app. Then open http://localhost:8080/swagger-ui.html

- Stop: `docker compose down` (add `-v` to delete the database data too).
- Port 5432 busy on your machine? Run `DB_PORT=5433 docker compose up --build`. The app still talks to the database inside Docker on 5432.

### Checks before a PR

```bash
mvn spotless:apply   # fix formatting (Google Java Style)
mvn verify           # tests + format check, same as CI
```

### Error format

Every error has the same JSON: `{ "code": "...", "message": "...", "fields": { "field": "problem" }, "requestId": "..." }`.
In services throw `NotFoundException`, `BadRequestException` or `ApiException`
(package `common.error`) and do not write your own try/catch in controllers.

Every response has the header `X-Request-Id` (#152). A client may send its own id (letters, digits and `-`, at most 64 characters); otherwise the server creates a UUID. The same id is in the error body as `requestId` and in every log line after the level, for example `ERROR [qa-bug-152]`. QA adds it to bug reports.

### Package layout

`com.wokandroll.back.<feature>` with `controller`, `service`, `repository`, `dto` inside.
`config` (Swagger) and `common` (errors, shared code) belong to #31.


### Login and JWT (#62)

- `POST /api/v1/auth/register` and `POST /api/v1/auth/login` return `{token, role}`. Send the token as `Authorization: Bearer <token>`. In Swagger UI use the Authorize button.
- The guest flow (countries, categories, products, cart quote, orders, bookings) works without a token. `/api/v1/me/**` needs login, `/api/v1/staff/**` needs STAFF or ADMIN, `/api/v1/reports/**` needs ADMIN or VIEWER, `/api/v1/admin/**` needs ADMIN. Design: `docs/auth.md`.
- A bad or expired token gives `401 UNAUTHORIZED` with `WWW-Authenticate: Bearer` on every endpoint, also on public ones, except `POST /auth/login` and `/auth/register`, which ignore the token. The frontend deletes the token and the guest flow works again. Tokens must be HS256 with `iss: wok-and-roll` and `aud: wok-and-roll-web`.
- 5 failed logins for one email from one IP in 15 minutes give `429 TOO_MANY_ATTEMPTS` with `Retry-After` (seconds). A good login resets the counter. The counter is in memory, a restart clears it.
- A password must fit in 72 bytes (BCrypt). Letters outside English take 2 bytes, emoji 4, so a long Cyrillic password gives `400 VALIDATION_ERROR` on `password`.
- Env: `JWT_SECRET` (32+ characters, required), `JWT_TTL_HOURS` (default 24). Without `JWT_SECRET` the app refuses to start. For local runs without Docker use `SPRING_PROFILES_ACTIVE=dev`, then a public development secret is used. Never use the dev profile for the demo.
- Docker: once, from the repo root, `echo "JWT_SECRET=$(openssl rand -base64 48)" > .env`. Compose reads it, the file is in `.gitignore`.
- Tests take `JWT_SECRET` from `src/test/resources/application.properties`.
- In a `@WebMvcTest` add `@Import(SecurityConfig.class)`, otherwise every request returns 401.

### Demo users (#151)

Created by the migration `V2026_11_19_0900__seed_demo_users.sql`. Only for local runs, QA and the demo, never for real data.

| Email | Password | Role |
|---|---|---|
| `admin@wokandroll.test` | `AdminDemo2026` | ADMIN |
| `staff@wokandroll.test` | `StaffDemo2026` | STAFF |
| `viewer@wokandroll.test` | `ViewerDemo2026` | VIEWER |
| `customer@wokandroll.test` | `CustomerDemo2026` | CUSTOMER |

The backend still needs `JWT_SECRET` (see above), the demo users do not change that.
