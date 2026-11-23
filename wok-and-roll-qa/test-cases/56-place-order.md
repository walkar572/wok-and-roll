# Test cases: place an order (UC-03)

Story #56 · Task #165 · Asif Najafov (QA Manual) · v0.2 · 19 November 2026

The guest uses the real checkout UI. The same rules are automated on the API by Khumoyunmirzo (#164), so API-only cases are not repeated here. They are checked by hand only where the UI shows the result.

## Before the test

- Build from `dev` with #157 merged. Backend and database are running, seed data for the 5 countries is loaded.
- Browsers: Chrome and Firefox. Widths: 320, 768 and 1200 px. If Firefox is not available, write "Chrome only" in the result.
- Figma frame ids are from `docs/design/checkout-handoff.md`. It is only in PR #204 (https://github.com/ITPU-Teamwork-2026/t12-wok-and-roll/pull/204) until that PR is merged.
- Figma frames (file hrf88PdGYF5vTIlzHiPwXo, `?node-id=` with colons replaced by hyphens), order 1200 / 768 / 320:
  - Delivery checkout 13:548 / 13:596 / 13:644
  - Pickup checkout 115:6292 / 115:7157 / 115:8022
  - Field validation 115:6493 / 115:7358 / 115:8223
  - Success receipt 115:6698 / 115:7563 / 115:8428
  - Empty cart 115:6821 / 115:7686 / 115:8551
  - Quote loading 327:9663 / 327:10399 / 327:11126
  - Quote API error 327:9814 / 327:10545 / 327:11272
  - Placing order 327:9957 / 327:10687 / 327:11412
  - SLOT_FULL 327:10105 / 327:10834 / 327:11557
  - PRICE_CHANGED 327:10256 / 327:10984 / 327:11705
  - Pickup points 278:6464 / 278:6514 / 278:6556
  - Outside delivery zone 278:6596 / 278:6645 / 278:6694 (not in contract v1.1, see TC-167-14)
  - Slots with Full and Too soon 278:6741 / 278:6780 / 278:6819
  - Order date 283:9163 / 283:9232 / 283:9298
- API (`docs/api-contract.md` v1.1): `GET /cafes`, `GET /delivery-zones`, `GET /time-slots`, `POST /cart/quote`, `POST /orders`.
- Rules to check on the UI:
  - Order date: from today to 7 days ahead, in the time zone of the country.
  - Slots: 30 minutes. Unavailable slots are disabled and have a text label: "Full" (no place left) and "Too soon" (the API reason PAST: less than 30 minutes from now in the café).
  - Totals come from the quote: Subtotal, VAT, Amount incl. VAT, Discount, Delivery fee, Total. 2 decimals and the currency of the country. The frontend does not calculate them. The delivery fee is added last, after the discount.
  - Pickup fee is 0.00 and the Delivery fee line is always shown. Delivery zone fee is its own line.
  - The first café and the first delivery zone are pre-selected when the list loads.
  - Order number: `WR-` and 8 characters.
- Test data: UZB, a cart with Subtotal 400.00. Expected from `pricing-examples.json`: VAT 60.00, Amount incl. VAT 460.00, Discount 3% 13.80, Total pickup 446.20, Total delivery (fee 20.00) 466.20.
- Tools: browser DevTools (Network tab) to see the requests. To make an API error, stop the backend for a moment or block the request in DevTools.
- "Run on" in each result: write the date, the browser and the width.

## Test cases

| ID | Area | Title | Steps | Expected result |
|---|---|---|---|---|
| TC-56-01 | Empty cart | Checkout without items | 1. Make sure the cart is empty. 2. Open `/checkout`. | The empty state is shown as in Figma, with a link to the menu. No form, no Place order button. |
| TC-56-02 | Open checkout | Checkout with a cart | 1. Add dishes (Subtotal 400.00 in UZB). 2. Open Cart, go to Checkout. | The form and the order summary are shown. The lines are the same as in the cart. The header counter is correct. |
| TC-56-03 | Quote loading | No stale total | 1. Open Checkout. 2. Look at the totals and the button while the quote loads (slow network in DevTools). | A loading state is shown. No final Total is shown and Place order is disabled until the quote answer comes. |
| TC-56-04 | Pickup, today | Order for today | 1. Choose Pickup. 2. Keep the pre-selected café (or choose the other one). 3. Keep the date today. 4. Choose the first available slot. 5. Type name and phone. 6. Press Place order. | The order is placed. The success page is shown (see TC-56-16). Fee is 0.00. The café is the chosen one. |
| TC-56-05 | Pickup, future date | Order for a later day | Same as TC-56-04, but choose the date 3 days ahead. | The order is placed. The success page shows the chosen date and slot. |
| TC-56-06 | Delivery, today | Delivery for today | 1. Choose Delivery. 2. Keep the pre-selected zone (or choose another). 3. Type an address (5 or more characters). 4. Keep the date today. 5. Choose a slot. 6. Type name and phone. 7. Place the order. | The order is placed. The delivery fee of the zone is on the success page. The address is shown. |
| TC-56-07 | Delivery, future date | Delivery on the last day | Same as TC-56-06, but choose the date 7 days ahead. | The order is placed. The success page shows this date. |
| TC-56-08 | Order date | Date limits | 1. Open the date field. 2. Try a past date. 3. Try today. 4. Try today + 7 days. 5. Try today + 8 days. | Past date and today + 8 are not selectable (or show a clear error). Today and today + 7 work. Nothing is sent for a wrong date. |
| TC-56-09 | Order date | Time zone of the country | 1. Choose a country with another time zone than yours (for example CHN or GEO). 2. Look at the first selectable date and at the slots of today. | "Today" is the date in the country of the café, not the date of the browser. Slots of today follow the café time. |
| TC-56-10 | Pickup points | List of cafés | 1. Choose Pickup. 2. Open the list of pickup points. 3. Change the country in the header and look again. | The cafés of the country are shown with name, address and opening hours, as in Figma "Pickup points". After a country change the old café, zone and slot are cleared. |
| TC-56-11 | Pickup | No delivery fields | Choose Pickup. | The delivery zone and the address are not shown. The Delivery fee line is shown and is 0.00. Nothing about delivery is sent. |
| TC-56-12 | Delivery zones | Zone and fee | 1. Choose Delivery. 2. Choose each zone one by one. | The zone fee is shown as its own line. The Total is requoted after every change. The fee is added after the discount and does not change the discount. |
| TC-56-13 | Delivery zones | Zone not available | Not in this file. The guest chooses a zone from a list, so there is no address check in v1.1. The error `UNKNOWN_DELIVERY_ZONE` is checked in TC-167-14. | See TC-167-14. |
| TC-56-14 | Slots | Full and Too soon | 1. Choose today. 2. Look at the slot list. 3. Use a day with a full slot if the seed or a test order makes one. | Slots are 30 minutes. A slot that starts in less than 30 minutes is disabled with the text "Too soon". A full slot is disabled with the text "Full". The state is not shown only by colour. A disabled slot cannot be chosen with the mouse or the keyboard. |
| TC-56-15 | Slots | Slots reload | Change the scenario, the café, the zone and the date one by one. | The slot list reloads every time and belongs to the chosen café (for delivery: the café of the zone). A slot chosen before that is not valid any more is cleared. |
| TC-56-16 | Success page | Number, slot and totals | 1. Place a pickup order (Subtotal 400.00, UZB). 2. Compare the success page with the checkout summary and with the quote answer in the Network tab. | The page shows: the order number `WR-` + 8 characters, the date and the slot start and end, the café name and address, and the totals block. The numbers are the same as in the quote: Subtotal 400.00, VAT 60.00, Amount incl. VAT 460.00, Discount 13.80, Fee 0.00, Total 446.20. |
| TC-56-17 | Success page | Delivery totals | Same as TC-56-16 with Delivery (zone fee 20.00). | The page shows the delivery address instead of the café. Total is 466.20. Fee (20.00) is its own line. |
| TC-56-18 | Success page | Reload | Press F5 on the success page. | Write down what happens. Expected: no second order is created. The guest has no read endpoint, so the page may be empty or go to the menu. It must not show an error text or place the order again. |
| TC-56-19 | Cart | Cart after success | 1. Place an order. 2. Look at the header counter and open Cart. | The cart is cleared only after the order is placed. The counter is 0 and the cart is empty. |
| TC-56-20 | Cart | Cart after failure | Make the order fail (TC-56-22) and look at the cart. | The cart is not cleared. The counter and the lines are the same as before. |
| TC-56-21 | Quote error | Retry | 1. Open Checkout. 2. Make the quote request fail (stop the backend). 3. Start the backend and press Try again. | A short text with a retry button is shown. No exception text. The form and the cart are kept. Place order is disabled. Try again goes back to loading and then shows the totals. |
| TC-56-22 | Order error | Input is kept | 1. Fill the whole form. 2. Make the order request fail (stop the backend or block `/orders`). 3. Press Place order. 4. Start the backend and press it again. | A clear short message is shown. No exception text and no "Failed to fetch". All typed values, the café or zone, the date and the slot are still there. The second try works. |
| TC-56-23 | Double click | One order | 1. Fill the form. 2. Press Place order two times very fast (double click). 3. Look at the Network tab. | The button is disabled while the request is running. Only one order is created: one `POST /orders`, or two with the same `Idempotency-Key` and the same order number. The success page shows one number. |
| TC-56-24 | Double click | Retry after error | 1. Make the first try fail on the network. 2. Press Place order again. | The retry uses the same `Idempotency-Key` as the first try (Network tab). Only one order exists after it works. |
| TC-56-25 | Slot full | SLOT_FULL on the UI | Use the last free place of a slot in a second browser, then place the order for the same slot in the first browser. | The guest sees the error next to the slot field. The inputs are kept, the slot is cleared and the slot list is refreshed. The two-browser part is also in #167. |
| TC-56-26 | Price changed | PRICE_CHANGED on the UI | Change a price or a discount rule in the database after the quote is shown, then place the order. | The inputs are kept. A new quote is shown and the guest must confirm the new total. The success page shows the new total (not the old one). |
| TC-56-27 | Country | Change country in checkout | 1. Fill a pickup order in UZB. 2. Change the country in the header to KAZ. | Prices, VAT and currency are requoted. The café, zone and slot of UZB are cleared. Nothing of UZB is sent. |
| TC-56-28 | Validation | Required fields | 1. Press Place order with an empty form. | The guest stays on Checkout. Every error is next to its field. The focus goes to the first wrong field. No order is created. The full list of rules is in #166. |
| TC-56-29 | Keyboard | Order without a mouse | Place a pickup order only with Tab, Space, arrow keys and Enter. | Every control can be reached and used. The focus ring is visible. Errors are announced (live region). |
| TC-56-30 | Layout 320 | Checkout at 320 px | Open delivery, pickup, field errors, loading, retry, submitting, empty and success at 320 px. Compare with the 320 frames. | The same layout as in Figma. No horizontal scroll, no cut text, no overlap. Active targets are at least 44 px. |
| TC-56-31 | Layout 768 | Checkout at 768 px | Same as TC-56-30 at 768 px. | The same layout as in the 768 frames. No horizontal scroll, no cut text, no overlap. |
| TC-56-32 | Layout 1200 | Checkout at 1200 px | Same as TC-56-30 at 1200 px. | The same layout as in the 1200 frames. No horizontal scroll, no cut text, no overlap. |
| TC-56-33 | Browsers | Chrome and Firefox | Run TC-56-04, TC-56-06, TC-56-16, TC-56-22 and TC-56-23 in Chrome and in Firefox. | The results are the same in both browsers. If Firefox is not available, write "Chrome only". |

## Run log

Fill after #157 is merged and the APIs are on `dev`. One line per test case: result (Pass, Fail, Blocked), date, browser, width, bug number or request id of the failure.

| ID | Result | Date | Browser | Width | Bug / request id |
|---|---|---|---|---|---|
| | | | | | |

## Notes

- Lower case email is not visible on the UI. It is checked in the database in TC-57-18 and in the API tests (#164).
- Out of scope here: API rules and error codes (Khumoyunmirzo, #164), pricing matrix (#162), pickup, zones and slots in detail (#167), validation rules (#166).
- TC-56-18 (F5 on the success page): the team agreed with the expected result as written.
