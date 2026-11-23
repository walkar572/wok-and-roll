# Backend ERD

One data model for the whole backend. Refs #19.
Each backend developer adds the tables of their part to this file.

| Part | Owner | Status |
|---|---|---|
| Users, roles, orders | Nodir, Timur | v1.1 (#134, #144) |
| Country, currency, VAT, discounts, catalog, pricing | Abdulaziz, Timur | draft |
| Cafés, delivery zones, time slots, booking | Abdulaziz | v1.1 (#143, #174) |

## Users, roles, orders

```mermaid
erDiagram
    ROLE ||--o{ USER : has
    USER |o--o{ ORDER : places
    COUNTRY ||--o{ ORDER : "priced in"
    ORDER ||--|{ ORDER_ITEM : contains
    PRODUCT ||--o{ ORDER_ITEM : "ordered as"
    ORDER_ITEM ||--o{ ORDER_ITEM_MODIFIER : has
    MODIFIER ||--o{ ORDER_ITEM_MODIFIER : "chosen as"
    CAFE ||--o{ ORDER : "prepared by"
    DELIVERY_ZONE |o--o{ ORDER : "delivered to"

    ROLE {
        bigint id PK
        varchar code "ADMIN, STAFF, VIEWER, CUSTOMER"
    }
    USER {
        bigint id PK
        varchar email UK
        varchar password_hash
        varchar full_name
        varchar phone
        bigint role_id FK
        timestamp created_at
    }
    ORDER {
        bigint id PK
        varchar order_number UK
        bigint user_id FK "null for guest"
        bigint country_id FK
        date order_date
        varchar scenario "PICKUP or DELIVERY"
        bigint cafe_id FK "pickup café, or the café of the zone"
        bigint delivery_zone_id FK "null if pickup"
        time slot_start "local time of the café"
        time slot_end
        varchar delivery_address
        varchar contact_name
        varchar contact_phone
        varchar contact_email
        varchar comment
        char currency_code
        numeric subtotal
        numeric vat_rate "percent, for example 15.00"
        numeric vat_amount
        numeric amount_incl_vat
        numeric discount_rate "percent, for example 3.00"
        numeric discount_amount
        numeric delivery_fee
        numeric total
        varchar status "NEW, CONFIRMED, DONE, CANCELLED"
        uuid idempotency_key UK
        char request_hash "SHA-256 of the request body"
        timestamptz created_at
        timestamptz updated_at
    }
    ORDER_ITEM {
        bigint id PK
        bigint order_id FK
        bigint product_id FK
        varchar product_name "copy at order time"
        int quantity "1 or more"
        numeric unit_price "base price plus modifiers"
        numeric line_total
    }
    ORDER_ITEM_MODIFIER {
        bigint order_item_id FK
        bigint modifier_id FK
        varchar modifier_name "copy at order time"
        numeric price_delta
    }
```

## Why it looks like this

1. One role per user. The plan has four roles and nobody needs two at once, so `role_id` in `user` is enough. RBAC (#65) reads it from the JWT.
2. `user_id` in `order` can be empty, so a guest can order without an account. Order history (#63) shows orders where `user_id` is the logged-in user.
3. The order keeps every step of the price formula (subtotal, VAT, discount, delivery fee, total) and the currency. Reports (#66, #67, #68) then sum saved numbers and do not recalculate old orders after a price or VAT change.
4. Product and modifier names are copied into the order. If the admin renames or deletes a dish, old orders and reports still show the right name.
5. All money is `numeric(12,2)`, in Java `BigDecimal`, as the plan says.

## Country, currency, VAT, discounts, catalog, pricing

```mermaid
erDiagram
    CURRENCY ||--o{ COUNTRY : "used in"
    COUNTRY ||--|| VAT_RATE : has
    COUNTRY ||--o{ DISCOUNT_THRESHOLD : "own thresholds"
    CATEGORY ||--o{ PRODUCT : groups
    PRODUCT ||--o{ REGIONAL_PRICE : "priced per country"
    COUNTRY ||--o{ REGIONAL_PRICE : has
    PRODUCT ||--o{ MODIFIER : offers
    MODIFIER ||--o{ MODIFIER_PRICE : "priced per country"
    COUNTRY ||--o{ MODIFIER_PRICE : has

    CURRENCY {
        char code PK "UZS, KZT, GEL, UAH, CNY"
        varchar name
    }
    COUNTRY {
        bigint id PK
        char code UK "UZB, KAZ, GEO, UKR, CHN"
        varchar name
        char currency_code FK
        varchar time_zone "IANA, for example Asia/Tashkent (v1.1)"
    }
    VAT_RATE {
        bigint id PK
        bigint country_id FK "unique, one rate per country"
        numeric rate "percent, for example 15.00"
        timestamp updated_at
    }
    DISCOUNT_THRESHOLD {
        bigint id PK
        bigint country_id FK "not null, thresholds of this country, local currency"
        numeric min_amount "unique with country_id; 100, 500, 700, 1000, 5000"
        numeric discount_rate "percent; 3, 5, 7, 10, 15"
    }
    CATEGORY {
        bigint id PK
        varchar name UK
        int sort_order
        boolean visible "admin can hide"
    }
    PRODUCT {
        bigint id PK
        bigint category_id FK
        varchar name
        varchar description
        varchar image_url
        boolean visible "admin can hide"
    }
    REGIONAL_PRICE {
        bigint id PK
        bigint product_id FK "unique with country_id"
        bigint country_id FK
        numeric price "local currency, before VAT"
        boolean in_stock
    }
    MODIFIER {
        bigint id PK
        bigint product_id FK
        varchar type "SIZE or EXTRA"
        varchar name
        boolean visible
    }
    MODIFIER_PRICE {
        bigint id PK
        bigint modifier_id FK "unique with country_id"
        bigint country_id FK
        numeric price_delta "local currency, before VAT, can be 0"
    }
```

Links to the first part: `order.country_id` → `country`, `order.currency_code` → `currency.code`, `order_item.product_id` → `product`, `order_item_modifier.modifier_id` → `modifier`.

### Why it looks like this

1. Country codes are UZB, KAZ, GEO, UKR, CHN, as in #50 and the seed data (#33). Currency is its own table with the ISO code as the key, so `order.currency_code` points to it and the countries API returns the currency together with the VAT rate.
2. Regional price is one row per product and country. The price is before VAT, because the formula starts from Subtotal and adds VAT on top (#55). No row means the dish is not sold in that country.
3. Stock is the `in_stock` flag in `regional_price`. The stories only need "in stock / out of stock" (#51, #64), and the menu already reads this row. If we need quantities later, we move it to its own table.
4. Modifier prices are per country for the same reason as product prices: one number cannot be right in UZS and in CNY. Unit price = regional price + sum of chosen `price_delta` (#52). The order copies `price_delta`, so later price changes do not touch old orders.
5. VAT and discount thresholds are tables, not code. The pricing service (#34) reads them, and the admin can change them without a new release. Thresholds are per country and in the local currency: every country has its own 5 rows with the same numbers (100 / 500 / 700 / 1000 / 5000), with no conversion (Decision Log, 5 Nov). Discount rate = the row with the biggest `min_amount` that is not more than Amount incl. VAT. Below 100 there is no row, so no discount (#55). The formula and rounding rules are in `docs/pricing.md`.
6. Category, product and modifier have `visible`. The admin hides them instead of deleting (#64), so old orders keep working.
7. Money is `numeric(12,2)` like in the first part. Rates are `numeric(5,2)` in percent (`15.00`, not `0.15`), in the tables, in the order and in the API.

## Cafés, delivery zones, time slots, booking (v1.1)

Agreed in `docs/api-contract.md` v1.1 (§3.6–3.10). Migrations: #143 (cafés, zones, tables, slot places), #174 (booking).

```mermaid
erDiagram
    COUNTRY ||--o{ CAFE : has
    CAFE ||--o{ DELIVERY_ZONE : "cooks for"
    CAFE ||--o{ CAFE_TABLE : has
    CAFE ||--o{ SLOT_RESERVATION : "slot places"
    CAFE ||--o{ BOOKING : "booked at"
    CAFE_TABLE ||--o{ BOOKING : "reserved as"
    USER |o--o{ BOOKING : makes

    CAFE {
        bigint id PK
        bigint country_id FK
        varchar name
        varchar address
        varchar phone
        time opens_at "local time"
        time closes_at "local time"
        int slot_capacity "orders per 30-minute slot, 1 or more"
        boolean active
    }
    DELIVERY_ZONE {
        bigint id PK
        bigint country_id FK
        bigint cafe_id FK "the café that cooks and delivers"
        varchar name
        numeric fee "local currency, 0 or more"
        int delivery_minutes
        boolean active
    }
    CAFE_TABLE {
        bigint id PK
        bigint cafe_id FK
        varchar label
        int seats "2, 4, 6 or 8"
    }
    SLOT_RESERVATION {
        bigint cafe_id PK
        date slot_date PK
        time slot_start PK
        int reserved "0 to cafe.slot_capacity"
    }
    BOOKING {
        bigint id PK
        varchar booking_number UK
        bigint cafe_id FK
        bigint table_id FK
        bigint user_id FK "null for guests"
        date booking_date
        time start_time
        tstzrange during "90 minutes, no overlap per table"
        int guests "1 to 8"
        varchar contact_name
        varchar contact_phone
        varchar contact_email
        varchar comment
        varchar status "CONFIRMED or CANCELLED"
        uuid idempotency_key UK
        char request_hash
        timestamptz created_at
    }
```

### Why it looks like this

1. One `cafe` for pickup, delivery and booking: the guest picks it for pickup, a zone points to it for delivery, and booking uses its tables. Opening hours live in one place.
2. Slots are not rows per day. The list is calculated from `opens_at` and `closes_at`, and only taken places are stored in `slot_reservation`. Taking a place is one atomic `INSERT … ON CONFLICT … WHERE reserved < capacity` (api-contract.md §3.8), so two guests can never both get the last place.
3. The order keeps `cafe_id` for both scenarios (the kitchen), `delivery_zone_id` only for delivery, and the slot as `slot_start` and `slot_end` local times, not as a foreign key.
4. A table can never be booked twice for overlapping times: `EXCLUDE USING gist (table_id WITH =, during WITH &&)` for confirmed bookings (`btree_gist`).
5. `idempotency_key` is unique in `customer_order` and `booking`, so a retry returns the first result instead of a second row.
6. Admin edits use optimistic locking: `product`, `category` and `regional_price` get `version int not null default 0` (api-contract.md §3.15).
7. `country.time_zone` (IANA name) gives "today" and "now" for each country.

## Open questions for the team

1. ~~Do we keep `vat_rate` and `discount_rate` in the order, or only the amounts?~~ Closed: keep both rates and amounts (`docs/pricing.md`, #34).
2. ~~Order status list: is NEW, CONFIRMED, DONE, CANCELLED enough?~~ Closed: yes. Staff change it; CANCELLED frees the slot place (api-contract.md v1.1, §3.14).
3. ~~Booking (table reservation): is it linked to `user`, or guests can book too?~~ Closed: guests can book; `user_id` is set when the guest is logged in (api-contract.md v1.1, §3.10).
4. ~~Discount thresholds: local currency or one base currency (#44)?~~ Closed: local currency of each country, the same numbers (100 / 500 / 700 / 1000 / 5000), no conversion, 5 rows per country. See the Decision Log, 5 Nov.
5. ~~Rates: percent (15.00) or fraction (0.15)?~~ Closed: percent everywhere, including `order.vat_rate` and `order.discount_rate` (`docs/pricing.md`, #34).
6. Stock: one flag per country (as here) or one flag for all countries?
7. Modifiers: per dish (as here), or shared between dishes, for example one "Extra cheese" for all?