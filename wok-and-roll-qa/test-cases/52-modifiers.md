# Test cases: product modifiers and prices

Story #52 · Task #115 · Asif Najafov (QA Manual) · v0.3 · 14 November 2026

Covers the points from #115: SIZE one, EXTRA many, price on the button, prices per country, separate cart lines.

## Before the test

- Build from `dev`, backend is running, seed data for the 5 countries is loaded.
- Browsers: Chrome and Firefox. Widths: 320, 768 and 1200 px.
- API: `GET /api/v1/products/{id}?country=UZB` (modifiers and their prices).
- Seed data: SIZE Regular / Large (Firecracker udon, Miso mushroom rice), 400 ml / 600 ml (Yuzu lemonade). Extra tofu has no price in China.
- Rule (Decision Log and `test-plan.md`): unit price = base price + price of the chosen modifiers, then × quantity.
- The frontend does not calculate totals. The numbers on the page must be the same as in the API.

## Test cases

| ID | Area | Title | Steps | Expected result |
|---|---|---|---|---|
| TC-52-01 | General | Product window opens | 1. Open Menu. 2. Click a product with modifiers. | The window shows the name, photo, description, base price and the modifier groups. The data is the same as in the product API. |
| TC-52-02 | General | Modifier prices are shown | Look at every modifier option. | Every option shows its price with a plus sign, as in Figma (example for Firecracker udon in UZB: Regular `+UZS 0.00`, Large `+UZS 12.00`), with 2 decimals and the sign of the currency of the country (sum: the word, `soʻm` in Uzbek). Today the app shows `UZS 12.00` without the plus sign: bug #200. |
| TC-52-03 | SIZE one | Only one SIZE | 1. Choose one SIZE. 2. Choose another SIZE. | Only one SIZE is selected at a time. The first one is not selected any more. |
| TC-52-04 | SIZE one | First size is pre-selected | Open the window and do not choose anything. | The first size is selected (Regular for Firecracker udon, 400 ml for Yuzu lemonade). The button shows the price with this size. A product without SIZE is never added. |
| TC-52-05 | EXTRA many | Several EXTRA | 1. Choose two EXTRA options. 2. Remove one. | Several EXTRA can be selected together. An option can be removed again. |
| TC-52-06 | EXTRA many | No EXTRA | Choose only a SIZE, no EXTRA. Press Add. | The product is added. EXTRA is not required. |
| TC-52-07 | Button price | Price on the button changes | 1. Remember the price on the Add button. 2. Choose a SIZE with a price. 3. Add one EXTRA. | The button price = base price + chosen modifiers. It updates at once. |
| TC-52-08 | Button price | Price with quantity | 1. Choose modifiers. 2. Set quantity 3. | The button price = (base price + modifiers) × 3, not base × 3 + modifiers. |
| TC-52-09 | Button price | Same as the API | Add the product and look at the answer of `POST /api/v1/cart/quote`. | `unitPrice` and `lineTotal` are the same as the price on the button. |
| TC-52-10 | Per country | Prices in another country | 1. Choose KAZ. 2. Open the same product. | Base price and modifier prices are in KZT and are the same as in the product API for KAZ. No UZS is left. |
| TC-52-11 | Per country | All 5 countries | Repeat TC-52-10 for GEO, UKR, CHN. | Every country shows its own prices from the API. |
| TC-52-12 | Cart lines | Different modifiers = different lines | 1. Add Firecracker udon Regular. 2. Add Firecracker udon Large. 3. Repeat with Yuzu lemonade 400 ml and 600 ml. 4. Open Cart. | Each size is a separate line, with its own modifiers and price. |
| TC-52-13 | Cart lines | Same modifiers = one line | 1. Add Firecracker udon Large + Extra chicken. 2. Add it again with the same choice. 3. Open Cart. | One line with quantity 2, not two lines. |
| TC-52-14 | Cart lines | Modifiers shown in the cart | Open Cart after TC-52-12. | Every line shows its chosen SIZE and EXTRA. |
| TC-52-15 | General | Product without modifiers | Open a product that has no modifiers. | The window opens without empty groups. Quantity and Add work. |
| TC-52-16 | General | Close the window | 1. Press Esc. 2. Open again and click outside. 3. Open again and press the close button. | The window closes every time. Nothing is added. The focus goes back to the product card. |
| TC-52-17 | General | Keyboard only | 1. Open the window with Enter. 2. Tab through the options. 3. Choose with Space or arrow keys. 4. Press Add with Enter. | The focus stays inside the window. The focus ring is visible. Everything works without the mouse. |
| TC-52-18 | General | Width 320, 768, 1200 px | Open the window at each width. | The window fits the screen, no horizontal scroll, the Add button can be reached, long names do not break the layout. |
| TC-52-19 | Per country | Modifier without price is hidden | 1. Choose CHN. 2. Open the dish that has Extra tofu. 3. Compare with UZB. | In CHN Extra tofu is not shown (no price in China). In UZB it is shown with its price. |
