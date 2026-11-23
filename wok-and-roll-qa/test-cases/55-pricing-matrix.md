# Test cases: pricing matrix (country × amount × pickup and delivery)

Story #55 · Task #162 · Asif Najafov (QA Manual) · v0.2 · 19 November 2026

The matrix checks every country at every discount range, for pickup and for delivery. Each row is one cart and the exact numbers the page must show.

## Before the test

- Build from `dev` with #145 (discount, delivery fee, total) and #156 (totals block) merged. Backend and database are running, seed data for the 5 countries is loaded. Run in Chrome at 1200 px. Run again at 320 and 768 px for one row per country.
- The numbers are calculated by the backend only (`BigDecimal`, HALF_UP to 2 decimals after each step). The page must not calculate them. Formula (`docs/api-contract.md`, section 5):
  - VAT = round(Subtotal × VAT rate / 100); Amount incl. VAT = Subtotal + VAT
  - Discount rate = rate of the highest threshold that is not more than Amount incl. VAT: 100 → 3 %, 500 → 5 %, 700 → 7 %, 1000 → 10 %, 5000 → 15 %. Below 100 → 0 %.
  - Discount = round(Amount incl. VAT × rate / 100)
  - Total = Amount incl. VAT − Discount + Delivery fee. The delivery fee is added last. It does not change the discount.
- VAT rates: UZB 15 %, KAZ 20 %, GEO 12 %, UKR 8 %, CHN 18 %. Currencies: UZS, KZT, GEL, UAH, CNY. Delivery fee: 20.00 (the zone with fee 20.00 of the country, see `GET /delivery-zones`). Pickup fee: 0.00.
- Expected numbers: I calculated them with the formula and checked the same formula on all 36 examples of `wok-and-roll-qa/test-data/pricing-examples.json` (0 differences). The carts below are built from the real seed dishes, because the examples of the file use amounts such as 86.96 that cannot be made from seed dishes in the UI. Those examples are covered on the API by #163 and #164.
- Seed prices for the carts come from `GET /api/v1/products?country=...` (dish without modifiers, Tuna nigiri is out of stock and not used).
- How to build a cart: add the dishes in the "Cart" column with the quantities, open Cart, then Checkout. Choose Pickup, or Delivery with the zone with fee 20.00.
- Firecracker udon and Miso mushroom rice must have a size. Use the first size, Regular (price +0.00). It is selected by default in the product window. In the quote API send `modifierIds` with it (udon `[1]`, miso rice `[5]`). Without it the API answers 400 `INVALID_MODIFIER`.
- How to read the page: every amount has 2 decimals and the currency code of the country, for example `UZS 446.20`.
- If a number is different: write the actual numbers and the request id of the answer (`X-Request-Id` of the quote or the order, from Wednesday) in the Actual column, and open a bug under #55 (severity, steps, expected, actual, request id).

## Matrix: every country × range × pickup and delivery

"Range" is the discount range that the cart is built for. The cart is the nearest one above the threshold that can be made from seed dishes ("100+" means Amount incl. VAT is just over 100).

| ID | Country | Range | Scenario | Cart | Subtotal | VAT | Amount incl. VAT | Disc. % | Discount | Fee | Total | Actual (numbers, request id) |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| TC-55-01 | UZB | below 100 | Pickup | 1 × Salmon avocado roll | 62.00 | 9.30 | 71.30 | 0 | 0.00 | 0.00 | **71.30** | Pass, API, 19 Nov |
| TC-55-02 | UZB | below 100 | Delivery | 1 × Salmon avocado roll | 62.00 | 9.30 | 71.30 | 0 | 0.00 | 20.00 | **91.30** | Pass, API, 19 Nov |
| TC-55-03 | UZB | 100+ | Pickup | 1 × California roll + 1 × Gyoza | 87.00 | 13.05 | 100.05 | 3 | 3.00 | 0.00 | **97.05** | Pass, API, 19 Nov |
| TC-55-04 | UZB | 100+ | Delivery | 1 × California roll + 1 × Gyoza | 87.00 | 13.05 | 100.05 | 3 | 3.00 | 20.00 | **117.05** | Pass, API, 19 Nov |
| TC-55-05 | UZB | 500+ | Pickup | 3 × Wok & Roll set for two + 1 × Firecracker udon | 435.00 | 65.25 | 500.25 | 5 | 25.01 | 0.00 | **475.24** | Pass, API, 19 Nov |
| TC-55-06 | UZB | 500+ | Delivery | 3 × Wok & Roll set for two + 1 × Firecracker udon | 435.00 | 65.25 | 500.25 | 5 | 25.01 | 20.00 | **495.24** | Pass, API, 19 Nov |
| TC-55-07 | UZB | 700+ | Pickup | 1 × Wok & Roll set for two + 10 × Firecracker udon | 609.00 | 91.35 | 700.35 | 7 | 49.02 | 0.00 | **651.33** | Pass, API, 19 Nov |
| TC-55-08 | UZB | 700+ | Delivery | 1 × Wok & Roll set for two + 10 × Firecracker udon | 609.00 | 91.35 | 700.35 | 7 | 49.02 | 20.00 | **671.33** | Pass, API, 19 Nov |
| TC-55-09 | UZB | 1000+ | Pickup | 6 × Wok & Roll set for two + 2 × Firecracker udon | 870.00 | 130.50 | 1000.50 | 10 | 100.05 | 0.00 | **900.45** | Pass, API, 19 Nov |
| TC-55-10 | UZB | 1000+ | Delivery | 6 × Wok & Roll set for two + 2 × Firecracker udon | 870.00 | 130.50 | 1000.50 | 10 | 100.05 | 20.00 | **920.45** | Pass, API, 19 Nov |
| TC-55-11 | UZB | 5000+ | Pickup | 4 × California roll + 32 × Wok & Roll set for two | 4348.00 | 652.20 | 5000.20 | 15 | 750.03 | 0.00 | **4250.17** | Pass, API, 19 Nov |
| TC-55-12 | UZB | 5000+ | Delivery | 4 × California roll + 32 × Wok & Roll set for two | 4348.00 | 652.20 | 5000.20 | 15 | 750.03 | 20.00 | **4270.17** | Pass, API, 19 Nov |
| TC-55-13 | KAZ | below 100 | Pickup | 1 × Salmon avocado roll | 67.00 | 13.40 | 80.40 | 0 | 0.00 | 0.00 | **80.40** | Pass, API, 19 Nov |
| TC-55-14 | KAZ | below 100 | Delivery | 1 × Salmon avocado roll | 67.00 | 13.40 | 80.40 | 0 | 0.00 | 20.00 | **100.40** | Pass, API, 19 Nov |
| TC-55-15 | KAZ | 100+ | Pickup | 1 × California roll + 1 × Edamame | 85.50 | 17.10 | 102.60 | 3 | 3.08 | 0.00 | **99.52** | Pass, API, 19 Nov |
| TC-55-16 | KAZ | 100+ | Delivery | 1 × California roll + 1 × Edamame | 85.50 | 17.10 | 102.60 | 3 | 3.08 | 20.00 | **119.52** | Pass, API, 19 Nov |
| TC-55-17 | KAZ | 500+ | Pickup | 2 × Wok & Roll set for two + 4 × Gyoza | 417.00 | 83.40 | 500.40 | 5 | 25.02 | 0.00 | **475.38** | Pass, API, 19 Nov |
| TC-55-18 | KAZ | 500+ | Delivery | 2 × Wok & Roll set for two + 4 × Gyoza | 417.00 | 83.40 | 500.40 | 5 | 25.02 | 20.00 | **495.38** | Pass, API, 19 Nov |
| TC-55-19 | KAZ | 700+ | Pickup | 8 × Salmon avocado roll + 1 × Miso mushroom rice | 583.50 | 116.70 | 700.20 | 7 | 49.01 | 0.00 | **651.19** | Pass, API, 19 Nov |
| TC-55-20 | KAZ | 700+ | Delivery | 8 × Salmon avocado roll + 1 × Miso mushroom rice | 583.50 | 116.70 | 700.20 | 7 | 49.01 | 20.00 | **671.19** | Pass, API, 19 Nov |
| TC-55-21 | KAZ | 1000+ | Pickup | 8 × Salmon avocado roll + 5 × California roll | 833.50 | 166.70 | 1000.20 | 10 | 100.02 | 0.00 | **900.18** | Pass, API, 19 Nov |
| TC-55-22 | KAZ | 1000+ | Delivery | 8 × Salmon avocado roll + 5 × California roll | 833.50 | 166.70 | 1000.20 | 10 | 100.02 | 20.00 | **920.18** | Pass, API, 19 Nov |
| TC-55-23 | KAZ | 5000+ | Pickup | 53 × Salmon avocado roll + 11 × Teriyaki chicken noodles | 4167.00 | 833.40 | 5000.40 | 15 | 750.06 | 0.00 | **4250.34** | Pass, API, 19 Nov |
| TC-55-24 | KAZ | 5000+ | Delivery | 53 × Salmon avocado roll + 11 × Teriyaki chicken noodles | 4167.00 | 833.40 | 5000.40 | 15 | 750.06 | 20.00 | **4270.34** | Pass, API, 19 Nov |
| TC-55-25 | GEO | below 100 | Pickup | 1 × Salmon avocado roll | 58.50 | 7.02 | 65.52 | 0 | 0.00 | 0.00 | **65.52** | Pass, API, 19 Nov |
| TC-55-26 | GEO | below 100 | Delivery | 1 × Salmon avocado roll | 58.50 | 7.02 | 65.52 | 0 | 0.00 | 20.00 | **85.52** | Pass, API, 19 Nov |
| TC-55-27 | GEO | 100+ | Pickup | 2 × Firecracker udon | 90.00 | 10.80 | 100.80 | 3 | 3.02 | 0.00 | **97.78** | Pass, API, 19 Nov |
| TC-55-28 | GEO | 100+ | Delivery | 2 × Firecracker udon | 90.00 | 10.80 | 100.80 | 3 | 3.02 | 20.00 | **117.78** | Pass, API, 19 Nov |
| TC-55-29 | GEO | 500+ | Pickup | 9 × Firecracker udon + 1 × Miso mushroom rice | 446.50 | 53.58 | 500.08 | 5 | 25.00 | 0.00 | **475.08** | Pass, API, 19 Nov |
| TC-55-30 | GEO | 500+ | Delivery | 9 × Firecracker udon + 1 × Miso mushroom rice | 446.50 | 53.58 | 500.08 | 5 | 25.00 | 20.00 | **495.08** | Pass, API, 19 Nov |
| TC-55-31 | GEO | 700+ | Pickup | 1 × Salmon avocado roll + 11 × California roll | 625.00 | 75.00 | 700.00 | 7 | 49.00 | 0.00 | **651.00** | Pass, API, 19 Nov |
| TC-55-32 | GEO | 700+ | Delivery | 1 × Salmon avocado roll + 11 × California roll | 625.00 | 75.00 | 700.00 | 7 | 49.00 | 20.00 | **671.00** | Pass, API, 19 Nov |
| TC-55-33 | GEO | 1000+ | Pickup | 17 × Teriyaki chicken noodles + 2 × Gyoza | 893.00 | 107.16 | 1000.16 | 10 | 100.02 | 0.00 | **900.14** | Pass, API, 19 Nov |
| TC-55-34 | GEO | 1000+ | Delivery | 17 × Teriyaki chicken noodles + 2 × Gyoza | 893.00 | 107.16 | 1000.16 | 10 | 100.02 | 20.00 | **920.14** | Pass, API, 19 Nov |
| TC-55-35 | GEO | 5000+ | Pickup | 19 × Wok & Roll set for two + 44 × Teriyaki chicken noodles | 4464.50 | 535.74 | 5000.24 | 15 | 750.04 | 0.00 | **4250.20** | Pass, API, 19 Nov |
| TC-55-36 | GEO | 5000+ | Delivery | 19 × Wok & Roll set for two + 44 × Teriyaki chicken noodles | 4464.50 | 535.74 | 5000.24 | 15 | 750.04 | 20.00 | **4270.20** | Pass, API, 19 Nov |
| TC-55-37 | UKR | below 100 | Pickup | 1 × Salmon avocado roll | 54.50 | 4.36 | 58.86 | 0 | 0.00 | 0.00 | **58.86** | Pass, API, 19 Nov |
| TC-55-38 | UKR | below 100 | Delivery | 1 × Salmon avocado roll | 54.50 | 4.36 | 58.86 | 0 | 0.00 | 20.00 | **78.86** | Pass, API, 19 Nov |
| TC-55-39 | UKR | 100+ | Pickup | 1 × Salmon avocado roll + 1 × Miso mushroom rice | 93.00 | 7.44 | 100.44 | 3 | 3.01 | 0.00 | **97.43** | Pass, API, 19 Nov |
| TC-55-40 | UKR | 100+ | Delivery | 1 × Salmon avocado roll + 1 × Miso mushroom rice | 93.00 | 7.44 | 100.44 | 3 | 3.01 | 20.00 | **117.43** | Pass, API, 19 Nov |
| TC-55-41 | UKR | 500+ | Pickup | 4 × California roll + 7 × Miso mushroom rice | 463.50 | 37.08 | 500.58 | 5 | 25.03 | 0.00 | **475.55** | Pass, API, 19 Nov |
| TC-55-42 | UKR | 500+ | Delivery | 4 × California roll + 7 × Miso mushroom rice | 463.50 | 37.08 | 500.58 | 5 | 25.03 | 20.00 | **495.55** | Pass, API, 19 Nov |
| TC-55-43 | UKR | 700+ | Pickup | 3 × Wok & Roll set for two + 8 × Miso mushroom rice | 648.50 | 51.88 | 700.38 | 7 | 49.03 | 0.00 | **651.35** | Pass, API, 19 Nov |
| TC-55-44 | UKR | 700+ | Delivery | 3 × Wok & Roll set for two + 8 × Miso mushroom rice | 648.50 | 51.88 | 700.38 | 7 | 49.03 | 20.00 | **671.35** | Pass, API, 19 Nov |
| TC-55-45 | UKR | 1000+ | Pickup | 10 × Firecracker udon + 11 × Teriyaki chicken noodles | 926.00 | 74.08 | 1000.08 | 10 | 100.01 | 0.00 | **900.07** | Pass, API, 19 Nov |
| TC-55-46 | UKR | 1000+ | Delivery | 10 × Firecracker udon + 11 × Teriyaki chicken noodles | 926.00 | 74.08 | 1000.08 | 10 | 100.01 | 20.00 | **920.07** | Pass, API, 19 Nov |
| TC-55-47 | UKR | 5000+ | Pickup | 51 × California roll + 19 × Wok & Roll set for two | 4630.00 | 370.40 | 5000.40 | 15 | 750.06 | 0.00 | **4250.34** | Pass, API, 19 Nov |
| TC-55-48 | UKR | 5000+ | Delivery | 51 × California roll + 19 × Wok & Roll set for two | 4630.00 | 370.40 | 5000.40 | 15 | 750.06 | 20.00 | **4270.34** | Pass, API, 19 Nov |
| TC-55-49 | CHN | below 100 | Pickup | 1 × Salmon avocado roll | 69.50 | 12.51 | 82.01 | 0 | 0.00 | 0.00 | **82.01** | Pass, API, 19 Nov |
| TC-55-50 | CHN | below 100 | Delivery | 1 × Salmon avocado roll | 69.50 | 12.51 | 82.01 | 0 | 0.00 | 20.00 | **102.01** | Pass, API, 19 Nov |
| TC-55-51 | CHN | 100+ | Pickup | 1 × Teriyaki chicken noodles + 1 × Edamame | 85.00 | 15.30 | 100.30 | 3 | 3.01 | 0.00 | **97.29** | Pass, API, 19 Nov |
| TC-55-52 | CHN | 100+ | Delivery | 1 × Teriyaki chicken noodles + 1 × Edamame | 85.00 | 15.30 | 100.30 | 3 | 3.01 | 20.00 | **117.29** | Pass, API, 19 Nov |
| TC-55-53 | CHN | 500+ | Pickup | 2 × Wok & Roll set for two + 5 × Edamame | 424.00 | 76.32 | 500.32 | 5 | 25.02 | 0.00 | **475.30** | Pass, API, 19 Nov |
| TC-55-54 | CHN | 500+ | Delivery | 2 × Wok & Roll set for two + 5 × Edamame | 424.00 | 76.32 | 500.32 | 5 | 25.02 | 20.00 | **495.30** | Pass, API, 19 Nov |
| TC-55-55 | CHN | 700+ | Pickup | 5 × Salmon avocado roll + 4 × California roll | 593.50 | 106.83 | 700.33 | 7 | 49.02 | 0.00 | **651.31** | Pass, API, 19 Nov |
| TC-55-56 | CHN | 700+ | Delivery | 5 × Salmon avocado roll + 4 × California roll | 593.50 | 106.83 | 700.33 | 7 | 49.02 | 20.00 | **671.31** | Pass, API, 19 Nov |
| TC-55-57 | CHN | 1000+ | Pickup | 6 × Salmon avocado roll + 7 × California roll | 847.50 | 152.55 | 1000.05 | 10 | 100.01 | 0.00 | **900.04** | Pass, API, 19 Nov |
| TC-55-58 | CHN | 1000+ | Delivery | 6 × Salmon avocado roll + 7 × California roll | 847.50 | 152.55 | 1000.05 | 10 | 100.01 | 20.00 | **920.04** | Pass, API, 19 Nov |
| TC-55-59 | CHN | 5000+ | Pickup | 30 × Salmon avocado roll + 35 × California roll | 4237.50 | 762.75 | 5000.25 | 15 | 750.04 | 0.00 | **4250.21** | Pass, API, 19 Nov |
| TC-55-60 | CHN | 5000+ | Delivery | 30 × Salmon avocado roll + 35 × California roll | 4237.50 | 762.75 | 5000.25 | 15 | 750.04 | 20.00 | **4270.21** | Pass, API, 19 Nov |

## Threshold edge: the delivery fee does not lift the discount

Each cart is just **below** a threshold. With delivery, the fee (20.00) must not move it into the next range. The Discount % must stay the lower one.

| ID | Country | Threshold | Scenario | Cart | Subtotal | VAT | Amount incl. VAT | Disc. % | Discount | Fee | Total | Actual (numbers, request id) |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| TC-55-E01 | UZB | below 100 | Delivery | 1 × Salmon avocado roll + 1 × Edamame | 86.00 | 12.90 | 98.90 | 0 | 0.00 | 20.00 | **118.90** | Pass, API, 19 Nov |
| TC-55-E02 | UZB | below 500 | Delivery | 2 × Wok & Roll set for two + 4 × Miso mushroom rice | 434.00 | 65.10 | 499.10 | 3 | 14.97 | 20.00 | **504.13** | Pass, API, 19 Nov |
| TC-55-E03 | UZB | below 700 | Delivery | 4 × Firecracker udon + 8 × Teriyaki chicken noodles | 608.00 | 91.20 | 699.20 | 5 | 34.96 | 20.00 | **684.24** | Pass, API, 19 Nov |
| TC-55-E04 | UZB | below 1000 | Delivery | 5 × Wok & Roll set for two + 7 × Gyoza | 869.00 | 130.35 | 999.35 | 7 | 69.95 | 20.00 | **949.40** | Pass, API, 19 Nov |
| TC-55-E05 | UZB | below 5000 | Delivery | 27 × Wok & Roll set for two + 18 × Firecracker udon | 4347.00 | 652.05 | 4999.05 | 10 | 499.91 | 20.00 | **4519.14** | Pass, API, 19 Nov |
| TC-55-E06 | KAZ | below 100 | Delivery | 1 × Miso mushroom rice + 1 × Gyoza | 82.00 | 16.40 | 98.40 | 0 | 0.00 | 20.00 | **118.40** | Pass, API, 19 Nov |
| TC-55-E07 | KAZ | below 500 | Delivery | 7 × California roll | 416.50 | 83.30 | 499.80 | 3 | 14.99 | 20.00 | **504.81** | Pass, API, 19 Nov |
| TC-55-E08 | KAZ | below 700 | Delivery | 9 × California roll + 1 × Miso mushroom rice | 583.00 | 116.60 | 699.60 | 5 | 34.98 | 20.00 | **684.62** | Pass, API, 19 Nov |
| TC-55-E09 | KAZ | below 1000 | Delivery | 14 × California roll | 833.00 | 166.60 | 999.60 | 7 | 69.97 | 20.00 | **949.63** | Pass, API, 19 Nov |
| TC-55-E10 | KAZ | below 5000 | Delivery | 11 × Wok & Roll set for two + 47 × Teriyaki chicken noodles | 4166.50 | 833.30 | 4999.80 | 10 | 499.98 | 20.00 | **4519.82** | Pass, API, 19 Nov |
| TC-55-E11 | GEO | below 100 | Delivery | 1 × Salmon avocado roll + 1 × Gyoza | 88.50 | 10.62 | 99.12 | 0 | 0.00 | 20.00 | **119.12** | Pass, API, 19 Nov |
| TC-55-E12 | GEO | below 500 | Delivery | 2 × California roll + 7 × Teriyaki chicken noodles | 446.00 | 53.52 | 499.52 | 3 | 14.99 | 20.00 | **504.53** | Pass, API, 19 Nov |
| TC-55-E13 | GEO | below 700 | Delivery | 9 × Salmon avocado roll + 2 × Teriyaki chicken noodles | 624.50 | 74.94 | 699.44 | 5 | 34.97 | 20.00 | **684.47** | Pass, API, 19 Nov |
| TC-55-E14 | GEO | below 1000 | Delivery | 11 × Salmon avocado roll + 6 × Miso mushroom rice | 892.50 | 107.10 | 999.60 | 7 | 69.97 | 20.00 | **949.63** | Pass, API, 19 Nov |
| TC-55-E15 | GEO | below 5000 | Delivery | 36 × Wok & Roll set for two + 2 × Firecracker udon | 4464.00 | 535.68 | 4999.68 | 10 | 499.97 | 20.00 | **4519.71** | Pass, API, 19 Nov |
| TC-55-E16 | UKR | below 100 | Delivery | 2 × Teriyaki chicken noodles | 92.00 | 7.36 | 99.36 | 0 | 0.00 | 20.00 | **119.36** | Pass, API, 19 Nov |
| TC-55-E17 | UKR | below 500 | Delivery | 1 × California roll + 9 × Teriyaki chicken noodles | 462.50 | 37.00 | 499.50 | 3 | 14.99 | 20.00 | **504.51** | Pass, API, 19 Nov |
| TC-55-E18 | UKR | below 700 | Delivery | 4 × California roll + 4 × Wok & Roll set for two | 648.00 | 51.84 | 699.84 | 5 | 34.99 | 20.00 | **684.85** | Pass, API, 19 Nov |
| TC-55-E19 | UKR | below 1000 | Delivery | 4 × California roll + 19 × Miso mushroom rice | 925.50 | 74.04 | 999.54 | 7 | 69.97 | 20.00 | **949.57** | Pass, API, 19 Nov |
| TC-55-E20 | UKR | below 5000 | Delivery | 36 × Salmon avocado roll + 55 × California roll | 4629.50 | 370.36 | 4999.86 | 10 | 499.99 | 20.00 | **4519.87** | Pass, API, 19 Nov |
| TC-55-E21 | CHN | below 100 | Delivery | 1 × Firecracker udon + 1 × Edamame | 81.00 | 14.58 | 95.58 | 0 | 0.00 | 20.00 | **115.58** | Pass, API, 19 Nov |
| TC-55-E22 | CHN | below 500 | Delivery | 5 × California roll + 2 × Teriyaki chicken noodles | 423.50 | 76.23 | 499.73 | 3 | 14.99 | 20.00 | **504.74** | Pass, API, 19 Nov |
| TC-55-E23 | CHN | below 700 | Delivery | 7 × California roll + 3 × Firecracker udon | 592.50 | 106.65 | 699.15 | 5 | 34.96 | 20.00 | **684.19** | Pass, API, 19 Nov |
| TC-55-E24 | CHN | below 1000 | Delivery | 10 × California roll + 4 × Teriyaki chicken noodles | 847.00 | 152.46 | 999.46 | 7 | 69.96 | 20.00 | **949.50** | Pass, API, 19 Nov |
| TC-55-E25 | CHN | below 5000 | Delivery | 36 × California roll + 14 × Wok & Roll set for two | 4237.00 | 762.66 | 4999.66 | 10 | 499.97 | 20.00 | **4519.69** | Pass, API, 19 Nov |

## Rounding: HALF_UP

The raw value ends in 5 at the third decimal. It must be rounded up (HALF_UP), not down and not to even. Pickup.

| ID | Country | Rounded step | Cart | Subtotal | VAT | Amount incl. VAT | Disc. % | Raw discount | Discount | Total | Actual (numbers, request id) |
|---|---|---|---|---|---|---|---|---|---|---|---|
| TC-55-R01 | UZB | discount | 5 × Salmon avocado roll | 310.00 | 46.50 | 356.50 | 3 | 10.695 | 10.70 | **345.80** | Pass, API, 19 Nov |
| TC-55-R02 | UKR | discount | 5 × Wok & Roll set for two | 567.50 | 45.40 | 612.90 | 5 | 30.645 | 30.65 | **582.25** | Pass, API, 19 Nov |
| TC-55-R03 | CHN | discount | 2 × Salmon avocado roll + 1 × Gyoza | 175.00 | 31.50 | 206.50 | 3 | 6.195 | 6.20 | **200.30** | Pass, API, 19 Nov |

The VAT rounding (for example UZB 10.30 → VAT 1.545 → 1.55) needs prices that are not in the seed. It is checked by the examples in `pricing-examples.json` on the API and by the unit tests of `PriceCalculator`.

## Other checks on the same screen

| ID | Title | Steps | Expected result |
|---|---|---|---|
| TC-55-X01 | Same numbers in cart, checkout and success page | Take one row of the matrix. Compare the cart, the checkout summary and the success page. | All six totals are the same on the three pages and the same as the quote answer in the Network tab. |
| TC-55-X02 | Change country with the same dishes | Fill a cart. Change the country in the header 5 times. | Prices, VAT rate, currency, discount and total are recalculated each time and match the matrix row of that country. No old number stays on the page. |
| TC-55-X03 | Change scenario | Choose Pickup, then Delivery, then Pickup. | Only the Delivery fee line and the Total change by the fee. Subtotal, VAT, Amount incl. VAT and Discount stay the same. |
| TC-55-X04 | Change quantity near a threshold | Take a TC-55-E row. Add one dish. | The discount goes up to the next range only when Amount incl. VAT reaches the threshold. The Total is requoted. |
| TC-55-X05 | Format | Look at 5 rows of different countries. | Every amount has exactly 2 decimals and the currency code (`UZS 446.20`). No `NaN`, no 3 decimals, no sign problems. |
| TC-55-X06 | Layout of the totals block | Open one row per country at 320, 768 and 1200 px. | The totals block is the same as in Figma. Amounts are not cut and do not wrap badly. No horizontal scroll. |

## Run log

Run 1: Thursday 19 November 2026, `dev` at commit 1dbd839 (#206 quote totals is in, #156 totals block UI is not merged yet). Run on the API: 88 `POST /api/v1/cart/quote` requests from the browser (`scenario`, `deliveryZoneId` of the zone with fee 20.00, items with the quantities of each row). Every field was compared with the table: Subtotal, VAT, Amount incl. VAT, Discount rate, Discount, Delivery fee, Total.

| Part | Rows | Pass | Fail |
|---|---|---|---|
| Matrix (5 countries × 6 ranges × pickup and delivery) | 60 | 60 | 0 |
| Threshold edge (delivery fee does not lift the discount) | 25 | 25 | 0 |
| Rounding HALF_UP | 3 | 3 | 0 |
| Total | 88 | 88 | 0 |

- No difference in any number. The API follows the formula (VAT, discount, HALF_UP, fee added last).
- The response has no `X-Request-Id` header yet (#152 is not on `dev`). Request ids will be written here after it is merged.
- Still to do: the same rows on the UI (cart, checkout, success page) after #156 and #157 are merged, one row per country at 320 and 768 px, and TC-55-X01 to TC-55-X06. Fill the table below only for rows that fail.

| ID | Expected | Actual | Request id | Bug |
|---|---|---|---|---|
| | | | | |
