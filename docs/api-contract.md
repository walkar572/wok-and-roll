# API contract: Wok & Roll MVP (v1.3)

Refs #123, #142 · Owner: Maksudbek (Team Lead) · Version 1.3 · Status: **for agreement** at Sprint 3 planning, Mon 16 Nov (agreed when merged into `dev`)

Stories #50–#69 · General rules: [`architecture.md`](architecture.md) §2 · Error code in the backend: `common.error` (#31) · Plan and dates: [`mvp-plan.md`](mvp-plan.md)

**Scope:** the whole MVP. §3.1–3.5 are built (Stage 2, v1.0). §3.6–3.19 and the v1.1 notes in §3.1 and §3.5 are new and are built in Sprint 3 and Sprint 4 (§11).

This file is the single source of truth for the API. Backend builds it, frontend and QA build against it. Swagger (`/swagger-ui.html`) is generated from the code and must show the same shapes; a difference is a bug.

| Who | Uses this file for |
|---|---|
| Abdulaziz | `countries`, `categories`, `products` (built); `cafes`, `delivery-zones`, `time-slots`, `bookings`, role rules |
| Nodir | `products/{id}` (built); `auth`, `me`, `me/orders`, reports and the PDF, admin catalog |
| Timur | `cart/quote` (built) with discount, delivery fee and total; `orders`; staff API; CSV; sales history seed |
| Serhii | API client, mocks, all screens |
| Khumoyunmirzo, Asif | expected shapes, codes and numbers in tests |
| Maksudbek | request id, contract changes, review |

| Version | Date | What changed |
|---|---|---|
| 1.0 | 10 Nov | Stage 2: countries, categories, products, product details, cart quote (#123) |
| 1.1 | 14 Nov | Stages 3–5 and bonus: totals in the quote, orders, cafés, delivery zones, time slots, bookings, auth and account, staff, admin, reports with PDF and CSV. New rules for time zones, idempotency, paging, request id and roles. Every change to v1.0 is additive (#142). |
| 1.2 | 20 Nov | 406 NOT_ACCEPTABLE error row (#155) |
| 1.3 | 23 Nov | Every endpoint of §2 is built. Additive details found while building: `GET /staff/bookings` returns `{ "items": [...] }` like the other new lists; `POST` and `PATCH /admin/products` take an optional `?country=` (default `UZB`) for the price fields of the response; new `GET /admin/categories` (all categories with `version`). Sales history seed Jun–Nov 2026 (#171). |
| 1.4 | 24 Nov | Real price levels (§5): prices, discount thresholds and delivery fees in each currency at the level of Tashkent, converted at November 2026 rates. The interface language follows the country; the API stays English. |
| 1.5 | 24 Nov | Portion of a dish (additive): `pieces`, `weightGrams`, `volumeMl` in §3.3, §3.4 and the admin API §3.15. The description now holds only the ingredients; the card shows the portion on its own line ("8 pcs · 250 g"). |
| 1.6 | 24 Nov | Sixth country (additive): `GBR`, United Kingdom, `GBP`, VAT 20 %, `Europe/London`, with prices, thresholds and zone fees converted like the others (§5), 2 cafés in London and a sales history. The interface is British English for GBR. |
| 1.7 | 24 Nov | Place of a café on the map (additive): `latitude` and `longitude` in §3.6. The home page shows the cafés of the country on a map next to their list. |
| 1.8 | 24 Nov | Bigger menu (data only, no new fields): 24 dishes in 5 categories. New categories `Desserts` and `Drinks` (the lemonade moves there); 15 new dishes, priced and in stock in all six countries; extras "Soft egg" (ramen) and "Oat milk" (matcha latte). Edamame is hidden (`visible = false`) and replaced by chili garlic peanuts; past orders keep the name they were ordered with. |
| 1.9 | 24 Nov | Seventh country and the city (additive): `USA`, United States, `USD`, tax 7 % (Massachusetts meals tax 6.25 % + Boston 0.75 %), `America/New_York`, 2 cafés in Boston, prices and fees converted like the others (§5). `city` of a café (§3.6) and `cities` of a country (§3.1). The interface lets the guest choose the city (it gives the country) and the language apart; American English is the 7th language. |
| 1.12 | 24 Nov | Web only, no API change: the city switcher offers "All cities" first. The home page then shows the cafés of every country (§3.6, one call per country) on one map, city by city; the menu, the cart, checkout and booking ask for a city before any price, so an order always has one country. |
| 1.11 | 24 Nov | Admin panel (additive): §3.20, six read-only `GET /api/v1/admin/…` endpoints for ADMIN (overview of a period, every order with one order in full, every booking, every account, every café with its day), and a booking history seed for the demo. The web app has an admin panel at `/admin` with its own login (`/admin/login`); café orders and reports moved into it. |
| 1.13 | 24 Nov | Web only, no API change: Panko, the mascot, in the corner of every page of the shop, with a button that tucks him away. |
| 1.15 | 25 Nov | No API change: prices on the web with the sign of the currency (`₴259.00`, not `UAH 259.00`; sum, which has no sign, as a word), and Panko writes them the same way. Gemini has spare models for a model out of its daily quota or overloaded (§3.21), the first is `gemini-3.1-flash-lite`, and Panko's answers are shorter and simpler. Only fast Flash-Lite models by default: a model with no first word after 2 s hands over to the next, and the guest waits 6 s at most. |
| 1.14 | 25 Nov | Panko, the AI chat (additive): §3.21, `GET /api/v1/assistant` and `POST /api/v1/assistant/chat` (public, server-sent events), codes `TOO_MANY_MESSAGES` and `ASSISTANT_UNAVAILABLE`. A click on Panko opens the chat. |
| 1.10 | 24 Nov | Roll sets (additive): category `Sets` with 7 sets, each 15–19 % under its dishes bought one by one. `regularPrice` and `setItems` in §3.3 and §3.4; a set is ordered and priced like any dish (§5 does not change). |

---

## 1. Conventions

| Topic | Rule |
|---|---|
| Base URL | `/api/v1`. The frontend always calls relative paths (`/api/v1/countries`); Vite proxies `/api` to `http://localhost:8080` (`API_PROXY_TARGET`). Never hard-code the port. |
| Format | JSON, UTF-8, `Content-Type: application/json`. Field names in camelCase. |
| Country | 3-letter code: `UZB`, `KAZ`, `GEO`, `UKR`, `CHN`, `GBR` (v1.6), `USA` (v1.9). In GET requests it is the `country` query parameter; in the cart quote it is a body field. |
| Money | JSON number with exactly 2 decimals (`62.00`), always together with `currency` (ISO 4217: `UZS`, `KZT`, `GEL`, `UAH`, `CNY`). Prices are in local currency, **before VAT**. |
| Rates | Percent number: `"vatRate": 15.00` means 15 %. |
| IDs | Positive integers from the database. |
| Lists | Stage 2 lists stay plain JSON arrays without paging (small, fixed data). New lists that can grow (`me/orders`, `staff/*`, `admin/products`, the admin panel lists of §3.20) use paging (§1.1). Other new endpoints return an object, so fields can be added later without breaking clients. |
| Auth | Public: the guest flow (menu, cart quote, cafés, zones, slots, orders, bookings) and `auth/register`, `auth/login`. Everything else needs `Authorization: Bearer <token>` (§3.11, §8). |
| Language | The API is English (codes, names, messages). The web interface has 7 languages: Uzbek, Kazakh, Georgian, Ukrainian, Chinese, British English, American English. Since v1.9 the guest chooses the language and the city apart: the city gives the country (prices, currency, cafés), the language only the texts; the first visit is in the language of the country of the city. Error messages are translated by `code` and field; only the brand name stays as it is (v1.4). |
| Health check | `GET /api/health` → `{"status":"UP"}` (outside `/api/v1`, already on `dev`). |
| Dates and times | A date is `YYYY-MM-DD` (`orderDate`). A clock time is `HH:mm`, 24 h (`slotStart: "12:30"`). Both are **local to the café's country** (next row). A moment is ISO 8601 in UTC with `Z` (`createdAt: "2026-11-18T09:15:00Z"`) and is stored as `timestamptz`. |
| Time zones | Each country has an IANA time zone: UZB `Asia/Tashkent`, KAZ `Asia/Almaty`, GEO `Asia/Tbilisi`, UKR `Europe/Kyiv`, CHN `Asia/Shanghai`, GBR `Europe/London`, USA `America/New_York`. "Today" and "now" for orders, slots and bookings are taken in that zone with one helper, `CountryClock`; never `LocalDate.now()` without a zone. Needs tz data 2024a or newer (JDK 21.0.3+), where all of Kazakhstan is UTC+5. |
| Enums | Strings in UPPER_SNAKE_CASE (`PICKUP`, `DELIVERY`, `CONFIRMED`). A client shows an unknown value as text and does not crash. |
| Unknown fields | The server ignores unknown request fields; clients ignore unknown response fields. This is what makes additive changes safe (§9). |
| Request id | Every response has `X-Request-Id`. A client may send its own (`[A-Za-z0-9-]`, at most 64 characters); otherwise the server creates a UUID. The same id is in the server log and in every error body as `requestId`. QA adds it to bug reports. |
| Idempotency | `POST /orders` and `POST /bookings` accept `Idempotency-Key: <UUID>`. The frontend creates one key per attempt and sends the same key again on a retry (§3.9). |

### 1.1 Paging (new lists only)

- Request: `?pageSize=20&pageToken=…`. `pageSize` is 1–100, default 20. `pageToken` is opaque: copy it from `nextPageToken`, never build it.
- Response: `{ "items": [ … ], "nextPageToken": "b2Zmc2V0OjIw" }`. `nextPageToken` is `null` on the last page.
- No total count (it costs a second query and is out of date at once). The order is fixed per endpoint.
- A wrong `pageSize` or a broken token → `400 VALIDATION_ERROR` with the field `pageSize` or `pageToken`.
- Backend: the token may be base64url of an offset today and a keyset later; clients do not change.

## 2. Endpoints

| # | Method and path | Access | Purpose | Owner | Story | Sprint |
|---|---|---|---|---|---|---|
| 3.1 | `GET /api/v1/countries` | public | Country switcher; v1.1 adds `timeZone` | Abdulaziz | #50 | built · v1.1 in S3 |
| 3.2 | `GET /api/v1/categories?country=UZB` | public | Category filter | Abdulaziz | #51 | built |
| 3.3 | `GET /api/v1/products?country=UZB&categoryId=2` | public | Menu cards | Abdulaziz | #51 | built |
| 3.4 | `GET /api/v1/products/{id}?country=UZB` | public | Product modal with modifiers | Nodir | #52 | built |
| 3.5 | `POST /api/v1/cart/quote` | public | Cart and checkout totals; v1.1 adds discount, delivery fee and total | Timur | #53–#55, #59 | built · v1.1 in S3 |
| 3.6 | `GET /api/v1/cafes?country=UZB` | public | Pickup points and booking venues | Abdulaziz | #58, #61 | S3 |
| 3.7 | `GET /api/v1/delivery-zones?country=UZB` | public | Zones with a fixed fee | Abdulaziz | #59 | S3 |
| 3.8 | `GET /api/v1/time-slots?cafeId=1&date=2026-11-20` | public | Slots with free places | Abdulaziz | #60 | S3 |
| 3.9 | `POST /api/v1/orders` | public, token optional | Place an order | Timur | #56, #57 | S3 |
| 3.10 | `POST /api/v1/bookings` | public, token optional | Book a table | Abdulaziz | #61 | S4 |
| 3.11 | `POST /api/v1/auth/register` · `POST /api/v1/auth/login` | public | Account and token | Nodir | #62 | built in #134 · v1.1 in S3 |
| 3.12 | `GET /api/v1/me` | any logged-in user | Current user | Nodir | #62 | built in #134 |
| 3.13 | `GET /api/v1/me/orders` · `GET /api/v1/me/orders/{orderNumber}` | any logged-in user | Order history | Nodir | #63 | S4 |
| 3.14 | `GET /api/v1/staff/orders` · `PATCH /api/v1/staff/orders/{orderNumber}` · `GET /api/v1/staff/bookings` | STAFF, ADMIN | Café work list | Timur | #65 | S4 |
| 3.15 | `GET`, `POST /api/v1/admin/products` · `PATCH /api/v1/admin/products/{id}` · `PUT /api/v1/admin/products/{id}/prices/{country}` · `POST /api/v1/admin/categories` · `PATCH /api/v1/admin/categories/{id}` | ADMIN | Catalog, prices, stock | Nodir | #64 | S4 |
| 3.16 | `GET /api/v1/reports/sales-by-region?from=&to=` | VIEWER, ADMIN | Sales by region | Nodir | #66 | S3 |
| 3.17 | `GET /api/v1/reports/sales-by-month?country=&year=` | VIEWER, ADMIN | Sales by month | Nodir | #67 | S3 |
| 3.18 | `GET /api/v1/reports/sales.pdf?from=&to=&country=&year=` | VIEWER, ADMIN | PDF of the reports page | Nodir | #68 | S3–S4 |
| 3.19 | `GET /api/v1/reports/sales-by-region.csv?from=&to=` · `GET /api/v1/reports/sales-by-month.csv?country=&year=` | VIEWER, ADMIN | CSV of each report | Timur | #69 | S4 |
| 3.20 | `GET /api/v1/admin/overview` · `/admin/orders` · `/admin/orders/{orderNumber}` · `/admin/bookings` · `/admin/users` · `/admin/cafes` | ADMIN | Admin panel, read only (v1.11) | — | — | S4 |
| 3.21 | `GET /api/v1/assistant` · `POST /api/v1/assistant/chat` | public | Panko, the AI chat (v1.14) | — | — | S4 |

The examples use the real seed data on `dev` (#33). Café, zone, order and report values are examples until their migrations are merged.

---

## 3. Endpoint details

### 3.1 `GET /api/v1/countries`

No parameters. Returns all 7 countries, ordered as in the seed (UZB first; the frontend uses UZB on the first visit).

```json
[
  { "code": "UZB", "name": "Uzbekistan", "currency": "UZS", "vatRate": 15.00 },
  { "code": "KAZ", "name": "Kazakhstan", "currency": "KZT", "vatRate": 20.00 },
  { "code": "GEO", "name": "Georgia",    "currency": "GEL", "vatRate": 12.00 },
  { "code": "UKR", "name": "Ukraine",    "currency": "UAH", "vatRate": 8.00 },
  { "code": "CHN", "name": "China",      "currency": "CNY", "vatRate": 18.00 },
  { "code": "GBR", "name": "United Kingdom", "currency": "GBP", "vatRate": 20.00 },
  { "code": "USA", "name": "United States", "currency": "USD", "vatRate": 7.00 }
]
```

| Field | Type | Notes |
|---|---|---|
| `code` | string | `UZB` … `USA` |
| `name` | string | Shown in the switcher |
| `currency` | string | ISO 4217, used by `Intl.NumberFormat` |
| `vatRate` | number | Percent, from `vat_rate` |

Responses: `200`.

**v1.1 (Sprint 3, #143):** every country also has `timeZone`, the IANA name of its time zone (§1), for example `{ "code": "UZB", "name": "Uzbekistan", "currency": "UZS", "vatRate": 15.00, "timeZone": "Asia/Tashkent" }`. The date picker uses it for "today".

**v1.9:** every country also has `cities`, the cities of its active cafés, A to Z (`["Tashkent"]`; empty while a country has no café). The city switcher of the interface lists them with their country and currency.

### 3.2 `GET /api/v1/categories?country=UZB`

| Parameter | Required | Rule |
|---|---|---|
| `country` | yes | One of the 7 codes |

Returns visible categories that have at least one visible dish with a price in this country, ordered by `sort_order`.

```json
[
  { "id": 1, "name": "Sushi & rolls" },
  { "id": 2, "name": "From the wok" },
  { "id": 3, "name": "Sides" }
]
```

Responses: `200` · `400` (missing or unknown `country`, see §4).

### 3.3 `GET /api/v1/products?country=UZB&categoryId=2`

| Parameter | Required | Rule |
|---|---|---|
| `country` | yes | One of the 7 codes |
| `categoryId` | no | Positive integer. Without it, all categories. An unknown id returns `[]`. |

Returns visible dishes that **have a price in this country**; a dish with no `regional_price` row for the country is not returned (#51). Out-of-stock dishes **are** returned with `inStock: false`, so the UI can show the label and disable Add. Order: category `sort_order`, then dish `id`. The example is shortened to 2 of the 3 wok dishes.

```json
[
  {
    "id": 5,
    "categoryId": 2,
    "name": "Firecracker udon",
    "description": "Udon noodles, chicken, chili, sesame.",
    "imageUrl": "/images/udon.png",
    "pieces": null,
    "weightGrams": 350,
    "volumeMl": null,
    "price": 48.00,
    "currency": "UZS",
    "inStock": true,
    "hasModifiers": true
  },
  {
    "id": 7,
    "categoryId": 2,
    "name": "Teriyaki chicken noodles",
    "description": "Egg noodles, chicken, teriyaki sauce, vegetables.",
    "imageUrl": "/images/teriyaki-noodles.png",
    "pieces": null,
    "weightGrams": 350,
    "volumeMl": null,
    "price": 52.00,
    "currency": "UZS",
    "inStock": true,
    "hasModifiers": false
  }
]
```

| Field | Type | Notes |
|---|---|---|
| `id`, `categoryId` | integer | |
| `name` | string | |
| `description` | string or `null` | The ingredients only (v1.5); the portion has its own fields |
| `imageUrl` | string or `null` | Path served by the frontend (`/images/...`). `null` = show the placeholder. |
| `pieces` | integer or `null` | Pieces in a portion (rolls, nigiri, gyoza, sets); `null` when not counted (v1.5) |
| `weightGrams` | integer or `null` | Weight of a portion in grams (v1.5) |
| `volumeMl` | integer or `null` | Volume of a drink in millilitres (v1.5). The card shows the three as "8 pcs · 250 g" in the language of the interface |
| `price` | number | Base price in local currency, before VAT |
| `currency` | string | Same as the country's currency |
| `inStock` | boolean | Per country (`regional_price.in_stock`) |
| `hasModifiers` | boolean | `true` if the dish has at least one modifier priced in this country. The card opens the modal for these dishes. |
| `regularPrice` | number or `null` | v1.10, sets only: what the dishes of the set cost one by one in this country (sum of quantity × price, before VAT). `null` for a dish, and for a set when one of its dishes has no price here (no saving is shown from a part of the set). |
| `setItems` | array | v1.10: the dishes of a set in their order, `{ "productId": 11, "name": "Philadelphia roll", "quantity": 2 }`; empty for a dish. A dish of a set may be hidden or out of stock on its own; the set has its own price and stock. |

Responses: `200` · `400` (missing or unknown `country`, `categoryId` not a number).

**v1.10: sets.** A set is a product (category `Sets`) made of other products (`product_set_item`). Its `price` is its own regional price; `regularPrice` is the sum of its dishes, so the card can show the saving (Philadelphia set in UZB: `199000.00` against `239000.00`, −17 %). The cart, the quote and the order treat a set like any dish: one line, its own price.

### 3.4 `GET /api/v1/products/{id}?country=UZB`

The product modal (#52, #114). All fields of §3.3 (also `regularPrice` and `setItems`, v1.10), plus `modifiers`.

```json
{
  "id": 5,
  "categoryId": 2,
  "name": "Firecracker udon",
  "description": "Udon noodles, chicken, chili, sesame.",
  "imageUrl": "/images/udon.png",
  "pieces": null,
  "weightGrams": 350,
  "volumeMl": null,
  "price": 48.00,
  "currency": "UZS",
  "inStock": true,
  "hasModifiers": true,
  "modifiers": [
    { "id": 1, "type": "SIZE",  "name": "Regular",       "priceDelta": 0.00 },
    { "id": 2, "type": "SIZE",  "name": "Large",         "priceDelta": 12.00 },
    { "id": 3, "type": "EXTRA", "name": "Extra chicken", "priceDelta": 9.00 },
    { "id": 4, "type": "EXTRA", "name": "Fried egg",     "priceDelta": 5.00 }
  ]
}
```

Rules:

- `modifiers` contains only visible modifiers **with a price in this country**; it is `[]` when there are none. Order: `SIZE` first, then `EXTRA`, then `id`.
- `priceDelta` is in local currency, before VAT, `0.00` or more.
- **SIZE:** if the dish has SIZE modifiers, the guest chooses **exactly one**. The UI pre-selects the first SIZE.
- **EXTRA:** the guest chooses **any number**, each at most once.
- **Price on the Add button** (display only, the only price the frontend calculates): `(price + sum of chosen priceDelta) × quantity`. Calculate in minor units (cents) to avoid floating-point errors, and format with 2 decimals.

Responses: `200` · `400` (`country` missing or unknown, `id` not a number) · `404 NOT_FOUND` when the dish does not exist, is hidden, or has no price in this country.

### 3.5 `POST /api/v1/cart/quote`

Prices the cart on the server. The cart page shows **only** these numbers (#53, #54). The call has no side effects (nothing is saved), so the frontend may repeat it after every cart or country change. It is `POST` because the cart is a list in the body.

**Request**

```json
{
  "country": "UZB",
  "items": [
    { "productId": 5, "quantity": 2, "modifierIds": [2, 3] },
    { "productId": 8, "quantity": 1, "modifierIds": [] }
  ]
}
```

| Field | Required | Rule |
|---|---|---|
| `country` | yes | One of the 7 codes |
| `items` | yes | 1–50 lines |
| `items[i].productId` | yes | Exists, visible, has a price in this country, `inStock` is `true` |
| `items[i].quantity` | yes | JSON number, whole, **1–99**. `0` or `1.5` is rejected with `VALIDATION_ERROR` (see §4) |
| `items[i].modifierIds` | no | Default `[]`. No duplicates; each belongs to this dish and has a price in this country; exactly one SIZE if the dish has sizes |

The same dish with different modifiers is a separate line (#53). The API does not merge lines.

**Response `200`**

```json
{
  "country": "UZB",
  "currency": "UZS",
  "lines": [
    {
      "productId": 5,
      "name": "Firecracker udon",
      "modifiers": [
        { "id": 2, "type": "SIZE",  "name": "Large",         "priceDelta": 12.00 },
        { "id": 3, "type": "EXTRA", "name": "Extra chicken", "priceDelta": 9.00 }
      ],
      "quantity": 2,
      "unitPrice": 69.00,
      "lineTotal": 138.00
    },
    {
      "productId": 8,
      "name": "Gyoza",
      "modifiers": [],
      "quantity": 1,
      "unitPrice": 32.00,
      "lineTotal": 32.00
    }
  ],
  "subtotal": 170.00,
  "vatRate": 15.00,
  "vat": 25.50,
  "amountInclVat": 195.50
}
```

- `lines[i]` belongs to `items[i]` (same order and count).
- `unitPrice` = price + chosen `priceDelta`s. `lineTotal` = `unitPrice × quantity`.
- **v1.1 (Sprint 3, #145):** the request gets `scenario` and `deliveryZoneId`, and the response gets `scenario`, `discountRate`, `discount`, `deliveryFee` and `total`. The Stage 2 fields do not change. See below.

Responses: `200` · `400` (see §4). A quote never returns partial totals: if one line is invalid, the whole request is rejected and the error names the line.

**v1.1 (Sprint 3, #55, #59): totals**

New request fields:

| Field | Required | Rule |
|---|---|---|
| `scenario` | no | `PICKUP` (default) or `DELIVERY` |
| `deliveryZoneId` | for `DELIVERY` | A zone of this country (§3.7). Must be empty for `PICKUP`. A zone of another country or an unknown id → `UNKNOWN_DELIVERY_ZONE` |

The response for the request above with `"scenario": "DELIVERY", "deliveryZoneId": 1` (fee 20.00):

```json
{
  "country": "UZB",
  "currency": "UZS",
  "scenario": "DELIVERY",
  "lines": [ "… the same 2 lines as above …" ],
  "subtotal": 170.00,
  "vatRate": 15.00,
  "vat": 25.50,
  "amountInclVat": 195.50,
  "discountRate": 3.00,
  "discount": 5.87,
  "deliveryFee": 20.00,
  "total": 209.63
}
```

- `discountRate` comes from the thresholds of the country (§5). For `PICKUP`, `deliveryFee` is `0.00`.
- The cart page sends `PICKUP` (or nothing), so it shows the totals without a fee. Checkout sends the chosen scenario and zone and asks again when they change.
- The quote still saves nothing. The order (§3.9) calculates again and never trusts the quote.

### 3.6 `GET /api/v1/cafes?country=UZB`

Cafés of the chain. Every café takes pickup orders and table bookings, and is the kitchen for its delivery zones (§3.7). The UI calls it "pickup point" in checkout and "café" in booking.

| Parameter | Required | Rule |
|---|---|---|
| `country` | yes | One of the 7 codes |

```json
{
  "items": [
    { "id": 1, "country": "UZB", "name": "Wok & Roll Chilanzar", "city": "Tashkent", "address": "12 Bunyodkor Ave, Tashkent",
      "phone": "+998712000001", "opensAt": "10:00", "closesAt": "22:00", "timeZone": "Asia/Tashkent",
      "latitude": 41.272187, "longitude": 69.203167 }
  ]
}
```

Active cafés of the country, ordered by name. Seed: 2 cafés per country. Responses: `200` · `400` (missing or unknown `country`).

- v1.7: `latitude` and `longitude` are WGS 84 degrees with 6 decimals. Both are numbers or both are `null` (the database refuses one without the other): a café without a place is still offered for pickup and booking, only not on the map. The seed places are the street addresses, found with OpenStreetMap Nominatim.
- v1.9: `city` is the city of the café in English, as in its address (never empty). The home page shows the cafés of the city the guest chose.

### 3.7 `GET /api/v1/delivery-zones?country=UZB`

```json
{
  "items": [
    { "id": 1, "country": "UZB", "name": "Tashkent: Center", "cafeId": 1, "fee": 20.00, "currency": "UZS", "deliveryMinutes": 45 },
    { "id": 2, "country": "UZB", "name": "Tashkent: Yunusabad", "cafeId": 2, "fee": 15.00, "currency": "UZS", "deliveryMinutes": 40 }
  ]
}
```

- `fee` is fixed per zone, in local currency, `0.00` or more. It is added last, outside VAT and discount, and never changes the threshold (Decision Log, 5 Nov).
- `cafeId` is the café that cooks and delivers for this zone. Delivery orders use the time slots of that café.
- Every country has three zones: near, center and far (v1.4: real fees in each currency, see §5). The JSON examples in this file keep the small demo numbers of v1.0 to show the shape; the real numbers are in §5.
- Active zones, ordered by name. Responses: `200` · `400`.

### 3.8 `GET /api/v1/time-slots?cafeId=1&date=2026-11-20`

| Parameter | Required | Rule |
|---|---|---|
| `cafeId` | yes | An active café (`UNKNOWN_CAFE`) |
| `date` | yes | `YYYY-MM-DD`, from today to 7 days ahead in the café's time zone (`VALIDATION_ERROR`) |

```json
{
  "cafeId": 1,
  "date": "2026-11-20",
  "timeZone": "Asia/Tashkent",
  "slots": [
    { "start": "10:00", "end": "10:30", "capacity": 5, "remaining": 5, "available": true,  "reason": null },
    { "start": "10:30", "end": "11:00", "capacity": 5, "remaining": 0, "available": false, "reason": "FULL" },
    { "start": "21:30", "end": "22:00", "capacity": 5, "remaining": 4, "available": true,  "reason": null }
  ]
}
```

- Slots are 30 minutes, from `opensAt` to the last start 30 minutes before `closesAt` (10:00 … 21:30). Nothing is stored per slot; the list is calculated from the opening hours.
- `capacity` is per café (`cafe.slot_capacity`, seed 5 orders) and is shared by pickup and delivery orders of that café.
- `available` is `false` when `remaining` is 0 (`reason: "FULL"`) or the slot starts in less than 30 minutes in the café's time zone (`reason: "PAST"`).
- The list is information only. The real check is in `POST /orders`: two guests can see the same last place, only one gets it, the other gets `409 SLOT_FULL`.

Reservation (backend, one statement inside the order transaction):

```sql
INSERT INTO slot_reservation (cafe_id, slot_date, slot_start, reserved)
VALUES (:cafeId, :slotDate, :slotStart, 1)
ON CONFLICT (cafe_id, slot_date, slot_start)
DO UPDATE SET reserved = slot_reservation.reserved + 1
WHERE slot_reservation.reserved < :capacity
RETURNING reserved;
```

No row returned → the slot is full → `409 SLOT_FULL`. If the order fails later, the transaction rolls back and the place is free again. A cancelled order (§3.14) runs `reserved = reserved - 1` (never below 0).

Responses: `200` · `400`.

### 3.9 `POST /api/v1/orders`

Places an order. Headers: `Idempotency-Key` (optional, UUID); `Authorization` (optional: with a valid token the order is linked to the user and appears in `me/orders`; an invalid or expired token → `401`, the API never silently treats it as a guest).

**Request**

```json
{
  "country": "UZB",
  "orderDate": "2026-11-20",
  "scenario": "DELIVERY",
  "cafeId": null,
  "deliveryZoneId": 1,
  "deliveryAddress": "Amir Temur 15, apt 4",
  "slotStart": "12:30",
  "contactName": "Aziz Karimov",
  "contactPhone": "+998901234567",
  "contactEmail": "aziz@mail.uz",
  "comment": "Call at the door",
  "expectedTotal": 209.63,
  "items": [
    { "productId": 5, "quantity": 2, "modifierIds": [2, 3] },
    { "productId": 8, "quantity": 1, "modifierIds": [] }
  ]
}
```

| Field | Required | Rule |
|---|---|---|
| `country` | yes | One of the 7 codes |
| `orderDate` | yes | `YYYY-MM-DD`, from today to 7 days ahead in the country's time zone. Before today → `PAST_DATE`; later than 7 days → `VALIDATION_ERROR` |
| `scenario` | yes | `PICKUP` or `DELIVERY` |
| `cafeId` | `PICKUP` | A café of this country (`UNKNOWN_CAFE`). Must be empty for `DELIVERY` (the zone's café is used) |
| `deliveryZoneId` | `DELIVERY` | A zone of this country (`UNKNOWN_DELIVERY_ZONE`). Must be empty for `PICKUP` |
| `deliveryAddress` | `DELIVERY` | 5–200 characters, trimmed. Must be empty for `PICKUP` |
| `slotStart` | yes | `HH:mm`, a slot of the café on `orderDate` (pickup café, or the zone's café). Not a slot of that day, or starts in less than 30 minutes → `INVALID_SLOT`; full → `409 SLOT_FULL` |
| `contactName` | yes | 2–100 characters, trimmed |
| `contactPhone` | yes | International format: `+` and 8–15 digits (`+998901234567`). The UI removes spaces before sending |
| `contactEmail` | no | A valid email, at most 254 characters, saved in lower case |
| `comment` | no | At most 300 characters |
| `expectedTotal` | no (the frontend always sends it) | The `total` of the last quote the guest saw. If the server total is different → `409 PRICE_CHANGED` and nothing is saved |
| `items` | yes | The same rules and error codes as the quote (§3.5, §4) |

All field problems are returned together in one `400` (as in the quote); `code` is the code of the first problem.

**Processing** (one database transaction): validate → price with the same pricing service as the quote (never trust the client) → compare `expectedTotal` → reserve the slot place (§3.8) → insert the order, its lines and modifiers, with names and prices copied → commit.

**Response `201`**

```json
{
  "orderNumber": "WR-7K3M9Q2X",
  "status": "NEW",
  "createdAt": "2026-11-18T09:15:00Z",
  "country": "UZB",
  "currency": "UZS",
  "orderDate": "2026-11-20",
  "slotStart": "12:30",
  "slotEnd": "13:00",
  "scenario": "DELIVERY",
  "cafe": { "id": 1, "name": "Wok & Roll Chilanzar", "address": "12 Bunyodkor Ave, Tashkent" },
  "deliveryZone": { "id": 1, "name": "Tashkent: Center" },
  "deliveryAddress": "Amir Temur 15, apt 4",
  "contactName": "Aziz Karimov",
  "contactPhone": "+998901234567",
  "lines": [ "… the same shape as the quote lines …" ],
  "subtotal": 170.00,
  "vatRate": 15.00,
  "vat": 25.50,
  "amountInclVat": 195.50,
  "discountRate": 3.00,
  "discount": 5.87,
  "deliveryFee": 20.00,
  "total": 209.63
}
```

- For `PICKUP`, `deliveryZone` and `deliveryAddress` are `null` and `deliveryFee` is `0.00`. `cafe` is always the café that prepares the order.
- For a logged-in user the response has `Location: /api/v1/me/orders/WR-7K3M9Q2X`. Guest orders have no read endpoint, so the success page uses this response.
- **Order number:** `WR-` and 8 characters of Crockford Base32 (`0–9`, `A–Z` without `I`, `L`, `O`, `U`) from `SecureRandom`, unique index, a new number on a conflict. It is not a sequence, so it cannot be guessed and does not show how many orders we have.
- **Statuses:** a new order is `NEW`. Staff move it `NEW → CONFIRMED → DONE`, or `NEW` / `CONFIRMED` → `CANCELLED` (§3.14).

**Idempotency** (IETF `Idempotency-Key` draft):

- The same key and the same body again → `201` with the same body and the header `Idempotent-Replayed: true`. No second order and no second slot place.
- The same key with a different body → `422 IDEMPOTENCY_KEY_REUSED`.
- A key that is not a UUID → `400 VALIDATION_ERROR` (field `Idempotency-Key`).
- Without the header the request still works, but a retry may create a second order. The UI also disables Place order while it waits.
- Backend: `customer_order.idempotency_key` (unique) and `request_hash` (SHA-256 of the request body). When two requests with one key arrive together, the unique index lets one insert; the other reads the saved order and replays it.

Responses: `201` · `400` (§4) · `401` (bad token) · `409 SLOT_FULL`, `409 PRICE_CHANGED` · `422 IDEMPOTENCY_KEY_REUSED`.

### 3.10 `POST /api/v1/bookings`

Books a table. `Idempotency-Key` and `Authorization` work as in §3.9 (with a token, the booking is linked to the user).

```json
{
  "cafeId": 1,
  "date": "2026-11-21",
  "time": "19:30",
  "guests": 4,
  "contactName": "Aziz Karimov",
  "contactPhone": "+998901234567",
  "contactEmail": null,
  "comment": "Window seat if possible"
}
```

| Field | Required | Rule |
|---|---|---|
| `cafeId` | yes | An active café (`UNKNOWN_CAFE`) |
| `date` | yes | From today to 30 days ahead in the café's time zone (`PAST_DATE`, `VALIDATION_ERROR`) |
| `time` | yes | `HH:mm` on a 30-minute step, from `opensAt` to 90 minutes before `closesAt`; today it must be at least 60 minutes from now (`PAST_DATE`) |
| `guests` | yes | Whole number 1–8 ("For more than 8 guests, please call the café") |
| `contactName`, `contactPhone`, `contactEmail`, `comment` | as in §3.9 | |

- A booking lasts **90 minutes**. Each café has tables of 2, 4, 6 and 8 seats; the server takes the **smallest free table** with enough seats.
- No free table → `409 NO_TABLE_AVAILABLE`, message "No table for 4 guests at 19:30. Please choose another time.", nothing saved.
- **No double booking**, even with two parallel requests: the database refuses overlapping bookings of one table.

```sql
CREATE EXTENSION IF NOT EXISTS btree_gist;  -- trusted extension, PostgreSQL 13+
ALTER TABLE booking ADD CONSTRAINT ex_booking_table_time
  EXCLUDE USING gist (table_id WITH =, during WITH &&) WHERE (status = 'CONFIRMED');
```

`during` is `tstzrange(start, start + 90 minutes, '[)')`, built in the café's time zone. If another guest takes the table first, PostgreSQL raises SQLSTATE `23P01`; the service then tries the next table. In PostgreSQL a failed statement aborts the transaction, so each try runs in its own savepoint (or its own transaction).

**Response `201`**

```json
{
  "bookingNumber": "BK-3F8K2M9P",
  "status": "CONFIRMED",
  "createdAt": "2026-11-18T10:02:11Z",
  "cafe": { "id": 1, "name": "Wok & Roll Chilanzar", "address": "12 Bunyodkor Ave, Tashkent" },
  "date": "2026-11-21",
  "time": "19:30",
  "endTime": "21:00",
  "guests": 4,
  "tableSeats": 4,
  "contactName": "Aziz Karimov"
}
```

Responses: `201` · `400` · `401` · `409 NO_TABLE_AVAILABLE` · `422 IDEMPOTENCY_KEY_REUSED`.

### 3.11 `POST /api/v1/auth/register` and `POST /api/v1/auth/login`

Built in #134 (design: `wok-and-roll-back/docs/auth.md`); v1.1 changes are marked.

Register:

```json
{ "email": "aziz@mail.uz", "password": "secret123", "fullName": "Aziz Karimov", "phone": "+998901234567" }
```

| Field | Required | Rule |
|---|---|---|
| `email` | yes | A valid email, at most 255 characters, saved in lower case. Taken → `409 EMAIL_TAKEN` (field `email`) |
| `password` | yes | 8–72 characters (BCrypt reads at most 72 bytes) |
| `fullName` | yes | At most 150 characters |
| `phone` | no | At most 30 characters, international format recommended |

Response **`201`**: `{ "token": "eyJhbGciOiJIUzI1NiJ9…", "role": "CUSTOMER" }`. Register always gives `CUSTOMER`; there is no public way to get another role.

Login: `{ "email": "aziz@mail.uz", "password": "secret123" }` → `200` with the same body.

- A wrong email and a wrong password both give `401 INVALID_CREDENTIALS` with the same message, so nobody can test which emails exist.
- **v1.1:** 5 failed logins for one email from one IP in 15 minutes → `429 TOO_MANY_ATTEMPTS` with `Retry-After: <seconds>`. A successful login resets the counter. The counter is in memory, which is enough for one backend instance.
- **Token:** JWT, HS256 only, valid 24 hours (`JWT_TTL_HOURS`). Claims `sub` (user id), `role`, `email`, `iat`, `exp`; **v1.1** adds `iss: "wok-and-roll"` and `aud: "wok-and-roll-web"`, checked on every request. No refresh token: after 24 hours the user logs in again.
- **Logout** happens in the client: it deletes the token. The server keeps no sessions, so a stolen token works until `exp` (at most 24 hours). We accept this for the MVP (§8).

### 3.12 `GET /api/v1/me`

`200 { "id": 15, "email": "aziz@mail.uz", "fullName": "Aziz Karimov", "phone": "+998901234567", "role": "CUSTOMER" }` · `401` without a valid token. Never contains the password hash.

### 3.13 `GET /api/v1/me/orders` and `GET /api/v1/me/orders/{orderNumber}`

The orders of the logged-in user. The user id comes **only from the token**, never from a parameter. Paging as in §1.1, newest first (`createdAt` desc, then id).

```json
{
  "items": [
    { "orderNumber": "WR-7K3M9Q2X", "createdAt": "2026-11-18T09:15:00Z", "orderDate": "2026-11-20", "slotStart": "12:30",
      "country": "UZB", "currency": "UZS", "scenario": "DELIVERY", "status": "NEW", "itemCount": 3, "total": 209.63 }
  ],
  "nextPageToken": null
}
```

`GET /api/v1/me/orders/{orderNumber}` returns the same body as the `201` of `POST /orders`. An order of another user, or an unknown number, → **`404 NOT_FOUND`**, not `403`: the API does not say that the order exists (OWASP API1). Orders placed as a guest are not added to an account later.

Responses: `200` · `400` (paging) · `401` · `404`.

### 3.14 Staff API (STAFF, ADMIN)

| Endpoint | What it does |
|---|---|
| `GET /api/v1/staff/orders?cafeId=1&date=2026-11-20` | Orders of a café for a date, ordered by `slotStart`, then order number. Paging (§1.1). Items: `orderNumber`, `slotStart`, `slotEnd`, `scenario`, `status`, `contactName`, `contactPhone`, `deliveryAddress`, `itemCount`, `total`, `currency` |
| `PATCH /api/v1/staff/orders/{orderNumber}` with `{ "status": "CONFIRMED" }` | Changes the status and returns the order (as in §3.9). Allowed: `NEW → CONFIRMED`, `CONFIRMED → DONE`, `NEW` or `CONFIRMED → CANCELLED`. Anything else → `409 INVALID_STATUS_CHANGE`. `CANCELLED` frees the slot place in the same transaction |
| `GET /api/v1/staff/bookings?cafeId=1&date=2026-11-21` | Bookings of a café for a date, ordered by time. Items: `bookingNumber`, `time`, `endTime`, `guests`, `tableSeats`, `status`, `contactName`, `contactPhone` |

Staff see all cafés (the MVP has no link between a user and a café). Responses: `200` · `400` · `401` · `403` · `404` · `409`.

### 3.15 Admin catalog API (ADMIN)

| Endpoint | Body | Result |
|---|---|---|
| `GET /api/v1/admin/products?country=UZB` | — | Paging (§1.1). All products, hidden ones too: `id`, `categoryId`, `categoryName`, `name`, `description`, `imageUrl`, `pieces`, `weightGrams`, `volumeMl`, `visible`, `version`, and for this country `price`, `currency`, `inStock`, `priceVersion` (`null` when the dish is not sold there) |
| `POST /api/v1/admin/products` | `{ categoryId, name, description, imageUrl, pieces, weightGrams, volumeMl, visible }` | `201` with the product and `Location` |
| `PATCH /api/v1/admin/products/{id}` | Any of `name`, `description`, `imageUrl`, `pieces`, `weightGrams`, `volumeMl`, `categoryId`, `visible`, plus `version` (required) | `200` with the product. Only the sent fields change; `0` removes a portion field |
| `PUT /api/v1/admin/products/{id}/prices/{country}` | `{ price, inStock, version }` | `200` with `{ productId, country, price, currency, inStock, version }`. Creates the regional price when it is missing (`version` is then ignored) |
| `POST /api/v1/admin/categories` | `{ name, sortOrder, visible }` | `201` |
| `PATCH /api/v1/admin/categories/{id}` | Any of `name`, `sortOrder`, `visible`, plus `version` | `200` |

- **Optimistic locking:** `UPDATE … SET …, version = version + 1 WHERE id = :id AND version = :version`. No row updated → `409 VERSION_CONFLICT` ("Changed by someone else, reload"). Two admins can never overwrite each other silently.
- `price` is `0.00`–`9999999999.99` with at most 2 decimals; more decimals → `VALIDATION_ERROR` (no silent rounding). `imageUrl` must start with `/images/`. `pieces` is 1–999, `weightGrams` and `volumeMl` are 1–9999 (on PATCH `0` removes the value).
- There is no DELETE: hiding (`visible: false`) keeps old orders and reports correct (erd.md, "Why", point 6).
- The public menu shows changes at once (no cache).

Responses: `200` · `201` · `400` · `401` · `403` · `404` · `409`.

### 3.16 `GET /api/v1/reports/sales-by-region?from=2026-11-01&to=2026-11-30`

| Parameter | Required | Rule |
|---|---|---|
| `from`, `to` | yes | `YYYY-MM-DD`, `from` ≤ `to`, at most 366 days, both days included |

```json
{
  "from": "2026-11-01",
  "to": "2026-11-30",
  "generatedAt": "2026-11-20T09:15:00Z",
  "totalOrders": 412,
  "regions": [
    { "country": "UZB", "countryName": "Uzbekistan", "currency": "UZS", "orders": 120,
      "subtotal": 52340.00, "vat": 7851.00, "discount": 2410.35, "deliveryFee": 1460.00,
      "total": 59240.65, "averageCheck": 493.67 }
  ]
}
```

- **Which orders:** `order_date` in the period and `status` not `CANCELLED` (`NEW`, `CONFIRMED` and `DONE` are sales; a new order shows at once).
- `regions` always has **all 7 countries** in the order of `GET /countries`, with zeros when a country has no orders.
- Sums of the **saved** order values; old orders are never priced again (erd.md, "Why", point 3).
- Each country is in its own currency, no conversion (mentor, 2 Nov). There is **no sum across countries** except `totalOrders`.
- `averageCheck = total / orders`, HALF_UP to 2 decimals; `0.00` when there are no orders.
- The chart shows `orders` per country (Decision Log, 5 Nov); the table shows the money.

Reference query for QA (the numbers must be equal):

```sql
SELECT c.code, count(o.id) AS orders,
       coalesce(sum(o.subtotal), 0) AS subtotal, coalesce(sum(o.vat_amount), 0) AS vat,
       coalesce(sum(o.discount_amount), 0) AS discount, coalesce(sum(o.delivery_fee), 0) AS delivery_fee,
       coalesce(sum(o.total), 0) AS total
FROM country c
LEFT JOIN customer_order o
       ON o.country_id = c.id AND o.order_date BETWEEN :from AND :to AND o.status <> 'CANCELLED'
GROUP BY c.id, c.code
ORDER BY c.id;
```

Responses: `200` · `400` · `401` · `403`.

### 3.17 `GET /api/v1/reports/sales-by-month?country=UZB&year=2026`

| Parameter | Required | Rule |
|---|---|---|
| `country` | yes | One of the 7 codes |
| `year` | yes | From 2024 to next year |

```json
{
  "country": "UZB",
  "countryName": "Uzbekistan",
  "currency": "UZS",
  "year": 2026,
  "generatedAt": "2026-11-20T09:15:00Z",
  "months": [
    { "month": 1, "orders": 0, "subtotal": 0.00, "vat": 0.00, "discount": 0.00, "deliveryFee": 0.00, "total": 0.00, "averageCheck": 0.00 }
  ],
  "totals": { "orders": 640, "subtotal": 281120.00, "vat": 42168.00, "discount": 13210.40, "deliveryFee": 7840.00, "total": 317917.60, "averageCheck": 496.75 }
}
```

`months` always has 12 entries (1–12), zeros for months without orders. The month comes from `order_date`; the same status rule as §3.16. The chart shows `total` per month in the country's currency. Responses: `200` · `400` · `401` · `403`.

### 3.18 `GET /api/v1/reports/sales.pdf?from=2026-11-01&to=2026-11-30&country=UZB&year=2026`

The PDF of the reports page: both reports with the current filters. All four parameters are required, with the rules of §3.16 and §3.17.

- `Content-Type: application/pdf`, `Content-Disposition: attachment; filename="wok-and-roll-sales_2026-11-01_2026-11-30_UZB-2026.pdf"`.
- A4 portrait. **Header:** "Wok & Roll · Sales report", the filters, "Generated 2026-11-20 09:15 UTC".
- **Section 1, sales by region:** bar chart of orders per country; table per country with orders, subtotal, VAT, discount, delivery fee, total and average check in its own currency.
- **Section 2, sales by month:** year KPIs (orders, subtotal, VAT, discount, delivery fee, total, average check); bar chart of total per month; table of 12 months.
- Footer: page x of y.
- The numbers come from the same service methods as §3.16 and §3.17, so **PDF = web = CSV**. Money is printed from `BigDecimal` with exactly 2 decimals.

Responses: `200` · `400` · `401` · `403`.

### 3.19 CSV: `GET /api/v1/reports/sales-by-region.csv?from=&to=` and `GET /api/v1/reports/sales-by-month.csv?country=&year=`

`Content-Type: text/csv; charset=UTF-8`, `Content-Disposition: attachment; filename="wok-and-roll-sales-by-region_2026-11-01_2026-11-30.csv"` (or `wok-and-roll-sales-by-month_UZB_2026.csv`).

```
country,countryName,currency,orders,subtotal,vat,discount,deliveryFee,total,averageCheck
UZB,Uzbekistan,UZS,120,52340.00,7851.00,2410.35,1460.00,59240.65,493.67
```

```
month,orders,subtotal,vat,discount,deliveryFee,total,averageCheck
2026-01,0,0.00,0.00,0.00,0.00,0.00,0.00
```

- RFC 4180: a header row, comma separator, CRLF line ends, UTF-8 (the content is ASCII).
- Numbers: `.` as the decimal point, money with exactly 2 decimals, no thousands separator, no currency sign (currency has its own column).
- A text cell with a comma, a quote or a line break is quoted, and inner quotes are doubled.
- **CSV injection** (OWASP): a text cell that starts with `=`, `+`, `-`, `@`, a tab or CR gets a leading `'`. Numbers are written as numbers.
- The same rows and numbers as the JSON (same service). Same parameters and errors as §3.16 and §3.17.

**Downloads in the frontend** (§3.18, §3.19): call with `fetch` and the `Authorization` header, read the Blob, then open it (`URL.createObjectURL`) for Generate PDF, or save it with `<a download>` and the file name from `Content-Disposition`. Never put the token in a URL.

### 3.20 Admin panel API (ADMIN, read only)

Everything the shop knows, for the admin panel of the web app (`/admin`, v1.11). Read only: the order status changes through the staff API (§3.14), the menu through the admin catalog API (§3.15). The lists use paging (§1.1); every filter is optional, and an empty search is no search.

| Endpoint | Filters | Items (or body) |
|---|---|---|
| `GET /api/v1/admin/overview?from=&to=` | both required, the period rule of §3.16 (at most 366 days, `from` not after `to`) | `orders` { `total` (cancelled too), `byStatus` (`NEW`, `CONFIRMED`, `DONE`, `CANCELLED`, in this order, zeros too), `pickup`, `delivery` } · `bookings` { `total`, `cancelled`, `guests` of the confirmed ones } · `accounts` { `total`, `newInPeriod` (UTC dates), `byRole` } · `regions` (every country: `country`, `currency`, `orders`, `revenue`, `averageCheck`: the numbers of §3.16) · `days` (every day of the period: `date`, `orders` without cancelled) · `topDishes` (5: `productId`, `name`, `quantity`, without cancelled) |
| `GET /api/v1/admin/orders` | `country`, `cafeId`, `status`, `from`, `to` (order date), `q` | Newest day first (`orderDate` desc, then `slotStart` desc, then id): `orderNumber`, `createdAt`, `orderDate`, `slotStart`, `slotEnd`, `country`, `currency`, `cafe` { `id`, `name` }, `scenario`, `status`, `contactName`, `contactPhone`, `account` (`null` for a guest, else { `email`, `fullName` }), `itemCount`, `total` |
| `GET /api/v1/admin/orders/{orderNumber}` | — | { `order`: the body of §3.9, `contactEmail`, `comment`, `account`, `updatedAt` }. Unknown number → `404 NOT_FOUND` |
| `GET /api/v1/admin/bookings` | `country`, `cafeId`, `status` (`CONFIRMED`, `CANCELLED`), `from`, `to` (booking date), `q` | Newest day first: `bookingNumber`, `createdAt`, `date`, `time`, `endTime`, `country`, `cafe`, `guests`, `tableSeats`, `status`, `contactName`, `contactPhone`, `contactEmail`, `comment`, `account` |
| `GET /api/v1/admin/users` | `role`, `q` | Newest account first: `id`, `email`, `fullName`, `phone`, `role`, `createdAt`, `orders`, `lastOrderAt`. Never the password hash |
| `GET /api/v1/admin/cafes` | — | `{ "items": [...] }`, every café, closed ones too, in the order of the countries: `id`, `country`, `city`, `name`, `address`, `phone`, `opensAt`, `closesAt`, `timeZone`, `active`, `tables`, `seats`, `slotCapacity`, `today` (in the café's time zone), `todayOrders` (without cancelled), `todayBookings` (confirmed) |

- `q` is at most 100 characters and is looked for, without case, in the number, the contact name, phone and email, and the account email (users: email, name, phone). It is plain text: `%` and `_` are not wildcards.
- A wrong `country`, `status` or `role`, `from` after `to`, or a too long `q` → `400 VALIDATION_ERROR` with the field; a date that is not a date → `400 BAD_REQUEST`.
- The overview money comes from the sales-by-region report (§3.16), so the panel and the reports page always show the same revenue.
- Demo data: the booking history of 26 Oct – 23 Nov 2026 (`V2026_11_24_1600`), 1–4 bookings a café a day, deterministic like the sales history; all dates are in the past, so the booking tests are not affected.

Responses: `200` · `400` · `401` · `403` · `404`.

### 3.21 Panko, the AI chat (public, v1.14)

Panko, the mascot of the shop, answers guests' questions about the menu, the cafés, delivery, discounts and booking. The answers come from a language model, Claude (Anthropic API) or Gemini (Google Gemini API), whichever has a key on the server, and are based only on the data of the chosen country, which the server puts into the prompt: every dish on the menu with its id, price before VAT, portion, ingredients, options and stock, the sets with their dishes, the cafés with hours, the delivery zones with fees and times, VAT and the discount thresholds. Panko can recommend at most 4 dishes; they come as menu cards, which open the dish dialog of the menu. He cannot order, pay or book.

| Endpoint | Body | Answer |
|---|---|---|
| `GET /api/v1/assistant` | — | `{ "enabled": true, "model": "gemini-3.1-flash-lite" }`. `enabled` is `false` (and `model` `null`) when the server has no `ANTHROPIC_API_KEY` or `GEMINI_API_KEY`; the web app then shows Panko without the AI: links to the menu and the booking. |
| `POST /api/v1/assistant/chat` | `{ "country": "UZB", "city": "Tashkent", "language": "en-GB", "messages": [{ "role": "user", "text": "Something spicy for two?" }] }` | `200 text/event-stream` (below) |

**Body.** `country`: one of the 7 codes. `city`: optional, at most 80 characters; it goes into the prompt only when it is a city of the country (§3.1), otherwise it is left out. `language`: `uz`, `kk`, `ka`, `uk`, `zh`, `en-GB`, `en-US`, the language of the answer. `messages`: 1–20, the whole conversation every time (the server keeps nothing), oldest first; `role` is `user` or `assistant`, `text` 1–2000 characters, and a question of the guest (`user`) at most 500. The last message must be the guest's. A greeting of the assistant before the first question is dropped, and messages of the same side in a row are joined.

**Answer: server-sent events** (`Content-Type: text/event-stream`, each `data` is JSON):

```text
event:delta
data:{"text":"Try the Firecracker udon"}

event:delta
data:{"text":", hot and quick to love."}

event:done
data:{"dishes":[{ ...a product of §3.3... }],"refused":false}
```

- `delta` { `text` }: the next piece of the answer, plain text (a short list with "- " is possible). The model marks a recommended dish with `[[dish:ID]]`; the server takes the markers out of the text (also when a marker comes in several pieces) and turns them into `dishes`.
- `done` { `dishes`, `refused` }: the last event. `dishes` are the products of the markers in their order, in the shape of §3.3, only those on the menu of the country and in stock, at most 4. `refused` is `true` when the model declined (the text says why).
- `error` { `code`, `message` }: instead of `done` when the model fails in the middle: `ASSISTANT_BUSY` (rate limit or overload at the provider, a network problem: try again in a moment; Gemini first tries the next model of its list, and once more a model that is busy for a moment) or `ASSISTANT_ERROR`. The text that came before stays. A `done` with `refused: true` and no text gets the web app's own "I can only help with Wok & Roll".

The stream closes after `done` or `error`, and at the latest after 120 seconds. When the guest closes the stream, the server stops asking the model.

**Errors before the stream** (JSON, §4): `400 VALIDATION_ERROR` (with the field: `country`, `language`, `messages`, `messages[i].role`, `messages[i].text`); `429 TOO_MANY_MESSAGES` after 20 questions from one IP in 10 minutes, with `Retry-After: <seconds>`; `503 ASSISTANT_UNAVAILABLE` when the server has no API key.

**Model and cost.** The provider is the one with a key (`ANTHROPIC_API_KEY` first, then `GEMINI_API_KEY`), or `ASSISTANT_PROVIDER` (`anthropic`, `gemini`). `ASSISTANT_MODEL`: default `claude-opus-5-5` (`claude-haiku-5-5` is cheaper) or, for Gemini, a list separated by commas, default `gemini-3.1-flash-lite,gemini-3.5-flash-lite,gemini-flash-lite-latest`. A free Gemini key gets about 20 requests a day per model, and each model has its own quota: a model that answers 429 with a time to wait (`RetryInfo`) rests until then, and the next model of the list answers. A model that is overloaded (500, 503, 504: such an answer can take seconds to come) rests for 2 minutes, so the next guests go straight to the next model; when every model rests, the overloaded ones are tried anyway. Speed comes first: the API sends the headers of an answer with its first word, and a model that has no first word after 2 seconds while another model could still answer is left behind and rests like an overloaded one (an overloaded model can take 13 seconds to say 503); the last model gets the time that is left, and after 6 seconds without a first word the guest gets `ASSISTANT_BUSY` at once. Answers are short and simple (one to three sentences, at most 3 dishes), which keeps them fast and cheap. `model` in the status is the first of the list. `ASSISTANT_EFFORT`: how much the model thinks first, Claude effort (default `low`) or Gemini thinking level (default `minimal`, the first words in about a second). The rules and the data of the country come first and are the same for every guest of a country, so they are cached (Claude: a 1-hour cache breakpoint; Gemini: the implicit cache); only the language, the city with its local time and the conversation are new. Opus, Fable and Sonnet 5.5 use the server-side refusal fallback of the Anthropic API (`fallbacks: "default"`): when the model declines, a fallback model answers in the same call. Gemini is called through its REST API (`streamGenerateContent?alt=sse`), with no extra library.

**Privacy.** The conversation is not stored on the server and its text is never logged (only country, language, number of messages, length, number of dishes, stop reason and time). The browser keeps it in `sessionStorage` for the visit. Guests are told that Panko is an AI and can make mistakes; the prompt tells him never to ask for personal data, to stay on the topic of Wok & Roll and to treat everything the guest writes as a question, not as instructions.

Responses: `200` · `400` · `429` · `503`.

---

## 4. Errors

Every error has the same body (already implemented in `common.error`):

```json
{ "code": "VALIDATION_ERROR", "message": "Request validation failed", "fields": { "items[0].quantity": "must be a whole number from 1 to 99" } }
```

| HTTP | `code` | When | `fields` |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Bean Validation on a parameter or body field: unknown `country` in a GET request, missing `country` or `items` in the quote, more than 50 lines, `quantity` missing, not a whole number or out of 1–99 | field → message |
| 400 | `UNKNOWN_COUNTRY` | Quote: the country code is not one of the 7 countries | `country` |
| 400 | `UNKNOWN_PRODUCT` | Quote: the dish does not exist, or the dish or its category is hidden | `items[i].productId` |
| 400 | `PRODUCT_NOT_SOLD` | Quote: the dish has no price in this country | `items[i].productId` |
| 400 | `OUT_OF_STOCK` | Quote: the dish is out of stock in this country | `items[i].productId` |
| 400 | `INVALID_MODIFIER` | Quote: a modifier does not exist, is hidden, belongs to another dish, has no price in this country or is chosen twice (`items[i].modifierIds[j]`); or the dish has sizes and not exactly one SIZE is chosen (`items[i].modifierIds`) | see left |
| 400 | `BAD_REQUEST` | A required query parameter is missing, or has the wrong type (`categoryId=abc`) | parameter → message |
| 400 | `MALFORMED_REQUEST` | The body is not valid JSON, or a value has an impossible type (`"quantity": "two"`) | `{}` |
| 404 | `NOT_FOUND` | Dish not found, hidden or not sold in the country; unknown path with a valid token (without a token an unknown path gives `401`, because security runs before routing) | `{}` |
| 405 | `METHOD_NOT_ALLOWED` | Wrong HTTP method | `{}` |
| 406 | `NOT_ACCEPTABLE` | The `Accept` header asks for a format the endpoint cannot give, for example JSON from `reports/sales.pdf` | `{}` |
| 500 | `INTERNAL_ERROR` | Unexpected error. Details are only in the server log. | `{}` |
| 500 | `PRICING_CONFIG_MISSING` | Data problem: the country has no VAT rate. Not the client's fault. | `{}` |

**v1.1: request id.** Every error body also has `requestId` (§1), for example `{ "code": "SLOT_FULL", "message": "This time slot is full", "fields": { "slotStart": "This time slot is full. Please choose another time." }, "requestId": "9f1c2e7a-3b4d-4c55-9d1e-0a6b7c8d9e10" }`. Old clients ignore it.

**v1.1 codes**

| HTTP | `code` | When | `fields` |
|---|---|---|---|
| 400 | `UNKNOWN_CAFE` | Orders, bookings, time slots: the café does not exist, is not active, or is not in the order's country | `cafeId` |
| 400 | `UNKNOWN_DELIVERY_ZONE` | Quote, orders: the zone does not exist, is not active, or is not in the country | `deliveryZoneId` |
| 400 | `PAST_DATE` | Order date before today; booking date or time in the past or less than 60 minutes from now | `orderDate`, `date`, `time` |
| 400 | `INVALID_SLOT` | `slotStart` is not a slot of that café and day, or starts in less than 30 minutes | `slotStart` |
| 401 | `UNAUTHORIZED` | No token on a protected endpoint, or any bad token (signature, `exp`, `iss`, `aud`) on any endpoint except `auth/login` and `auth/register`, which ignore the token; header `WWW-Authenticate: Bearer` | `{}` |
| 401 | `INVALID_CREDENTIALS` | Login: wrong email or password | `{}` |
| 403 | `FORBIDDEN` | The role may not use this endpoint (§8) | `{}` |
| 404 | `NOT_FOUND` | Also: an order of another user in `me/orders` | `{}` |
| 409 | `SLOT_FULL` | The slot has no free place | `slotStart` |
| 409 | `PRICE_CHANGED` | `expectedTotal` is not the server total; the message has the new total | `expectedTotal` |
| 409 | `NO_TABLE_AVAILABLE` | Booking: no free table for this time and number of guests | `time` |
| 409 | `EMAIL_TAKEN` | Register with an email that exists | `email` |
| 409 | `VERSION_CONFLICT` | Admin: the row was changed by someone else | `version` |
| 409 | `INVALID_STATUS_CHANGE` | Staff: a status change that is not allowed | `status` |
| 422 | `IDEMPOTENCY_KEY_REUSED` | The same `Idempotency-Key` with a different body | `Idempotency-Key` |
| 429 | `TOO_MANY_ATTEMPTS` | 5 failed logins in 15 minutes; header `Retry-After` | `{}` |
| 429 | `TOO_MANY_MESSAGES` | v1.14: 20 questions to Panko from one IP in 10 minutes; header `Retry-After` | `{}` |
| 503 | `ASSISTANT_UNAVAILABLE` | v1.14: the AI chat is off (no API key on the server) | `{}` |

`400 VALIDATION_ERROR` also covers the shape rules of every new field in §3.6–3.19 (lengths, formats, ranges, paging).

**How the frontend handles the v1.1 errors**

- `401`: delete the token, show "Please log in again", go to Log in; the cart stays.
- `403`: a "No access" page.
- `409`: show the message next to the related field and load fresh data (slots for `SLOT_FULL`, a new quote for `PRICE_CHANGED`).
- `422`: should not happen if one key is used per attempt; log it as a bug.
- `429`: show "Too many attempts, try again in N minutes" from `Retry-After`.

**Several problems at once.** The quote checks every line and returns **all** problems together in `fields`; `code` is the code of the first problem. The UI shows every message next to its line.

**Field keys.** Query parameters use their name (`country`, `categoryId`). Body fields use the request path: `items[1].productId`, `items[0].quantity`, `items[0].modifierIds[1]`, where `i` is the line and `j` the modifier position in the request.

**Examples**

| Request problem | Status and body |
|---|---|
| `GET /api/v1/products?country=FRA` | `400 {"code":"VALIDATION_ERROR","message":"Request validation failed","fields":{"country":"must be one of UZB, KAZ, GEO, UKR, CHN, GBR, USA"}}` |
| `GET /api/v1/products` (no country) | `400 {"code":"BAD_REQUEST","message":"Required parameter is missing","fields":{"country":"Required parameter is missing"}}` |
| `GET /api/v1/products/99?country=UZB` | `404 {"code":"NOT_FOUND","message":"Product 99 is not sold in UZB","fields":{}}` |
| Quote with `"quantity": 0` in line 0 | `400 {"code":"VALIDATION_ERROR","message":"Request validation failed","fields":{"items[0].quantity":"must be a whole number from 1 to 99"}}` |
| Quote with Tuna nigiri (out of stock) in line 1 | `400 {"code":"OUT_OF_STOCK","message":"Some cart items cannot be ordered","fields":{"items[1].productId":"Tuna nigiri is out of stock"}}` |
| Quote for udon with two sizes | `400 {"code":"INVALID_MODIFIER","message":"Some cart items cannot be ordered","fields":{"items[0].modifierIds":"choose exactly one size"}}` |
| Quote with `"country": "USA"` | `400 {"code":"UNKNOWN_COUNTRY","message":"Unknown country code","fields":{"country":"Unknown country code"}}` |
| Quote with `"quantity": 1.5` | `400 {"code":"VALIDATION_ERROR","message":"Request validation failed","fields":{"items[0].quantity":"must be a whole number from 1 to 99"}}` |

**How the frontend handles errors**

- `400` with `fields`: show the message as text next to the field; `items[i].…` goes to cart line `i` (#54).
- `400` without `fields`: a short general message. It means the UI sent something it should have blocked, so log it as a bug.
- `404` on the product modal: close the modal, show a toast "This dish is not available in {country}", reload the menu.
- `5xx` or no network: the error state with a Retry button (#51). Never show the `message` of a `500`.

**How QA asserts errors:** status, `code` and the `fields` keys. Not the message text, which may be reworded.

---

## 5. Money and rounding

Calculated only in the backend `pricing` package (architecture.md §1), with `BigDecimal`, rounding `HALF_UP` to 2 decimals after each step (Decision Log, 5 Nov).

| Step | Formula | Stage |
|---|---|---|
| Unit price | `price + Σ priceDelta` | 2 |
| Line total | `unitPrice × quantity` | 2 |
| Subtotal | `Σ lineTotal` | 2 |
| VAT | `round(subtotal × vatRate / 100)` | 2 |
| Amount incl. VAT | `subtotal + vat` | 2 |
| Discount rate | Rate of the highest threshold `≤ amountInclVat` for this country (table below, local currency; the rates are 3 / 5 / 7 / 10 / 15 % everywhere); below the first threshold → 0 | 3 |
| Discount | `round(amountInclVat × discountRate / 100)` | 3 |
| Delivery fee | Zone fee for `DELIVERY`, `0.00` for `PICKUP`; added last, does not change the threshold | 3 |
| Total | `amountInclVat − discount + deliveryFee` | 3 |

Worked example (the quote from §3.5, Stage 3 pickup): 170.00 → VAT 25.50 → 195.50 → 3 % discount 5.865 → **5.87** (half-up) → total **189.63**.

Worked example with delivery (the quote above, zone fee 20.00): 170.00 → VAT 25.50 → 195.50 → discount 5.87 → plus delivery fee 20.00 → total **209.63**. The fee is outside VAT and discount.

The two worked examples and `pricing-examples.json` use the demo scale of the project plan (thresholds 100 / 500 / 700 / 1000 / 5000); the backend unit tests check the formula with them.

**v1.4: real prices.** The seed now has prices at the level of a Tashkent sushi bar (a roll 69,000–79,000 UZS), converted to the other currencies (1 USD ≈ 12,700 UZS · 510 KZT · 2.70 GEL · 41.5 UAH · 7.15 CNY · 0.79 GBP; the US prices are rounded to 5 cents) and rounded to café prices. Thresholds and zone fees are converted the same way:

| Country | Currency | 3 % from | 5 % from | 7 % from | 10 % from | 15 % from | Delivery zones |
|---|---|---|---|---|---|---|---|
| UZB | UZS | 100,000 | 500,000 | 700,000 | 1,000,000 | 5,000,000 | 15,000 / 20,000 / 30,000 |
| KAZ | KZT | 4,000 | 20,000 | 28,000 | 40,000 | 200,000 | 600 / 800 / 1,200 |
| GEO | GEL | 20 | 100 | 150 | 200 | 1,000 | 3.50 / 4.50 / 6.50 |
| UKR | UAH | 300 | 1,600 | 2,300 | 3,300 | 16,000 | 50 / 65 / 100 |
| CHN | CNY | 55 | 280 | 400 | 550 | 2,800 | 8 / 11 / 17 |
| GBR | GBP | 6 | 30 | 45 | 60 | 300 | 0.95 / 1.25 / 1.85 |
| USA | USD | 8 | 40 | 55 | 80 | 400 | 1.20 / 1.55 / 2.35 |

In the US the menu prices are before tax too, as US menus are; the 7 % is added in the cart like VAT elsewhere.

Example on the seed (UZB, pickup): 2 × Salmon avocado roll 79,000 = 158,000.00 → VAT 15 % = 23,700.00 → 181,700.00 → 3 % = 5,451.00 → total **176,249.00 UZS**; with the Tashkent: Center zone (20,000.00) **196,249.00 UZS**.

Reports (§3.16–3.19) add up the saved, already rounded order values; only `averageCheck` is rounded again (HALF_UP, 2 decimals). PDF and CSV print the same `BigDecimal` values with exactly 2 decimals, never through `double`.

The shared expected numbers for unit tests, API tests and manual tests are in [`wok-and-roll-qa/test-data/pricing-examples.json`](../wok-and-roll-qa/test-data/pricing-examples.json) (#42), for example UZB 400.00 → 446.20 (pickup).

---

## 6. Types

**TypeScript** (frontend `src/services/api/types.ts`, also for QA):

```ts
export type CountryCode = 'UZB' | 'KAZ' | 'GEO' | 'UKR' | 'CHN' | 'GBR' | 'USA';

export interface Country {
  code: CountryCode; name: string; currency: string; vatRate: number;
  timeZone: string; cities: string[]; // v1.1, v1.9
}
export interface Category { id: number; name: string }

export interface Product {
  id: number; categoryId: number; name: string; description: string | null;
  imageUrl: string | null; pieces: number | null; weightGrams: number | null; volumeMl: number | null;
  price: number; currency: string; inStock: boolean; hasModifiers: boolean;
  regularPrice: number | null; setItems: SetItem[]; // v1.10
}
export interface SetItem { productId: number; name: string; quantity: number }

export type ModifierType = 'SIZE' | 'EXTRA';
export interface Modifier { id: number; type: ModifierType; name: string; priceDelta: number }
export interface ProductDetail extends Product { modifiers: Modifier[] }

export interface CartQuoteItem { productId: number; quantity: number; modifierIds: number[] }
export interface CartQuoteRequest { country: CountryCode; items: CartQuoteItem[]; scenario?: Scenario; deliveryZoneId?: number | null }

export interface CartQuoteLine {
  productId: number; name: string; modifiers: Modifier[];
  quantity: number; unitPrice: number; lineTotal: number;
}
export interface CartQuoteResponse {
  country: CountryCode; currency: string; scenario: Scenario; lines: CartQuoteLine[];
  subtotal: number; vatRate: number; vat: number; amountInclVat: number;
  discountRate: number; discount: number; deliveryFee: number; total: number; // v1.1 (#145)
}

export interface ApiError { code: string; message: string; fields: Record<string, string>; requestId?: string }

// v1.1
export type Scenario = 'PICKUP' | 'DELIVERY';
export type OrderStatus = 'NEW' | 'CONFIRMED' | 'DONE' | 'CANCELLED';
export type Role = 'ADMIN' | 'STAFF' | 'VIEWER' | 'CUSTOMER';
export interface Page<T> { items: T[]; nextPageToken: string | null }

export interface CountryV11 extends Country { timeZone: string }
export interface Cafe {
  id: number; country: CountryCode; name: string; address: string; phone: string | null;
  opensAt: string; closesAt: string; timeZone: string;
}
export interface DeliveryZone {
  id: number; country: CountryCode; name: string; cafeId: number; fee: number; currency: string; deliveryMinutes: number;
}
export interface TimeSlot { start: string; end: string; capacity: number; remaining: number; available: boolean; reason: 'FULL' | 'PAST' | null }
export interface TimeSlots { cafeId: number; date: string; timeZone: string; slots: TimeSlot[] }

export interface OrderRequest {
  country: CountryCode; orderDate: string; scenario: Scenario;
  cafeId: number | null; deliveryZoneId: number | null; deliveryAddress: string | null;
  slotStart: string; contactName: string; contactPhone: string;
  contactEmail?: string | null; comment?: string | null; expectedTotal?: number;
  items: CartQuoteItem[];
}
export interface OrderResponse {
  orderNumber: string; status: OrderStatus; createdAt: string; country: CountryCode; currency: string;
  orderDate: string; slotStart: string; slotEnd: string; scenario: Scenario;
  cafe: { id: number; name: string; address: string };
  deliveryZone: { id: number; name: string } | null; deliveryAddress: string | null;
  contactName: string; contactPhone: string; lines: CartQuoteLine[];
  subtotal: number; vatRate: number; vat: number; amountInclVat: number;
  discountRate: number; discount: number; deliveryFee: number; total: number;
}
export interface OrderSummary {
  orderNumber: string; createdAt: string; orderDate: string; slotStart: string; country: CountryCode;
  currency: string; scenario: Scenario; status: OrderStatus; itemCount: number; total: number;
}

export interface BookingRequest {
  cafeId: number; date: string; time: string; guests: number;
  contactName: string; contactPhone: string; contactEmail?: string | null; comment?: string | null;
}
export interface BookingResponse {
  bookingNumber: string; status: 'CONFIRMED' | 'CANCELLED'; createdAt: string;
  cafe: { id: number; name: string; address: string };
  date: string; time: string; endTime: string; guests: number; tableSeats: number; contactName: string;
}

export interface AuthResponse { token: string; role: Role }
export interface Me { id: number; email: string; fullName: string; phone: string | null; role: Role }

export interface SalesNumbers {
  orders: number; subtotal: number; vat: number; discount: number; deliveryFee: number; total: number; averageCheck: number;
}
export interface RegionSales extends SalesNumbers { country: CountryCode; countryName: string; currency: string }
export interface SalesByRegion { from: string; to: string; generatedAt: string; totalOrders: number; regions: RegionSales[] }
export interface MonthSales extends SalesNumbers { month: number }
export interface SalesByMonth {
  country: CountryCode; countryName: string; currency: string; year: number; generatedAt: string;
  months: MonthSales[]; totals: SalesNumbers;
}
```

**Java DTO records** (`dto` packages, architecture.md §2): `CountryResponse`, `CategoryResponse`, `ProductResponse`, `ProductDetailResponse`, `ModifierResponse`, `CartQuoteRequest`, `CartQuoteItemRequest`, `CartQuoteResponse`, `CartQuoteLineResponse`.

**v1.1 DTO records:** `CafeResponse`, `DeliveryZoneResponse`, `TimeSlotsResponse`, `TimeSlotResponse`, `OrderRequest`, `OrderItemRequest` (or reuse `CartItemRequest`), `OrderResponse`, `OrderSummaryResponse`, `BookingRequest`, `BookingResponse`, `RegisterRequest`, `LoginRequest`, `AuthResponse`, `MeResponse`, `StaffOrderResponse`, `StaffBookingResponse`, `OrderStatusRequest`, `AdminProductResponse`, `AdminProductRequest`, `AdminPriceRequest`, `AdminCategoryRequest`, `SalesByRegionResponse`, `RegionSalesResponse`, `SalesByMonthResponse`, `MonthSalesResponse`, `PageResponse<T>`.

Backend notes:

- Validate `country` with one shared rule (for example a `@Pattern` on the parameter) so the message is the same everywhere.
- Read `quantity` as `BigDecimal` and check it with a whole-number rule (as in #119). As an `Integer`, Jackson would silently turn `1.5` into `1`.
- Business checks that need the database (dish exists, sold in the country, in stock, modifiers) collect all problems and throw one `ApiException(400, <first code>, ..., fields)` with the codes and field keys of §4 (as in `pricing.md`).
- In GET requests an unknown country is rejected by `@Pattern` in the controller (`VALIDATION_ERROR`). In the quote it is `UNKNOWN_COUNTRY` from pricing.
- **v1.1:** "today" and "now" only through `CountryClock` (§1). Every public endpoint is added to the `SecurityConfig` public lists in the same PR, with a test, because everything else is denied by default. SQL only with named parameters (`JdbcClient`). An order, a booking and a status change are each one `@Transactional` service method.

---

## 7. Mocks and tests

- Until an endpoint is merged, the frontend uses mock JSON **copied from the examples in this file**, behind the same API client, so switching to the real API changes nothing in the screens.
- API tests (#112) check every endpoint against §3 and §4 with the seed data: 5 countries, prices with 2 decimals, Tuna nigiri out of stock, a dish without a price in a country, unknown country.
- E2E tests (#108, #118, #122) use these shapes for test data and the `pricing-examples.json` numbers for totals.
- **v1.1:** API tests for every endpoint of §3.6–3.19 with status, `code` and field keys; the role matrix of §8 with the demo users; concurrency tests for slots (§3.8) and bookings (§3.10); report numbers against the reference query of §3.16 on the deterministic seed (#171).
- Every API test and bug report keeps the `X-Request-Id` of a failed call.

---

## 8. Security

**Covered in Stage 2**

| Risk | Control |
|---|---|
| Client sends its own prices | Requests carry only ids and quantities. Prices and deltas are always loaded from the database (`pricing.md`, step 3), and the order recalculates in Stage 3. |
| Bad or oversized input | Country from a fixed list; quantity a whole number 1–99, read as a decimal so `1.5` is never cut to `1` (§6); at most 50 lines; modifiers must belong to the dish. |
| SQL injection | Only parameterized SQL (`JdbcClient` with named parameters). No SQL built from strings. |
| Leaking internals | A `500` returns only `INTERNAL_ERROR`; stack traces stay in the server log. |
| Personal data | None in Stage 2. All five endpoints are public and read-only; the quote saves nothing. |

**v1.1 (Stages 3–5)**

**Roles.** One place: `SecurityConfig` (URL rules), deny by default (`anyRequest().authenticated()`), and `SecurityRulesTest` with one test per cell:

| Endpoint group | Guest | CUSTOMER | STAFF | VIEWER | ADMIN |
|---|---|---|---|---|---|
| Menu, cart quote, cafés, zones, slots, orders, bookings, register, login | yes | yes | yes | yes | yes |
| `/me`, `/me/**` (own data only) | 401 | yes | yes | yes | yes |
| `/staff/**` | 401 | 403 | yes | 403 | yes |
| `/reports/**` (JSON, PDF, CSV) | 401 | 403 | 403 | yes | yes |
| `/admin/**` | 401 | 403 | 403 | 403 | yes |

**OWASP API Security Top 10 (2023)**

| Risk | Our control |
|---|---|
| API1 Broken object level authorization | `/me/**` reads the user id only from the token; another user's order → 404 |
| API2 Broken authentication | BCrypt (cost 10); JWT HS256 only, with `exp`, `iss` and `aud` checked; `JWT_SECRET` is random and at least 32 bytes (`openssl rand -base64 48`), never a word (RFC 8725); 429 after 5 failed logins; one message for a wrong email or password |
| API3 Broken object property level authorization | DTO records list every field: no `password_hash` in any response; register cannot set `role`; admin PATCH accepts only the listed fields |
| API4 Unrestricted resource consumption | At most 50 lines × 99; maximum length for every string; `pageSize` ≤ 100; report period ≤ 366 days; the AI chat: 20 questions per IP in 10 minutes, 500 characters a question, 20 messages, a short answer (§3.21) |
| API5 Broken function level authorization | URL rules per role group, deny by default, a test per role |
| API6 Unrestricted access to sensitive business flows | Slot and table capacity checked in the database (atomic upsert, exclusion constraint); `Idempotency-Key` against double orders; random order numbers |
| API7 Server side request forgery | The API never loads a URL from input; `imageUrl` is only a `/images/…` path shown by the frontend |
| API8 Security misconfiguration | No stack traces in responses; Spring Security default headers and `Cache-Control: no-store`; CORS only for the frontend origin, never `*`; Swagger off or protected in a deployed demo |
| API9 Improper inventory management | This file and Swagger list every endpoint with an owner (§2); no undocumented endpoints |
| API10 Unsafe consumption of APIs | One third-party API for the AI chat (§3.21): Anthropic or Google Gemini. The key is only in the server environment; the answer is shown as plain text (never HTML), its dish ids are checked against the menu, and only the data of the menu goes to the model, never personal data |

**Token in the frontend.** `localStorage` key `wr.auth`, dropped when `exp` has passed. This is readable by scripts if we ever have an XSS bug, so: React escapes output, we never use `dangerouslySetInnerHTML`, and the token is never put in a URL (downloads use `fetch` and a Blob). A `httpOnly` cookie is the v2 option.

**Personal data.** Orders and bookings keep name, phone, optional email and address, because the café needs them. They are shown only to the owner (`/me`), staff and admins (the admin panel, §3.20, is ADMIN only; a viewer sees only report sums), and are never written to logs (log the order number, not the phone). Seed and demo data use invented names and `.test` emails.

## 9. Changing this contract

- **Additive change** (a new optional field, a new endpoint): allowed. Update this file in the same PR as the code.
- **Breaking change** (rename, remove, change a type or meaning): write in the story first, agree with every consumer in the table at the top, then change the code, this file, the mocks and the tests in one PR.
- The owner of an endpoint keeps this file in sync with Swagger. A difference found in review or testing is a bug for the owner.
- **Versions.** Additive changes raise the minor version of this file (1.1, 1.2, …) and stay under `/api/v1`. A breaking change would need `/api/v2`; none is planned for the MVP.

## 10. Decisions in this contract

| Decision | Why |
|---|---|
| Modifiers come inside `GET /products/{id}`, not from a separate endpoint | One request when the modal opens; matches architecture.md §3. Task #113 is updated to this. |
| Category filter is `categoryId` | The schema has no slug column; ids are stable from the seed. |
| Stage 2 lists are arrays without paging | 10 dishes now. New growing lists use paging (§1.1) from the start. |
| The quote returns subtotal and VAT in Sprint 2; discount, delivery fee and total arrive in Stage 3 as new fields | Matches the sprint plan (#116, #55) and keeps the change additive, so Sprint 2 clients keep working. |
| Quantity is a whole number 1–99; decimals are rejected | #54 needs "whole number ≥ 1"; the upper limit stops absurd carts; the Jackson setting stops silent rounding. |
| An invalid line fails the whole quote, with its field path | The UI marks the exact line, and the server never shows totals for a cart it cannot price. |
| `hasModifiers` in the product list | The card knows whether to open the modal without an extra request per dish. |
| Shape errors use `VALIDATION_ERROR`; cart business errors have their own codes (`UNKNOWN_PRODUCT`, `PRODUCT_NOT_SOLD`, `OUT_OF_STOCK`, `INVALID_MODIFIER`, `UNKNOWN_COUNTRY`) | Matches #116 / #119 and `pricing.md`. The UI can show a precise message (for example "out of stock"), and still handles every `400` with `fields` the same way. |
| The map of the home page uses `latitude` and `longitude` of `/cafes` (v1.7), not a separate endpoint, and OpenStreetMap tiles from OpenFreeMap | The list and the map show the same cafés from one request. OpenFreeMap needs no key and has no request limit, so there is no secret in the frontend and no bill. |
| One café resource for pickup and table booking (`/cafes`, not `/pickup-points`) | One list and one set of opening hours. The UI still says "pickup point" in checkout. The public list in `SecurityConfig` (#134) changes from `pickup-points` to `cafes`. |
| Time slots are calculated from opening hours; only the reservations are stored | No daily job to create slot rows; capacity is enforced by one atomic SQL statement (§3.8). |
| "Today" is the date in the country's time zone | A guest in Kyiv at 23:30 must still be able to order for today; one Tashkent date was wrong for GEO, UKR and CHN. Replaces "Tashkent date" from the Decision Log, 5 Nov. |
| `Idempotency-Key` on orders and bookings | A double click or a network retry must not create two orders or take two slot places (IETF draft). |
| `expectedTotal` → `409 PRICE_CHANGED` | The guest never pays a total they did not see, for example after an admin price change between quote and order. |
| Random order numbers `WR-XXXXXXXX` | Cannot be guessed and do not show our order volume. |
| `404`, not `403`, for another user's order | Does not reveal that the order exists (OWASP API1). |
| Reports count all orders except `CANCELLED`, by `order_date` | A new order shows in the report at once (good for the demo); cancelled orders are not sales. |
| One PDF with both reports | The PDF is what the admin sees on the page: both charts with the current filters. |
| Keep `{code, message, fields}` and add `requestId`; no `application/problem+json` (RFC 9457) in v1 | RFC 9457 changes the field names and the media type, a breaking change for the frontend and the tests. Our body has the same idea (`code` is like `type`). It can come with v2. |
| Paging with `pageSize` and an opaque `pageToken`, no total count | Google AIP-158 style: the token can change from an offset to a keyset later without breaking clients. |
| Login limit per email and IP, in memory | Stops password guessing without locking out a real user from another network; one backend instance is enough for the MVP. |
| Booking: 90 minutes, tables of 2–8 seats, smallest free table, exclusion constraint | No double booking even with two parallel requests. |
| Staff see all cafés | The MVP has no link between a user and a café. |

## 11. Delivery plan

This file now covers the whole MVP. Changes after the agreement on Mon 16 Nov are additive only and go into the PR that needs them (§9). Dates and owners: [`mvp-plan.md`](mvp-plan.md).

| Version | Sprint | Endpoints | Status |
|---|---|---|---|
| 1.0 | Sprint 2 · Stage 2 | §3.1–3.5 | built |
| 1.1 | Sprint 3 (16–22 Nov) | §3.1 `timeZone`, §3.5 totals, §3.6–3.9, §3.11 changes, §3.16, §3.17, first part of §3.18 | agreed Mon 16 Nov |
| 1.1 | Sprint 4 (23–29 Nov) | §3.10, §3.13–3.15, the rest of §3.18, §3.19 | feature freeze Thu 26 Nov 20:00 |
| 1.11 | Sprint 4 (23–29 Nov) | §3.20 admin panel | built |

## 12. References

- RFC 9457, Problem Details for HTTP APIs (2023): why our error body has a `code` and stays JSON.
- IETF draft-ietf-httpapi-idempotency-key-header-07 (2025): the `Idempotency-Key` header, 422 for a reused key.
- RFC 6750 (Bearer tokens, `WWW-Authenticate`), RFC 8725 (JWT Best Current Practices), RFC 7518 §3.2 (an HS256 key has at least 256 bits).
- OWASP API Security Top 10 (2023); OWASP CSV Injection.
- RFC 4180 (CSV), RFC 6266 (`Content-Disposition`), RFC 3339 / ISO 8601 (dates and times), ISO 4217 (currency codes).
- IANA time zone database 2024a (Kazakhstan on UTC+5 from 1 March 2024).
- Google API Improvement Proposals AIP-158 (pagination); Zalando RESTful API Guidelines (money, enums, top-level objects).
- PostgreSQL documentation: `INSERT … ON CONFLICT`, exclusion constraints with `btree_gist`.
