# Test cases: cart validation (keyboard, 320 px)

Story #54 · Task #121 · Asif Najafov (QA Manual) · v0.3 · 14 November 2026 (updated after review)

Covers the points from #121: quantity 0, negative, decimal, empty, text, remove, empty state, error from the API, keyboard and 320 px.

## Before the test

- Build from `dev`, backend is running, seed data for the 5 countries is loaded.
- Browsers: Chrome and Firefox. Widths: 320, 768 and 1200 px.
- API: `POST /api/v1/cart/quote`. The cart shows the numbers from this answer. The frontend does not calculate totals.
- Error answers from the API (`docs/api-contract.md`, section 4):
  - quantity 0, negative, decimal or more than 99: 400, `VALIDATION_ERROR`, `fields["items[0].quantity"]` = "must be a whole number from 1 to 99"
  - quantity 99: accepted (200)
  - quantity missing: 400, `VALIDATION_ERROR`, `fields["items[0].quantity"]` = "is required"
  - quantity is text (`"abc"`): 400, `MALFORMED_REQUEST`, no `fields`
- Sprint 2: the cart shows Subtotal, VAT and Amount incl. VAT. Discount and Total come in Stage 3 (#55).

## Test cases

| ID | Area | Title | Steps | Expected result |
|---|---|---|---|---|
| TC-54-01 | Quantity | Plus and minus | 1. Add a product. 2. Open Cart. 3. Press plus, then minus. | Quantity changes by 1. Line total, Subtotal, VAT and Amount incl. VAT update from a new quote answer. The header counter updates. |
| TC-54-02 | Quantity 0 | Minus at 1 | Quantity is 1. Press minus. | Quantity stays 1 (minus is disabled) or the guest is asked to remove the line. 0 is never shown or sent. |
| TC-54-03 | Quantity 0 | Type 0 | Type `0` in the quantity field and leave the field. | The value is not accepted. A text error is shown next to the field. The old valid quantity stays. |
| TC-54-04 | Negative | Type -1 | Type `-1` in the quantity field. | Not accepted, text error is shown, no request with a negative quantity. |
| TC-54-05 | Decimal | Type 1.5 | Type `1.5` (and `1,5`). | Not accepted or changed to a whole number. A text error is shown. 1.5 is never sent. |
| TC-54-06 | Empty | Clear the field | Delete the value and leave the field. | Not accepted. A text error is shown or the old value comes back. Totals are not broken (no `NaN`). |
| TC-54-07 | Text | Type letters | Type `abc` and `2a`. | Not accepted. A text error is shown. No `NaN` on the page. |
| TC-54-08 | Quantity | Limit 99 | 1. Type `99`. 2. Type `100`. | 99 is accepted and the totals update. 100 is not accepted and a text error says the limit (1 to 99). The layout is not broken. |
| TC-54-09 | Remove | Remove one line | Cart has 2 lines. Press Remove on one. | The line is deleted. Totals update from the API. |
| TC-54-10 | Empty state | Remove the last line | Remove the last line. | The empty state from the design system is shown with a text and a link to the menu. No 0.00 totals. |
| TC-54-11 | Empty state | Empty cart from the start | Clean start, open Cart. | The same empty state. No error. |
| TC-54-12 | API error | API validation | In Swagger send `POST /api/v1/cart/quote` with `"quantity": 0`, then `-1`, `1.5`, `100` and `99`. | 0, -1, 1.5 and 100: status 400, `VALIDATION_ERROR`, `fields["items[0].quantity"]` = "must be a whole number from 1 to 99". 99: status 200, the quote is returned. Never 500. |
| TC-54-12a | API error | Text and missing quantity | 1. Send `"quantity": "abc"`. 2. Send an item without `quantity`. | 1. Status 400, `MALFORMED_REQUEST`, no `fields`. 2. Status 400, `VALIDATION_ERROR`, `fields["items[0].quantity"]` = "is required". Never 500. |
| TC-54-13 | API error | Error shown on the page | Make the API return an error (block the quote request in DevTools or stop the backend) and change a quantity. | A clear text error is shown. The page does not crash. Old totals are not shown as new. |
| TC-54-14 | Keyboard | Keyboard only | 1. Tab to the quantity field, plus, minus and Remove on every line. 2. Use them with Enter, Space or typing. 3. Tab to the checkout button. | Everything can be reached and used. The focus ring is visible. After Remove the focus goes to the next line or to the empty state. |
| TC-54-15 | Keyboard | Error is read | Type `0` with the keyboard and check the error in DevTools (Accessibility). | The error text is connected to the field (`aria-describedby` or `aria-live`), so a screen reader reads it. |
| TC-54-16 | 320 px | Cart at 320 px | Open Cart with 3 lines at 320. | No horizontal scroll. Names, prices and buttons are not cut. The error text fits. The checkout button can be reached. |
| TC-54-17 | 320 px | Tap targets | Measure plus, minus and Remove in DevTools at 320. | Each is about 44 × 44 px or bigger. |
| TC-54-18 | Layout | 768 and 1200 px | Open Cart at 768 and at 1200. | The layout matches Figma, nothing is broken. |
