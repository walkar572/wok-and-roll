# Pricing service design

Refs #34. Owner: Timur. Reviewer: Maksudbek.

Goal: one source of truth for money calculations. Prices are calculated **only** in the `pricing` package. The cart quote (#53), the order (#55–57) and the reports read its result. The frontend shows API numbers and never calculates.

Status: ready for review. The rules for thresholds, rounding and delivery fee are in the [Decision Log](https://github.com/ITPU-Teamwork-2026/t12-wok-and-roll/blob/dev/docs/decision-log.md) (5 Nov, Refs #44). Open items are in section 5.

## 1. Formula

```
Subtotal         = sum of line totals
VAT amount       = Subtotal × VAT rate of the country
Amount incl. VAT = Subtotal + VAT amount
Discount amount  = Amount incl. VAT × discount rate
Delivery fee     = fee of the delivery zone (0 for pickup)
Total            = Amount incl. VAT − Discount amount + Delivery fee
```

Details:

- Line total = unit price × quantity. Unit price = regional price + sum of `price_delta` of the chosen modifiers. All of these are before VAT, in the currency of the country (ERD, part 2).
- The discount rate is chosen by **Amount incl. VAT**, not by Subtotal and not by Total. Delivery fee is a fixed fee per delivery zone, in local currency. It is added last, so it is not discounted, has no VAT and does not change the threshold ([Decision Log](https://github.com/ITPU-Teamwork-2026/t12-wok-and-roll/blob/dev/docs/decision-log.md)).
- Discount rate = the row with the biggest `min_amount` that is **not more than** Amount incl. VAT (so exactly 100.00 already gets the first tier). Below the first threshold there is no row, so no discount.
- Rates are stored in percent (`15.00` means 15%), as in the ERD.

### Rounding

`BigDecimal`, scale 2, `RoundingMode.HALF_UP` (README, "Money"). Decided by the Team Lead on 5 Nov (the mentor did not answer this; see the [Decision Log](https://github.com/ITPU-Teamwork-2026/t12-wok-and-roll/blob/dev/docs/decision-log.md)): **round every step to 2 decimals right after it is calculated** (VAT, discount, total), then use the rounded value in the next step. Only VAT amount and Discount amount need rounding (everything else is a sum or a product of values that already have 2 decimals). The reason: the order stores every step (ERD, point 3), and a printed receipt must add up exactly: `Subtotal + VAT = Amount incl. VAT` and `Amount incl. VAT − Discount + Delivery = Total`. If we rounded only at the end, the stored steps could differ from the total by 0.01.

### Worked examples

The thresholds are the real ones (100/500/700/1000/5000 → 3/5/7/10/15%, the same numbers in the local currency of each country). The VAT rate of 15% is illustration only; real rates come from the tables. These numbers were checked with a throw-away Java prototype and go into the shared QA test file.

| # | Case | Subtotal | VAT | Incl. VAT | Disc. rate | Discount | Delivery | Total |
|---|---|---|---|---|---|---|---|---|
| 1 | Below first threshold, delivery | 80.00 | 12.00 | 92.00 | 0 | 0.00 | 5.00 | 97.00 |
| 2 | VAT rounds half-up (1.545 → 1.55) | 10.30 | 1.55 | 11.85 | 0 | 0.00 | 0.00 | 11.85 |
| 3 | Exactly on threshold 100.00 | 86.96 | 13.04 | 100.00 | 3 | 3.00 | 0.00 | 97.00 |
| 4 | Discount rounds half-up (4.515 → 4.52) | 130.87 | 19.63 | 150.50 | 3 | 4.52 | 10.00 | 155.98 |
| 5 | Exactly on top threshold 5000.00 | 4347.83 | 652.17 | 5000.00 | 15 | 750.00 | 0.00 | 4250.00 |

Cases 2 and 4 fail with `HALF_EVEN` (banker's rounding gives 1.54 and 4.51), so they protect the rounding rule.

## 2. Data

Two tables, both defined in `db/migration` (file `V2026_11_06_1000__vat_and_discount_tables.sql`; V1 is the base schema from #32, after V1 we use timestamp versions). They are part of the ERD "Country, currency, VAT, discounts" diagram.

| Table | Columns | Rules |
|---|---|---|
| `vat_rate` | `id`, `country_id` FK **unique**, `rate numeric(5,2)`, `updated_at` | one rate per country; 0 ≤ rate < 100 |
| `discount_threshold` | `id`, `country_id` FK, `min_amount numeric(12,2)`, `discount_rate numeric(5,2)` | unique (`country_id`, `min_amount`); `min_amount` > 0; 0 < rate ≤ 100 |

Why tables and not constants in code: the admin can change VAT and thresholds without a new release. Old orders are not affected, because the order keeps the rate and the amount that were used (`customer_order.vat_rate`, `customer_order.discount_rate`, ERD open question 1: answer is **yes, keep both**).

Lookups (Spring Data, derived queries):

```java
Optional<VatRate> findByCountryId(Long countryId);

Optional<DiscountThreshold>
    findFirstByCountryIdAndMinAmountLessThanEqualOrderByMinAmountDesc(Long countryId, BigDecimal amount);
```

The tiers are read with one small query per calculation. No cache for now: five rows per country, and a cache would delay the admin's change.

Thresholds are **per country and in the local currency**. The mentor said to use the technical task table; the Team Lead decided that the same numbers (100 / 500 / 700 / 1000 / 5000) apply in the local currency of each country, with no conversion ([Decision Log](https://github.com/ITPU-Teamwork-2026/t12-wok-and-roll/blob/dev/docs/decision-log.md)). So `discount_threshold.country_id` is `NOT NULL`. The ERD draft allowed `null` = "same for all"; that case is dropped, because 100 is not the same money in UZS and in CNY. `docs/erd.md` should say the same.

### How rates are stored

Rates are stored and sent as **percent**, never as a fraction: `15.00` means 15%, not `0.15`. The same format is used in:

- `vat_rate.rate` and `discount_threshold.discount_rate`: `numeric(5,2)`, for example `15.00`, `3.00`;
- `customer_order.vat_rate` and `customer_order.discount_rate` (the rate used for that order);
- the API (`vatRate`, `discountRate` in the breakdown) and the seed data from #33.

`PriceCalculator` divides by 100 once, when it turns a rate into an amount. Nobody else converts a rate.

## 3. Service design

Package `com.wokandroll.back.pricing`, layers as in the README. There is no public controller: pricing is called by `cart` (quote, #116) and `order` (create). The cart endpoint `POST /api/v1/cart/quote` returns the breakdown to the frontend.

Status (Sprint 3, #145): the whole formula is implemented: Subtotal, VAT, Amount incl. VAT, Discount, Delivery fee and Total.

```
pricing/
  service/
    PricingService         loads data, checks every line, calls the calculator
    PriceCalculator        pure math, no database, no Spring
    DeliveryZoneFees       port: fee of an active zone of the country (implemented on top of the delivery package, #147)
  repository/
    PricingRepository      interface: country + VAT rate, products, modifiers, discount tiers
    JdbcPricingRepository  plain SQL (JdbcClient), no entities
  dto/
    PricingRequest, PricingItem, Scenario, DiscountTier, PriceBreakdown, PricedLine, PricedModifier
cart/
  controller/CartController, service/CartService, dto/CartQuoteRequest, ...
```

The repository uses plain SQL on purpose: pricing only reads a few columns of tables that `catalog` and `country` own, so it does not copy their JPA entities and cannot clash with them.

Two classes on purpose. `PriceCalculator` is a pure function of numbers, so the shared QA test file can run against it in plain unit tests. `PricingService` only fetches data and checks input.

```java
// Sprint 3 (#145).
public record PricingRequest(
    String countryCode,
    List<PricingItem> items,        // productId, quantity, modifierIds
    Scenario scenario,              // PICKUP (default) or DELIVERY
    Long deliveryZoneId) {}         // required for DELIVERY, must be empty for PICKUP

public record PriceBreakdown(
    String country,
    String currency,
    Scenario scenario,
    List<PricedLine> lines,         // per line: unit price, quantity, line total, modifiers (id, name, type, delta)
    BigDecimal subtotal,
    BigDecimal vatRate,
    BigDecimal vat,
    BigDecimal amountInclVat,
    BigDecimal discountRate,        // percent
    BigDecimal discount,
    BigDecimal deliveryFee,         // 0.00 for pickup
    BigDecimal total) {}

PriceBreakdown PricingService.calculate(PricingRequest request);
```

Flow of `calculate`:

1. Validate: at least one line.
2. Find the country and its VAT rate. For `DELIVERY` take the fee of the active zone of this country from `DeliveryZoneFees` (no second zone query in pricing). A missing zone id is `VALIDATION_ERROR`, a zone id for `PICKUP` is `VALIDATION_ERROR`, an unknown, inactive or foreign zone is `UNKNOWN_DELIVERY_ZONE`. The zone problem is collected together with the line problems and comes first, so its code is the error code.
3. For each line load the regional price and the modifier deltas **from the database**, and check the line (see Errors). The request carries no prices, so a client cannot send its own.
4. Call `PriceCalculator` with the numbers, the VAT rate and the discount tiers of the country. The tier is chosen by Amount incl. VAT; the delivery fee is added last.
5. Return `PriceBreakdown`.

The order does not trust an earlier quote. On "place order" the order service calls `calculate` again and saves the breakdown it gets (ERD, point 3).

### Errors

Use `common.error` and the single format `{code, message, fields}`:

| Situation | Status and `code` | `fields` key |
|---|---|---|
| body is wrong: no country, no items, more than 50 items, `quantity` missing, not a whole number, below 1 or above 99 | 400 `VALIDATION_ERROR` (Bean Validation, #119) | `country`, `items`, `items[0].quantity` |
| unknown country code | 400 `UNKNOWN_COUNTRY` | `country` |
| product does not exist, or the product or its category is hidden | 400 `UNKNOWN_PRODUCT` | `items[0].productId` |
| product has no regional price in this country (not sold there) | 400 `PRODUCT_NOT_SOLD` | `items[0].productId` |
| product is out of stock in this country (`in_stock = false`) | 400 `OUT_OF_STOCK` | `items[0].productId` |
| modifier does not exist, is hidden, belongs to another product, has no price in this country, or is chosen twice in one line | 400 `INVALID_MODIFIER` | `items[0].modifierIds[0]` |
| the dish has sizes in this country but the line does not have exactly one SIZE modifier (none, or two or more); message `choose exactly one size` | 400 `INVALID_MODIFIER` | `items[0].modifierIds` (the whole list, not one item) |
| no `vat_rate` row for the country (data problem, not user error) | 500 `PRICING_CONFIG_MISSING` | none |

The request body is the cart, so a wrong product is a 400 and not a 404 (the URL is fine). The quantity is read as a decimal and checked to be a whole number: as an `Integer`, Jackson would silently turn 1.5 into 1.

Limits (Team Lead, PR #116): quantity 1 to 99 per line, at most 50 lines per cart. They are checked in the request validation and again in `PricingService`, so the order (#56) gets the same rules. The numbers are in one place, `PricingLimits`.

Sizes: a dish "has sizes" in a country if it has a visible modifier of type `SIZE` with a price there. Such a line needs exactly one SIZE modifier and any number of EXTRA modifiers. A size that is hidden or has no price in the country does not count, so a dish whose sizes cannot be ordered here needs no size. The size rule is checked after the other checks of the line: if a modifier is already wrong, the client fixes that first.

All wrong lines are collected and returned together in one 400, so the UI can show every problem at once. The `code` is the code of the first problem in the cart; `fields` lists all of them. Pricing does not check the stock of modifiers, because there is none (stock is per product and country).

## 4. Tests

- Unit tests on `PriceCalculator` and, through the whole service, on `PricingService`: all examples of the shared file `../wok-and-roll-qa/test-data/pricing-examples.json` (36 cases). Every step is checked, from Subtotal to Total, including the delivery fee. `PriceCalculatorDiscountTest` also covers the threshold edges (99.99 / 100.00 … 4999.99 / 5000.00) for the 5 countries.
- `PricingService` tests with an in-memory repository (`FakePricingRepository`, no database): modifiers, exactly one size, limits (99 and 50), hidden / unknown / not sold / out of stock, several problems at once, missing VAT row, pickup and delivery totals, the 15 % tier, unknown / foreign zone.
- `CartController` tests with MockMvc: JSON, two decimals in the response, the error format.

## 5. Open items

| # | Item | Status |
|---|---|---|
| 1 | Rounding rule | **Closed.** Team Lead decision: half-up, 2 decimals, after each step. See the [Decision Log](https://github.com/ITPU-Teamwork-2026/t12-wok-and-roll/blob/dev/docs/decision-log.md) |
| 2 | Threshold currency | **Closed.** Local currency of each country, same numbers, no conversion, stored per country. See the [Decision Log](https://github.com/ITPU-Teamwork-2026/t12-wok-and-roll/blob/dev/docs/decision-log.md) |
| 3 | Delivery fee | **Closed.** Fixed fee per delivery zone, local currency, added last. **Done** in #145: the fee is read from the active `delivery_zone` of the country (#147). The client sends only `scenario` and `deliveryZoneId` |
| 4 | Seed data | **Done** in #33: VAT rate and 5 threshold rows per country, in percent. Seed data is the source of truth for prices. It has **no modifiers yet**, so a quote with `modifierIds` can only be tried by hand after #52 |
| 5 | QA test-data format | **Done** in #42: `wok-and-roll-qa/test-data/pricing-examples.json`, used by the backend tests |

Also closed here: ERD question 1 (keep rates in the order: yes) and question 5 (percent, not fraction: percent, also in `customer_order`).

## Definition of Done

- [ ] This design approved by the Team Lead
- [x] Rounding and threshold currency closed with a link to the Decision Log
- [ ] Migration merged together with the seed from #33
- [ ] `docs/erd.md`: `discount_threshold.country_id` is not null