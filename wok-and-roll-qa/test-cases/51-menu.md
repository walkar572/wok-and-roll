# Test cases: menu

Story #51 · Task #111 · Asif Najafov (QA Manual) · v0.3 · 14 November 2026

One test case (or more) for each acceptance criterion of #51. AC numbers follow the order of the list in #51:

1. Country selected → categories and product cards for that country
2. Card shows name, description, photo, regional price in local currency, "in stock" status
3. Out of stock product cannot be added to the cart
4. Loading, empty and error states follow the design system
5. Layout matches Figma at 320 / 768 / 1200
6. Filter by category
7. Dishes with no price in the chosen country are hidden
8. Out of stock: label is shown and the Add button is disabled
9. No hard-coded prices: all prices come from the API

## Before the test

- Build from `dev`, backend is running, seed data for the 5 countries is loaded.
- Browsers: Chrome and Firefox. Widths: 320, 768 and 1200 px.
- API: `GET /api/v1/categories?country=UZB` and `GET /api/v1/products?country=UZB&categoryId=2`
- Expected prices come from the API answer (seed data), not from Figma.

## Test cases

| ID | AC | Title | Steps | Expected result |
|---|---|---|---|---|
| TC-51-01 | 1 | Categories and cards for the country | 1. Choose UZB. 2. Open Menu. 3. Open DevTools → Network. | Categories and product cards are shown. They are the same as in the answers of the categories and products API for UZB. The requests go with `?country=UZB`. |
| TC-51-02 | 1 | Menu follows the country | 1. Open Menu with UZB. 2. Choose KAZ. | The cards and prices change to KAZ and KZT. No UZS is left on the page. |
| TC-51-03 | 2 | Card content | Look at 3 product cards. | Every card has the name, the description, the photo, the price and the stock status. The data is the same as in the API. |
| TC-51-04 | 2 | Price in local currency | Check the cards in all 5 countries. | The price has 2 decimals and the currency of the country (UZS, KZT, GEL, UAH, CNY). |
| TC-51-05 | 2 | Photos | Look at the photos and check them in DevTools. | Every photo loads and has an `alt` text. Dishes 3, 4, 7 and 9 have no photo in the seed data (on purpose): they show the placeholder, not a broken image. |
| TC-51-06 | 3, 8 | Out of stock card | 1. Find Tuna nigiri (out of stock in the seed data). 2. Open its product window. | The card shows an "Out of stock" label as text, not only colour. In the product window the Add button is disabled. |
| TC-51-07 | 3 | Out of stock cannot be added | 1. Open the window of Tuna nigiri. 2. Click the disabled Add button. 3. Tab to it and press Enter. | Nothing is added to the cart. The cart counter does not change. |
| TC-51-08 | 4 | Loading state | 1. DevTools → Network → Slow 3G. 2. Open Menu. | A loader or skeleton like in the design system (Figma, Components → Loader) is shown. The page does not jump when the data comes. |
| TC-51-09 | 4 | Empty state | Open a category that has no products in this country (if the seed data has one). | The empty state from the design system with a text is shown, not a white page. |
| TC-51-10 | 4 | Error state | 1. Stop the backend or block the products request. 2. Open Menu. | The error state from the design system with a clear text is shown. The page does not crash. |
| TC-51-11 | 5 | Width 320 px | Open Menu at 320 and compare with Figma. | Same layout as in Figma. No horizontal scroll. Long names do not break the card. The categories can be reached. |
| TC-51-12 | 5 | Width 768 and 1200 px | Open Menu at 768 and at 1200 and compare with Figma. | Same grid as in Figma. Photos keep their shape, nothing is cut. |
| TC-51-13 | 6 | Filter by category | 1. Open Menu. 2. Choose every category one by one. | Only the products of the chosen category are shown. The chosen category is marked. |
| TC-51-14 | 6 | Filter with keyboard | 1. Tab to the categories. 2. Choose one with Enter or Space. | The filter works without the mouse. The focus ring is visible. |
| TC-51-15 | 7 | Dish without price is hidden | 1. Find a product that has a price in UZB but not in another country. 2. Choose that country. | The product is not shown. There is no card with an empty or 0.00 price. **Covered by the backend automated test:** the seed data has no dish without a price. For modifiers see TC-52-19. |
| TC-51-16 | 9 | Prices come from the API | 1. Open Menu. 2. Compare every price on the page with the products API answer. 3. Change a price in the seed data (or block the request). | Every price is the same as in the API. Without the API answer no prices are shown, so no price is written in the code. |
