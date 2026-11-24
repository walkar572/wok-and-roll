# Wok & Roll

Teamwork 2026, Team 12. A web app for a sushi and wok restaurant chain in six countries
(Uzbekistan, Kazakhstan, Georgia, Ukraine, China, the United Kingdom): menu with modifiers, cart and checkout with
regional prices and VAT, pickup or delivery with time slots, table booking, accounts with order
history, role-based tools for staff and admins, and sales reports with PDF and CSV export.

| Part | Stack | Folder |
|---|---|---|
| Frontend | React 18, TypeScript, Vite, React Router, Radix, react-day-picker, MapLibre GL | [`wok-and-roll-front`](wok-and-roll-front) |
| Backend | Java 21, Spring Boot 3.5, Spring Security (JWT), JdbcClient, Flyway, OpenPDF | [`wok-and-roll-back`](wok-and-roll-back) |
| Database | PostgreSQL 16 | migrations in `wok-and-roll-back/src/main/resources/db/migration` |
| QA | Playwright API and E2E tests, test cases | [`wok-and-roll-qa`](wok-and-roll-qa) |

Docs: [API contract](docs/api-contract.md) · [Architecture](docs/architecture.md) ·
[Technical overview](docs/technical-overview.md) · [ERD](wok-and-roll-back/docs/erd.md) ·
[Pricing](wok-and-roll-back/docs/pricing.md) · [How we work](HOW-WE-WORK.md)

## Run it locally

You need Docker and Node.js 20 or newer.

```sh
# 1. Backend and database (from the repository root). Once: a random JWT secret in .env
echo "JWT_SECRET=$(openssl rand -base64 48)" > .env
docker compose up --build          # API on http://localhost:8080, Swagger on /swagger-ui.html

# 2. Frontend (a second terminal, from the repository root)
npm install
npm run dev                         # http://localhost:5173, /api is proxied to :8080
```

Port 5432 busy? `DB_PORT=5433 docker compose up --build`.
The database starts with the menu (real prices at the level of Tashkent, converted to every
currency), cafés, delivery zones and about 3,400 orders from June to November 2026, so the
reports have data at once.

Dish photos live in `wok-and-roll-front/public/images` (512 × 512 PNG, the dish on a cream
background where the photo allows it). Until a file is there, the dish shows a neutral
placeholder; a photo can be replaced by a file with the same name. Nineteen photos are free stock
photos under the [Pexels license](https://www.pexels.com/license/), cropped and, where the table
allowed it, put on the cream background:
[California roll](https://www.pexels.com/photo/sushi-roll-on-white-ceramic-plate-10692544/),
[set for two](https://www.pexels.com/photo/close-up-photo-of-sushi-served-on-table-248444/),
[teriyaki noodles](https://www.pexels.com/photo/close-up-of-chicken-noodles-dish-24738507/),
[edamame](https://www.pexels.com/photo/close-up-of-fresh-edamame-beans-in-bowl-28460867/) (hidden
from the menu since v1.8),
[Philadelphia roll](https://www.pexels.com/photo/sushi-on-ceramic-plate-3620705/),
[dragon roll](https://www.pexels.com/photo/plate-of-sushi-858496/),
[crunchy shrimp roll](https://www.pexels.com/photo/plate-of-sushi-343870/),
[chicken ramen](https://www.pexels.com/photo/top-view-of-a-bowl-of-ramen-20802552/),
[beef yakisoba](https://www.pexels.com/photo/photo-of-food-on-a-white-plate-6718705/),
[spicy egg fried rice](https://www.pexels.com/photo/close-up-of-a-bowl-of-kimchi-fried-rice-with-an-egg-24738523/),
[chicken karaage](https://www.pexels.com/photo/fried-chicken-1860202/),
[spring rolls](https://www.pexels.com/photo/fried-spring-rolls-and-dumplings-on-top-of-tray-218769/),
[peanuts](https://www.pexels.com/photo/close-up-photo-of-a-bowl-full-of-roasted-groundnuts-on-brown-wooden-table-2112789/),
[mochi ice cream](https://www.pexels.com/photo/mochi-ice-cream-on-the-wooden-tray-6645975/),
[matcha cheesecake](https://www.pexels.com/photo/sliced-cake-on-black-plate-1860209/),
[matcha latte](https://www.pexels.com/photo/a-glass-of-matcha-latte-8004562/),
[jasmine tea](https://www.pexels.com/photo/cup-of-aromatic-jasmine-tea-served-on-table-in-garden-7138780/),
[salmon sashimi](https://www.pexels.com/photo/sushi-on-a-wooden-bowl-8472633/),
[mango sticky rice](https://www.pexels.com/photo/mango-and-rice-with-dipping-sauce-on-a-white-plate-19856579/).

The café map on the home page uses [MapLibre GL](https://maplibre.org/) with free vector tiles
from [OpenFreeMap](https://openfreemap.org/) (no API key). Map data © OpenStreetMap
contributors; the attribution is shown on the map.

### Demo accounts

Only for local runs and the demo (they are created by a migration).

| Role | Email | Password | Can use |
|---|---|---|---|
| Admin | `admin@wokandroll.test` | `AdminDemo2026` | everything: reports, menu admin, café orders |
| Staff | `staff@wokandroll.test` | `StaffDemo2026` | café orders and bookings, status changes |
| Viewer | `viewer@wokandroll.test` | `ViewerDemo2026` | reports, PDF and CSV |
| Customer | `customer@wokandroll.test` | `CustomerDemo2026` | My orders (has a history) |

Guests can order and book a table without an account.

## Features

| Area | What works |
|---|---|
| Header | Stays at the top while the page scrolls (menu, country and basket always one tap away); soft rounded highlight on hover; on a phone a black menu grows out of the menu button, and the country list and the menu never open over each other |
| Languages | The country switcher shows every country with its language and currency in that language (United Kingdom · English · GBP). The interface follows the country: Uzbek, Kazakh, Georgian, Ukrainian, Chinese, British English (dishes, messages, money and dates too); the brand "Wok & Roll" and the "Wok. Roll." of the home title stay |
| Home | A slideshow of dishes from the menu with their price in the chosen country (arrows, swipe, pause; it stops on hover and with reduced motion); the cafés of the country on a map and in a list with address, hours, "open now" in the café's time zone, phone and "Book a table" (the booking form opens with that café); four favourite dishes |
| Menu and cart | 24 dishes in 5 categories (sushi and rolls, from the wok, sides, desserts, drinks); dish cards with the ingredients and the portion (pieces, grams, ml) that grow a little and light up on hover, country switcher in the header (saved), regional prices and stock, modifiers that change the unit price, cart with a live counter, quantity validation |
| Checkout | Pickup café or delivery zone with a fee, date and 30-minute time slots (full and past slots disabled), validation with messages next to the fields, totals from the server, idempotent order placement |
| Booking | Café, date (30 days ahead), time, 1–8 guests; the server picks the smallest free table; no double booking even for parallel requests |
| Accounts | Register, log in, log out, session expiry; My orders with paging and order details |
| Roles | Menu items and routes by role (ADMIN, STAFF, VIEWER, CUSTOMER), the same rules on the server |
| Reports | Orders by country (period filter) and total by month (country and year filters), money tables, KPIs, Generate PDF with preview, Download PDF, Export CSV — the same numbers everywhere |
| Admin | Price, stock and visibility of every dish per country, with conflict detection |
| Staff | Orders and bookings of a café for a day, status changes (cancel frees the slot) |

## Checks

```sh
npm run lint        # ESLint (with jsx-a11y) and Prettier, frontend
npm run build       # TypeScript and Vite build

cd wok-and-roll-back
mvn verify          # unit tests and Spotless (Google Java Style)
CI=true mvn verify  # also the integration tests; needs PostgreSQL on localhost:5432
```

GitHub Actions runs the same commands on every pull request to `dev` and `main`.

## Repository

This repository continues the team repository's `dev` branch from snapshot `46ec106`
(imported without history). Work happens on branches off `dev`; `main` only takes merged
pull requests.
