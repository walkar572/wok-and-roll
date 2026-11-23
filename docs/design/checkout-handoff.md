# Sprint 3 checkout design handoff

Owner: Komronkhon Islamov. Refs #170, #55–#62. Updated 19 Nov 2026 against docs/api-contract.md v1.1 on dev.

Design preparation and the 19 Nov screenshot review are complete. Findings are posted in PRs #207, #208, #209, #210, #211 and #212. They cover responsive layout, clipped labels, missing state captures and field-error styling. The frontend PRs track the fixes and follow-up verification.

## Figma frames

File: https://www.figma.com/design/hrf88PdGYF5vTIlzHiPwXo

Each row is ordered 1200 / 768 / 320; use ?node-id= with colons replaced by hyphens.

| Flow or state | 1200 | 768 | 320 |
| --- | --- | --- | --- |
| Delivery checkout | 13:548 | 13:596 | 13:644 |
| Pickup checkout | 115:6292 | 115:7157 | 115:8022 |
| Field validation | 115:6493 | 115:7358 | 115:8223 |
| Success receipt | 115:6698 | 115:7563 | 115:8428 |
| Empty cart | 115:6821 | 115:7686 | 115:8551 |
| Quote loading | 327:9663 | 327:10399 | 327:11126 |
| Quote API error | 327:9814 | 327:10545 | 327:11272 |
| Placing order | 327:9957 | 327:10687 | 327:11412 |
| SLOT_FULL conflict | 327:10105 | 327:10834 | 327:11557 |
| PRICE_CHANGED conflict | 327:10256 | 327:10984 | 327:11705 |
| Success with refreshed price | 329:11296 | 329:11313 | 329:11330 |
| Pickup points | 278:6464 | 278:6514 | 278:6556 |
| Outside delivery zone (out of scope for API v1.1) | 278:6596 | 278:6645 | 278:6694 |
| Slots with FULL and PAST examples | 278:6741 | 278:6780 | 278:6819 |
| Order date | 283:9163 | 283:9232 | 283:9298 |
| Booking reference for Sprint 4 | 278:6856 | 278:6930 | 278:7004 |
| Login reference for Sprint 4 | 281:7414 | 281:7470 | 281:7524 |

The fulfillment section has 51 current frames, including the existing quantity variants. Original frame IDs are preserved. Eighteen new responsive state frames were added. Frames use Auto Layout and linked local components; clip content stays off.

The Outside delivery zone frames are retained as a future design reference. In API v1.1 the guest selects a delivery zone from the list and enters an address. The API does not check whether that address belongs to the selected zone. Exclude this frame from v1.1 implementation and acceptance checks.

## Required behavior for implementation

- Totals come from cart/quote: Subtotal, VAT, Amount incl. VAT, Discount, Delivery fee, Total. Render exactly 2 decimals and the selected country's currency. Do not recalculate totals in the client. UZB golden fixture: subtotal 400.00, VAT 60.00, incl. VAT 460.00, 3% discount 13.80, pickup 446.20 or delivery 466.20 with fee 20.00. The fixture note distinguishes this from catalog prices.
- Requote on country, scenario, zone or cart changes. While loading, show no stale final total and disable Place order. On network or 5xx failure keep the form and cart, show a short retry message, and do not expose backend exception text. Retry returns to loading.
- Name is required, 2–100 trimmed characters. Phone is required, '+' and 8–15 digits; strip spaces before sending. Email is optional and valid if entered; comment is optional, at most 300 characters. Delivery address is 5–200 trimmed characters. Quantity is an integer 1–99.
- Order date is today through 7 days ahead in the selected country's IANA zone, not the browser's or a fixed Tashkent date. The date example is 16–23 Nov with 20 Nov selected. Booking has a separate 30-day rule; do not reuse the order-date limit there.
- Pickup loads /cafes for the country, shows name, address and opening hours; it sends cafeId and no delivery zone/address. Pickup fee is 0.00. Delivery sends the zone ID and address; its kitchen and slots use the zone's cafeId. Changing country clears incompatible café, zone and slot values. Changing scenario/café/zone/date reloads the appropriate slots.
- Slots are 30 minutes within opening hours. Disable FULL and PAST reasons, supplementing color with text. PAST means less than 30 minutes from café-local now. The slot reference explicitly uses 20 Nov 17:40: 18:00 is PAST, 18:30 is FULL, 19:00 is available. Reference data is illustrative; production availability comes from the API.
- During POST /orders disable Place order and retain one Idempotency-Key per unchanged attempt/retry. Send expectedTotal from the last displayed quote. Same-key replay must not duplicate an order. Do not clear the cart until 201.
- 409 SLOT_FULL: keep inputs, clear the invalid slot, show its field error and refresh slots. 409 PRICE_CHANGED: keep inputs, fetch a new quote and require explicit confirmation of the new total. The illustrative refreshed quote is 420.00 + VAT 63.00 = 483.00, discount 14.49, fee 20.00, total 488.51. Its success receipt keeps 488.51.
- Map all fields errors to their input or cart line, focus the first invalid field, connect aria-invalid and aria-describedby, and announce asynchronous changes. The general validation text stays directly above the submit button. A 401 clears the token and leads to login while preserving the cart. Never silently retry as a guest after an expired token.
- Success is based on the 201 response: random WR-XXXXXXXX order number, status NEW, country/currency, date, slot start/end, scenario, café/address, delivery address where relevant and all six totals. Guest orders have no read endpoint; preserve the response for the receipt.

## Responsive implementation review

At each of 320 / 768 / 1200, attach screenshots for delivery and pickup, field errors, loading, API retry, submitting, empty, SLOT_FULL, PRICE_CHANGED and both success totals. Compare them with the frame IDs above. Check no horizontal scroll, text clipping or overlaps; real input/button components; at least 44px active targets; the minus button disabled at quantity 1; no duplicate country/cart in the mobile menu; and no guest Reports link. Keyboard focus and error announcements require a running-app check, not a static screenshot.

The 16 Nov Figma structural check found no visible text outside fulfillment frames, no overlapping root frames, no broken navigation targets, no active hit areas below 44px, no stale 13 Nov dates in this section, and no clipping enabled. FULL and PAST slots have zero actions. This verifies the design, not application behavior.

## Images

The repository [image manifest](image-manifest.json) in `docs/design/` maps original concept food images to optimized WebP files in the same directory. Keep originals as the editable source; use WebP for the app, preserve aspect ratio, specify intrinsic dimensions and lazy-load images below the first viewport. These are generated concept images, not photographs of actual restaurant dishes. No whole-screen rasters are used in Figma.

## Remaining review

- Recheck corrected captures in #207–#212. Verify keyboard focus, error announcements and the API flows in a running app.
- Sprint 4 booking/login/account/admin/report implementation review stays in #196. The links for #61 and #62 are references for that work, not approval of implementation.
