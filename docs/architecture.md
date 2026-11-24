# Architecture and API conventions

Refs #29 · Owner: Maksudbek · Read together with [`wok-and-roll-back/README.md`](../wok-and-roll-back/README.md) (layers, error format, how to run) and [`HOW-WE-WORK.md`](../HOW-WE-WORK.md).

If something here does not fit your task, write in the issue and we change this file. Do not silently do it another way.

## 1. Big picture

```
React + TypeScript (Vite)  ──HTTP JSON /api/v1──▶  Spring Boot: controller → service → repository  ──▶  PostgreSQL (Flyway)
```

- **Prices are calculated only in the backend `pricing` package.** The frontend shows the numbers from the API and never calculates totals.
- One backend app, one database, one frontend app. Folders: `wok-and-roll-back`, `wok-and-roll-front`, `wok-and-roll-qa`.

## 2. API rules

| Rule | Example |
|---|---|
| Base path `/api/v1`, plural nouns, kebab-case | `/api/v1/products`, `/api/v1/pickup-points` |
| Country is a query parameter (3-letter code from the plan) | `?country=UZB` (UZB, KAZ, GEO, UKR, CHN, GBR, USA) |
| JSON fields in camelCase | `orderDate`, `amountInclVat` |
| Dates `YYYY-MM-DD`, date-time ISO-8601 in UTC | `"orderDate": "2026-11-05"` |
| Money: `BigDecimal` with scale 2 as a JSON number, plus a `currency` field | `"total": 446.20, "currency": "UZS"` |
| Rates as a percent number | `"vatRate": 15.00`, `"discountRate": 3.00` |
| Request and response objects are DTO records: `XxxRequest`, `XxxResponse` in the `dto` package. Never return entities. | `CartQuoteRequest` |
| Validation with Bean Validation on request DTOs, short English messages | `@Min(1) "must be 1 or more"` |
| Every endpoint has a Swagger `@Operation(summary = ...)`, updated in the same PR | |

**Status codes:** 200 read/update · 201 create (with body) · 204 delete · 400 validation · 401/403 auth · 404 not found · 409 conflict (slot full, table taken, changed by someone else) · 422 idempotency key reused · 429 too many login attempts · 500 unexpected.

**Error body (already in `common.error`, #31):** `{ "code": "VALIDATION_ERROR", "message": "...", "fields": { "quantity": "must be 1 or more" } }`. Existing codes: `NOT_FOUND`, `VALIDATION_ERROR`, `MALFORMED_REQUEST`, `INTERNAL_ERROR`. Cart and pricing (#116, #119) add `UNKNOWN_COUNTRY`, `UNKNOWN_PRODUCT`, `PRODUCT_NOT_SOLD`, `OUT_OF_STOCK`, `INVALID_MODIFIER` (all 400) and `PRICING_CONFIG_MISSING` (500). From v1.1 the body also has `requestId`, and the full list of codes (`PAST_DATE`, `SLOT_FULL`, `PRICE_CHANGED`, …) is in [`api-contract.md`](api-contract.md) §4. New codes are `UPPER_SNAKE_CASE`.

## 3. Endpoints

All endpoints, with requests, responses, errors, roles, owners and sprints, are in [`api-contract.md`](api-contract.md) (v1.1, #142). Change the contract first (its §9), then the code. This section keeps only the rules that shape the code:

- **Totals only from the server:** cart and checkout show `POST /api/v1/cart/quote`; `POST /api/v1/orders` prices the cart again and never trusts the client (section 1).
- **One café resource** (`/api/v1/cafes`) for pickup points and table booking. Every delivery zone is served by one café, and time slots belong to a café.
- **Local time:** order dates, slots and bookings use the time zone of the café's country (`CountryClock`).
- **Roles:** the guest flow is public; `/me/**` any logged-in user; `/staff/**` STAFF or ADMIN; `/reports/**` VIEWER or ADMIN; `/admin/**` ADMIN; everything else is denied by default.
- **One code path for reports:** the JSON, the PDF and the CSV use the same service methods, so the numbers are the same.

**Reports and currencies (mentor, 2 Nov):** every country is shown in its own currency. No conversion. The region chart shows the **number of orders**; money per country is in the table, in local currency. The month chart shows one country, so it uses that country's currency.

## 4. Database and Flyway

- **`V1__base_schema.sql` belongs to #32** (Abdulaziz): country, currency, category, product, regional price, modifier, stock.
- **After V1, use timestamp versions** so three developers never pick the same number: `V2026_11_05_1430__vat_and_discount_tables.sql` (`V` + date + time + `__` + short name). Flyway runs them in order after V1.
- Never edit a migration that is merged into `dev`. Add a new one.
- Seed data for the 5 countries is a migration too (#33). Demo sales history for reports is a separate migration (Timur, Stage 5).
- Names: `snake_case`, singular tables. **`order` and `user` are reserved words in PostgreSQL**: use `customer_order` and `app_user` (the ERD names stay as concepts).
- Money columns `numeric(12,2)`. Timestamps `timestamptz` in UTC.
- Discount thresholds are stored **per country, in local currency** (`discount_threshold.country_id NOT NULL`, as in #88). The values come from the technical task table (100 / 500 / 700 / 1000 / 5000). The mentor allowed other numbers if they make more sense; the logic stays the same.
- Local times (`slot_start`, `opens_at`) are `time` columns in the café's local time; moments are `timestamptz` in UTC. Each country has its IANA time zone in `country.time_zone` (#143).
- Bookings use the `btree_gist` extension for the "no double booking" constraint. It is a trusted extension (PostgreSQL 13+), so the database owner can create it.
- Migration order matters for foreign keys: the auth tables (#134) and the cafés (#143) come before the order tables (#144).

## 5. Frontend ↔ backend

- **Dev proxy:** Vite proxies `/api` to `http://localhost:8080`, so no CORS setup is needed for local work.
- **One API client:** `src/services/api.ts` (split it into `src/services/api/` when it grows). Until an endpoint exists, use mock JSON copied from the examples in `api-contract.md`.
- **Country:** chosen in the header, saved in the browser, sent as `?country=` with every request.
- **Money on screen:** `Intl.NumberFormat` with 2 decimals and the currency code from the API.
- **Components:** use the Figma tokens. For complex components (Select, Date picker, Toast, dialogs) use a ready accessible library (Radix UI Primitives, react-day-picker) and style it with our tokens. Charts: Chart.js (Project Plan v2). Do not write these from scratch.
- **Auth:** the token is in `localStorage` (`wr.auth`) and goes into `Authorization: Bearer`. Drop it when `exp` has passed; on any `401` drop it and go to Log in, keeping the cart.
- **Checkout and booking:** one `Idempotency-Key` per attempt (`crypto.randomUUID()`, or a `crypto.getRandomValues()` UUID where `randomUUID` is unavailable, such as plain http by IP); the button is disabled while the request runs.
- **Downloads (PDF, CSV):** `fetch` with the token → Blob → open or save. Never put the token in a URL.

## 6. Branches, PRs and CI

- Branch from `dev`: `feature/<issue>-short-name`. Open a **Draft PR on the first day** and push at least once per working day.
- PR description has `Refs #N` and "How to test". Squash merge into `dev` by the Team Lead.
- **CI must be green before merge** (`.github/workflows`, #30):
  - Backend: Java 21, `mvn verify` (tests + Spotless format check).
  - Frontend: `npm ci`, `lint` (when the script exists), `build` (TypeScript + Vite).
  - QA: Playwright smoke test is added in #41.

## 7. Security (Stage 4, bonus)

JWT: design in #35, code in #134 (`wok-and-roll-back/docs/auth.md`). Roles: `ADMIN`, `STAFF`, `VIEWER`, `CUSTOMER`. The role matrix, the login limit and the OWASP API Security Top 10 checklist are in `api-contract.md` §8 (#65). The guest flow (menu, cart, checkout, booking) is public.
