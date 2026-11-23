# Shared test data

One source of truth for expected numbers. Backend unit tests, Playwright tests and the manual price matrix all read **the same file**.

- `pricing-examples.json`: input (country, items, scenario, delivery fee) → expected Subtotal, VAT, Amount incl. VAT, Discount, Delivery fee, Total. Task: [#42](https://github.com/ITPU-Teamwork-2026/t12-wok-and-roll/issues/42) (Khumoyunmirzo + Asif, with Timur).

First example from the Project Plan (UZB, pickup): Subtotal 400.00 → VAT 60.00 → 460.00 → discount 3% 13.80 → **Total 446.20**. With delivery fee 20.00 → **466.20**.

Change this file only with a PR reviewed by QA **and** Timur, because it changes what "correct" means.
