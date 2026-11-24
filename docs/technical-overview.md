# Wok & Roll — technical overview

Team 12 · Teamwork 2026 · at most 3 pages. Details: [API contract](api-contract.md),
[ERD](../wok-and-roll-back/docs/erd.md), [pricing](../wok-and-roll-back/docs/pricing.md).

## 1. Architecture

React + TypeScript single-page app (Vite) → REST API `/api/v1` (Spring Boot 3.5, Java 21) →
PostgreSQL 16. The backend is split by feature (`catalog`, `cart`, `pricing`, `order`, `cafe`,
`slot`, `booking`, `account`, `admin`, `staff`, `report`), each with controller → service →
repository (plain SQL with named parameters through `JdbcClient`). The schema is created only by
Flyway migrations. Every error has one shape, `{code, message, fields, requestId}`, from one
global handler. Money is `BigDecimal` / `numeric(12,2)` in the local currency; the frontend never
calculates totals, it shows the numbers of the quote.

## 2. Data model

| Table | Key fields | Notes |
|---|---|---|
| `country`, `currency` | `code` (UZB…), `currency_code`, `time_zone` | 6 countries, each with its own currency and IANA time zone |
| `vat_rate` | `country_id`, `rate` | one rate per country, percent |
| `discount_threshold` | `country_id`, `min_amount`, `discount_rate` | 5 tiers per country, local currency |
| `category`, `product` | `name`, `visible`, `version`; `pieces`, `weight_grams`, `volume_ml` | hidden instead of deleted, so old orders stay correct; the portion is shown as "8 pcs · 250 g" |
| `regional_price` | `product_id`, `country_id`, `price`, `in_stock`, `version` | no row = not sold in that country |
| `modifier`, `modifier_price` | `type` SIZE / EXTRA, `price_delta` per country | exactly one SIZE if a dish has sizes |
| `cafe`, `delivery_zone` | opening hours, `slot_capacity`; zone `fee`, `cafe_id` | a zone is cooked by one café of the same country |
| `slot_reservation` | `cafe_id`, `slot_date`, `slot_start`, `reserved` | only taken places are stored; slots are calculated |
| `cafe_table`, `booking` | seats 2–8; `during tstzrange` | exclusion constraint: one table is never booked twice |
| `app_user`, `role` | BCrypt hash, role ADMIN / STAFF / VIEWER / CUSTOMER | JWT HS256, 24 h |
| `customer_order` | scenario, slot, contact, every price step, `status`, `idempotency_key` | prices are copied, never recalculated later |
| `order_item`, `order_item_modifier` | copied names, `unit_price`, `line_total` | |

Order statuses: `NEW → CONFIRMED → DONE`, or `NEW`/`CONFIRMED → CANCELLED` (frees the slot place).
Concurrency is solved in the database: an atomic upsert for slot places
(`… DO UPDATE … WHERE reserved < capacity`), the exclusion constraint for tables, and a unique
`Idempotency-Key` for orders and bookings.

## 3. Price formula

Calculated only on the server (`pricing` package), rounded `HALF_UP` to 2 decimals after each
step. The order is priced again on placement; if the total differs from the one the guest saw,
the API answers `409 PRICE_CHANGED` and saves nothing.

| Step | Formula |
|---|---|
| Unit price | `price + Σ priceDelta` of the chosen modifiers |
| Line total | `unitPrice × quantity` (quantity: whole number 1–99) |
| Subtotal | `Σ lineTotal` |
| VAT | `round(subtotal × vatRate / 100)` |
| Amount incl. VAT | `subtotal + VAT` |
| Discount | `round(amountInclVat × rate / 100)`, rate of the highest threshold `≤ amountInclVat` |
| Delivery fee | the zone fee for delivery, `0.00` for pickup; added last, outside VAT and discount |
| **Total** | `amountInclVat − discount + deliveryFee` |

Example (UZB, the Tashkent: Center zone, fee 20,000.00): 2 × Salmon avocado roll 79,000.00 =
158,000.00 → VAT 15 % = 23,700.00 → 181,700.00 → discount 3 % = 5,451.00 → total 176,249.00 for
pickup, **196,249.00 UZS** with delivery. The same formula with the demo scale of the project plan:
subtotal 170.00 → VAT 15 % = 25.50 → 195.50 →
discount 3 % = 5.865 → **5.87** → total 195.50 − 5.87 + 20.00 = **209.63 UZS**.

### VAT by country

| Country | Currency | VAT |
|---|---|---|
| Uzbekistan (UZB) | UZS | 15 % |
| Kazakhstan (KAZ) | KZT | 20 % |
| Georgia (GEO) | GEL | 12 % |
| Ukraine (UKR) | UAH | 8 % |
| China (CHN) | CNY | 18 % |
| United Kingdom (GBR) | GBP | 20 % |

### Discount thresholds and delivery fees (local currency)

Prices, thresholds and fees are at the level of Tashkent and converted at November 2026 rates
(1 USD ≈ 12,700 UZS · 510 KZT · 2.70 GEL · 41.5 UAH · 7.15 CNY ·
0.79 GBP), so a discount means the same in
every country: 3 % from about two dishes, 15 % only for very big orders.

| Country | Currency | 3 % from | 5 % from | 7 % from | 10 % from | 15 % from | Delivery zones |
|---|---|---|---|---|---|---|---|
| UZB | UZS | 100,000 | 500,000 | 700,000 | 1,000,000 | 5,000,000 | 15,000 / 20,000 / 30,000 |
| KAZ | KZT | 4,000 | 20,000 | 28,000 | 40,000 | 200,000 | 600 / 800 / 1,200 |
| GEO | GEL | 20 | 100 | 150 | 200 | 1,000 | 3.50 / 4.50 / 6.50 |
| UKR | UAH | 300 | 1,600 | 2,300 | 3,300 | 16,000 | 50 / 65 / 100 |
| CHN | CNY | 55 | 280 | 400 | 550 | 2,800 | 8 / 11 / 17 |
| GBR | GBP | 6 | 30 | 45 | 60 | 300 | 0.95 / 1.25 / 1.85 |

Below the first threshold there is no discount. Each country has three zones: near, center, far.

## 4. Reports, PDF and CSV

- **Which orders:** `order_date` in the period, status not `CANCELLED`. Sums of the saved order
  values; each country in its own currency, so there is no money total across countries.
- **Sales by region** (`from`, `to`, at most 366 days): orders, subtotal, VAT, discount, delivery
  fee, total and average check per country; the chart shows orders per country.
- **Sales by month** (`country`, `year`): 12 months and the year totals; the chart shows the
  total per month.
- **One source of numbers:** the web page, the PDF and the CSV call the same service methods, so
  PDF = web = CSV = the reference SQL in the contract. Money is printed from `BigDecimal` with
  exactly 2 decimals; `averageCheck = total / orders`, `HALF_UP`.
- **PDF** (A4): header with the filters and the UTC time, both bar charts, the region table, the
  year KPIs (orders, subtotal, VAT, discount, delivery fee, total, average check) and the month
  table, "Page x of y".
- **CSV** (RFC 4180, UTF-8, CRLF): one file per report, a header row, money with a `.` and no
  grouping, protection against CSV injection for text cells.
- Downloads use `fetch` with the token and a Blob: the token never appears in a URL.

## 5. Roles and security

| Endpoints | Guest | Customer | Staff | Viewer | Admin |
|---|---|---|---|---|---|
| Menu, quote, cafés, zones, slots, orders, bookings, log in | ✓ | ✓ | ✓ | ✓ | ✓ |
| `/me`, `/me/orders` (own data only, another user's order is 404) | — | ✓ | ✓ | ✓ | ✓ |
| `/staff/**` | — | — | ✓ | — | ✓ |
| `/reports/**` (JSON, PDF, CSV) | — | — | — | ✓ | ✓ |
| `/admin/**` | — | — | — | — | ✓ |

Deny by default, one test per cell. Passwords: BCrypt. Tokens: HS256 with `exp`, `iss`, `aud`;
5 failed logins in 15 minutes → `429`. Admin changes use optimistic locking (`version`) and get
`409 VERSION_CONFLICT` instead of overwriting each other. The frontend mirrors the rules in its
menu and route guards; the server checks every request again.

## 6. Languages

The interface speaks the language of the chosen country: Uzbek (Latin), Kazakh, Georgian,
Ukrainian, Chinese, British English (GBR: basket, collection, favourite); only the brand
"Wok & Roll" and the "Wok. Roll." of the home title stay. Dish names, categories, cafés and zones
are translated in the frontend by their English name from the API (a new dish shows as entered).
API errors are translated by `code` and field. Money and dates use the conventions of the
language (176 249,00 soʻm, 2 190,00 ₸, ¥31.00, £3.45); `<html lang>` follows, so screen readers switch
voice.

## 7. Quality

Backend: about 500 unit and integration tests (integration tests against PostgreSQL), Spotless
with Google Java Style. Frontend: TypeScript strict, ESLint with jsx-a11y, Prettier. CI runs both on every pull
request. Accessibility: keyboard navigation, visible focus, labels and error messages tied to
fields, WCAG AA colour contrast, charts with keyboard tooltips and a table with every value, a home slideshow
with a pause button that stops on hover and focus and does not move with reduced motion. The café
map is an extra: every café is also in the list next to it, the markers are buttons with the café
name, the map does not take the page scroll (Ctrl + wheel or two fingers), and without WebGL the
list stays with a short note. MapLibre (about 290 KB gzip) is a separate chunk, loaded only when
the map is about to scroll into view.
Responsive layouts at 320 / 768 / 1200 px with 44 px touch targets. The header is sticky, and
keyboard focus never hides under it (WCAG 2.4.11 Focus Not Obscured): Shift+Tab to a link under
the header moves the page down; a mouse click never moves it.
