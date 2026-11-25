# Test cases: country switch

Story #50 · Task #107 · Asif Najafov (QA Manual) · v0.2 · 14 November 2026

One test case (or more) for each acceptance criterion of #50. AC numbers follow the order of the list in #50.

## Before the test

- Build from `dev`, backend is running, seed data for the 5 countries is loaded.
- Browsers: Chrome and Firefox. Widths: 320, 768 and 1200 px.
- "Clean start" = Local Storage of the site is cleared (DevTools → Application → Local Storage → Clear), then reload.
- Expected VAT and currency come from `GET /api/v1/countries` and from `wok-and-roll-qa/test-data/pricing-examples.json`:

| Country | Code | Currency | VAT |
|---|---|---|---|
| Uzbekistan | UZB | UZS | 15% |
| Kazakhstan | KAZ | KZT | 20% |
| Georgia | GEO | GEL | 12% |
| Ukraine | UKR | UAH | 8% |
| China | CHN | CNY | 18% |

## Test cases

| ID | AC | Title | Steps | Expected result |
|---|---|---|---|---|
| TC-50-01 | 1 | Switcher is in the header | 1. Clean start. 2. Open Home. 3. Look at the header. 4. Open the switcher. | The switcher is visible in the header without scrolling. It has exactly 5 countries: UZB, KAZ, GEO, UKR, CHN. |
| TC-50-02 | 1 | Switcher is on every page | Open Menu, Cart, Checkout and Reports. | The switcher is in the header on every page and shows the same country. |
| TC-50-03 | 7 | First visit is UZB | 1. Clean start. 2. Open Home. 3. Open Menu. | The switcher shows UZB. Prices are in UZS. The requests go with `?country=UZB` (DevTools → Network). |
| TC-50-04 | 2 | Menu updates without reload | 1. Open Menu with UZB. 2. Remember the price of the first product. 3. Open DevTools → Network. 4. Choose KAZ. | The prices and the currency change to KZT at once. The page does not reload (no new document request in Network, the scroll position stays). New requests go with `?country=KAZ`. |
| TC-50-05 | 2 | Cart updates without reload | 1. UZB, add one product to the cart. 2. Open Cart. 3. Choose GEO. | Currency, prices, VAT rate, Subtotal, VAT and Amount incl. VAT in the cart change to GEL and 12% without a page reload. No UZS is left anywhere on the page. |
| TC-50-06 | 2, 6 | Every country shows its own currency and VAT | 1. Add one product to the cart. 2. Open Cart. 3. Choose every country one by one. | For each country the currency and the VAT rate are the same as in the table above. The numbers on the page are the same as in the API response. |
| TC-50-07 | 3 | Choice is saved after reload | 1. Choose UKR. 2. Reload the page (F5). | The switcher shows UKR. Prices are in UAH. There is no moment when UZB prices are shown first. |
| TC-50-08 | 3 | Choice is saved when I come back | 1. Choose CHN. 2. Close the tab. 3. Open the site again in a new tab. | The switcher shows CHN. Prices are in CNY. |
| TC-50-09 | 3 | Choice is kept between pages | 1. Choose KAZ on Home. 2. Go to Menu, then Cart, then back to Home. | KAZ stays selected on every page. |
| TC-50-10 | 4 | Prices have 2 decimals | 1. Check the prices on Home, Menu, the product window and Cart. 2. Repeat for all 5 countries. | Every price has exactly 2 decimals and the sign of the currency, never its code (examples: `2 190,00 ₸` in Kazakh, `₴259.00` in English; sum has no sign: `55 000,00 soʻm`). The format is the same on all pages. A price like `50` or `50.5` is a bug. |
| TC-50-11 | 5 | Keyboard only | 1. Reload. 2. Press Tab until the switcher is in focus. 3. Open it with Enter or Space. 4. Choose a country with the arrow keys and Enter. 5. Open it again and press Esc. | The focus ring is visible on the switcher. The country changes with the keyboard only. Esc closes the list and does not change the country. The focus stays on the switcher, there is no keyboard trap. |
| TC-50-12 | 5 | Width 320 px | 1. Set the width to 320. 2. Open Home and Menu. 3. Change the country. | No horizontal scroll. The switcher is visible and can be used (in the header or in the burger menu). The country name or code is not cut. |
| TC-50-13 | 5 | Width 768 and 1200 px | Repeat TC-50-12 at 768 and at 1200. | The same result. The header is not broken, nothing covers the switcher. |
| TC-50-14 | 6 | **Stage 3 (#55)** Discount levels are in the local currency | 1. Choose KAZ. 2. Build a cart with Amount incl. VAT from 100.00 to 499.99 KZT. 3. Repeat in one more country. | The discount is 3% in every country, with no conversion between currencies. The numbers follow the examples `KAZ-threshold-100`, `GEO-threshold-100` and so on in `pricing-examples.json`. |
| TC-50-15 | 8 | Cart keeps items after the country change | 1. UZB, add product A (quantity 2) and product B (quantity 1). 2. Open Cart. 3. Choose KAZ. | The cart still has A × 2 and B × 1. The prices, Subtotal, VAT (20%) and Amount incl. VAT are in KZT and are the same as in the answer of `POST /api/v1/cart/quote` for KAZ. |
| TC-50-16 | 8, 3 | Cart and country after reload | 1. After TC-50-15, reload the page. | The country is KAZ, the cart has the same items and the same KZT Subtotal, VAT and Amount incl. VAT. |
| TC-50-17 | 3 | Broken saved value | 1. In Local Storage change the saved country to `XXX`. 2. Reload. | The page does not crash. The country is UZB (the default). |
| TC-50-18 | 2 | Countries API does not answer | 1. Stop the backend or block `/api/v1/countries` in DevTools. 2. Reload. | The page does not crash. The guest sees a clear error message, not an empty switcher. |

## Notes

- Sprint 2: the cart shows only Subtotal, VAT and Amount incl. VAT. Discount and Total come in Stage 3 (#55), so TC-50-14 is marked Stage 3. TC-50-15 (AC 8) is a Sprint 2 test case.

- TC-50-17 and TC-50-18 are not in the acceptance criteria. They are edge cases from my checklists in `test-plan.md` (section 4).
- Bugs go in as sub-issues of #50 with the severity and the developer assigned.
