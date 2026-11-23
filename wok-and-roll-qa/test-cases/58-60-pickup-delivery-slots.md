# Test cases: pickup, delivery zones and time slots

Task #167 · Related tasks #158 (pickup café), #159 (delivery zone), #160 (time slot picker) · Asif Najafov (QA Manual) · v0.2 · 19 November 2026

The guest chooses where and when to get the order. The API rules are automated by Khumoyunmirzo (#164). Here the guest sees the result on the real UI.

## Before the test

- Build from `dev`. Run each part on the day its task is merged: pickup after #158, delivery after #159, slots after #160. Backend and database are running, seed data for the 5 countries is loaded.
- Browsers: Chrome and Firefox. Widths: 320, 768 and 1200 px. If Firefox is not available, write "Chrome only".
- Rules from `docs/api-contract.md` (sections 3.6, 3.7, 3.8, 3.9):
  - Cafés: 2 cafés per country in the seed. Each has name, address, opening hours, time zone. The UI calls it "pickup point". Every café takes pickup orders and is the kitchen for its delivery zones.
  - Pickup sends `cafeId`. `deliveryZoneId` and `deliveryAddress` are null or missing (both are fine). Pickup fee is 0.00 and the Delivery fee line is always shown.
  - The first café and the first delivery zone are pre-selected when the list loads. "Nothing chosen" cannot happen in the UI.
  - Delivery zones: fixed fee per zone (0.00 or more), added last, after the discount, never changes the discount. Every country has a zone with fee 20.00. Delivery sends the zone id and the address. The slots of a delivery order are the slots of the café of the zone.
  - Delivery address: 5 to 200 characters, spaces at the ends are removed.
  - Order date: from today to 7 days ahead, in the time zone of the café.
  - Slots: 30 minutes, from opening to 30 minutes before closing (for example 10:00 … 21:30 for a café that is open 10:00 to 22:00). `capacity` is 5 orders per café per slot, shared by pickup and delivery.
  - API reasons: FULL (no place left) and PAST (the slot starts in less than 30 minutes in the café time). The UI shows them as the labels "Full" and "Too soon". Both slots are disabled.
  - Two guests can see the same last place. One gets it, the other gets 409 `SLOT_FULL`.
- Figma frame ids are from `docs/design/checkout-handoff.md`, which is only in PR #204 (https://github.com/ITPU-Teamwork-2026/t12-wok-and-roll/pull/204) until it is merged.
- Figma frames (order 1200 / 768 / 320): Pickup points 278:6464 / 278:6514 / 278:6556, Outside delivery zone 278:6596 / 278:6645 / 278:6694 (not in contract v1.1, see TC-167-14), Slots with Full and Too soon 278:6741 / 278:6780 / 278:6819, Order date 283:9163 / 283:9232 / 283:9298.
- Tools: DevTools Network tab (to see `cafeId`, `deliveryZoneId`, `slotStart` in the request). Two browsers (or one normal and one private window) for the SLOT_FULL cases. To fill a slot, place 5 orders for it (or ask the developer for a test slot).

## Pickup (after #158)

| ID | Area | Title | Steps | Expected result |
|---|---|---|---|---|
| TC-167-01 | Pickup points | List of cafés | 1. Open Checkout with a cart. 2. Choose Pickup. | The cafés of the chosen country are shown. Each one has name, address and opening hours, as in Figma "Pickup points". There are 2 cafés in the seed. |
| TC-167-02 | Pickup points | Every country | Repeat TC-167-01 for UZB, KAZ, GEO, UKR and CHN. | Every country shows its own cafés (name and address of that country). No café of another country is shown. |
| TC-167-03 | Pickup points | Choose a café | Choose the first café, then the second. | The chosen café is marked and can be seen clearly. Only one café can be chosen. The slots reload for the chosen café. |
| TC-167-04 | Pickup | Fee 0.00 | Place a pickup order. | The Delivery fee line is shown and is 0.00 (not hidden). The Total is the Amount incl. VAT minus the discount. |
| TC-167-05 | Pickup | What is sent | Place a pickup order and look at `POST /orders`. | The request has `scenario` PICKUP and `cafeId`. `deliveryZoneId` and `deliveryAddress` are null or missing (both are fine). |
| TC-167-06 | Pickup | Café on success page | Place a pickup order for the second café. | The success page shows that café (name and address), not the first one. |
| TC-167-07 | Pickup | Café list error | 1. Block `GET /cafes` in DevTools (or stop the backend). 2. Open Checkout and choose Pickup. 3. Then unblock it and press the retry. | A short text is shown ("We could not load pickup cafés. Please try again."), no exception text. Place order cannot send a pickup order without a café. After the retry the list is shown and the first café is pre-selected. If a country has no active café (deactivate them in the database), the text says no pickup cafés are available. The first café is pre-selected when the list is loaded, so "no café chosen" cannot be tested. |
| TC-167-08 | Pickup | Change country | Choose a café in UZB. Change the country to KAZ. | The chosen café is cleared. The list shows the cafés of KAZ. The old slot is cleared too. |
| TC-167-09 | Pickup | Opening hours | Read the opening hours of a café and look at the slot list. | The first slot starts at the opening time. The last slot starts 30 minutes before closing. |

## Delivery (after #159)

| ID | Area | Title | Steps | Expected result |
|---|---|---|---|---|
| TC-167-10 | Delivery zones | List of zones | Choose Delivery. | The zones of the chosen country are shown with name and fee. Every country has a zone with fee 20.00. |
| TC-167-11 | Delivery zones | Fee is its own line | Choose each zone one by one. | The zone fee is shown as its own line. The Total is requoted every time. The fee is added after the discount. |
| TC-167-12 | Delivery zones | Fee does not change the discount | Use a cart just below a discount threshold (see `55-pricing-matrix.md`, TC-55-E rows). Choose Delivery. | The discount rate does not change when the fee is added. |
| TC-167-13 | Delivery zones | Zone with fee 0.00 | If a zone with fee 0.00 exists, choose it. | The fee line shows 0.00. The order works. If there is no such zone, write "Not run". |
| TC-167-14 | Delivery zones | Zone not available (UNKNOWN_DELIVERY_ZONE) | In contract v1.1 the guest chooses a zone from a list, so there is no address check and the Figma frame "Outside delivery zone" is not in v1.1 (the lead checks it with Komronkhon). Test the API error instead: 1. Choose Delivery, a zone and fill the form. 2. In the database set `active = false` for the chosen zone (`UPDATE delivery_zone SET active = false WHERE id = <id>`). 3. Place the order. 4. Set `active = true` again. | The order is not placed. The API answers 400 `UNKNOWN_DELIVERY_ZONE` with the key `deliveryZoneId`. The error is shown next to the zone. The input is kept and the cart is not cleared. This case is only in this file. |
| TC-167-15 | Delivery | What is sent | Place a delivery order and look at `POST /orders`. | The request has `scenario` DELIVERY, `deliveryZoneId` and `deliveryAddress`. `cafeId` is null or missing (both are fine). |
| TC-167-16 | Delivery | Slots of the zone café | Choose a zone, look at the slots. Choose a zone of the other café. | The slots are the slots of the café of the zone. After a zone change the slots reload. |
| TC-167-17 | Delivery address | Length limits | Type an address of 4, 5, 200 and 201 characters (paste it for 201). | 4: a text error. 5 and 200: accepted. 201: a text error, or the field stops at 200 characters. |
| TC-167-18 | Delivery address | Spaces and empty | Type only spaces. Type `  Amir Temur 15  `. | Only spaces: a text error ("required"). The spaces at the ends are removed and the address is accepted. |
| TC-167-19 | Delivery address | Special characters | Type an address with a comma, a number, a quote and a Cyrillic word. | The address is accepted and shown on the success page exactly as typed. No broken characters. |
| TC-167-20 | Delivery | Address is required | 1. Choose Delivery (the first zone is pre-selected). 2. Leave the address empty. 3. Press Place order. | The order is not placed. A text error is shown next to the address. The focus goes to it. The zone is not an error because it is pre-selected. |
| TC-167-21 | Delivery | Switch scenario | Choose a zone (not the first one) and type an address. Switch to Pickup and back to Delivery. | Delivery fields are hidden for Pickup and not sent. When the guest comes back, the zone and the address are kept (the team confirmed this). The totals are right. |
| TC-167-22 | Delivery | Address on success page | Place a delivery order. | The success page shows the delivery address, the date and the slot, and the totals. |
| TC-167-41 | Delivery zones | Zone list error | 1. Block `GET /delivery-zones` in DevTools. 2. Choose Delivery. 3. Unblock it and press the retry. | A short text is shown ("We could not load delivery zones. Please try again."), no exception text. The order cannot be placed without a zone. After the retry the list is shown and the first zone is pre-selected. If a country has no active zone, the text says no delivery zones are available. |

## Time slots (after #160)

| ID | Area | Title | Steps | Expected result |
|---|---|---|---|---|
| TC-167-23 | Slots | Step and range | Open the slot list for a day in the future. | Slots are 30 minutes. The first one starts at opening. The last one starts 30 minutes before closing. No slot is outside the opening hours. |
| TC-167-24 | Slots | Too soon | Choose today. Look at the slots near the current time of the café. | A slot that starts in less than 30 minutes (and all slots before it) is disabled with the text "Too soon". The next slot is available. |
| TC-167-25 | Slots | PAST uses café time | Choose a café in another time zone than yours (for example CHN or GEO). Compare with the clock of that country. | "Too soon" is calculated with the café time, not with the time of the browser. |
| TC-167-26 | Slots | FULL | Fill a slot (5 orders). Open the slot list again. | The full slot is disabled with the text "Full". The other slots are free. |
| TC-167-27 | Slots | Not only colour | Look at a "Full" and a "Too soon" slot. | Each one has a text ("Full" or "Too soon"). It is not shown only by colour. |
| TC-167-28 | Slots | Disabled slot cannot be chosen | Try to choose a disabled slot with the mouse and with the keyboard. | It cannot be chosen. The focus can reach it (or skips it) and Enter or Space does nothing. |
| TC-167-29 | Slots | Slots reload | Change the date, the café and the zone one by one. | The list reloads after every change. A chosen slot that is not valid any more is cleared. |
| TC-167-30 | Slots | Order date | Choose today, today + 7 and try today + 8 and a past date. | Today and today + 7 work. Today + 8 and a past date cannot be chosen, or a text error is shown. See Figma "Order date". |
| TC-167-31 | Slots | Shared capacity | Place 3 pickup orders and 2 delivery orders (of that café) for the same slot. Open the list again. | The slot is FULL. Pickup and delivery use the same 5 places. |
| TC-167-32 | Slots | Remaining places | Note a free slot. Place one order for it. Open the list again. | The slot has one place less. When it was the last place, it is FULL. |
| TC-167-33 | Slots | Slot is required | Do not choose a slot. Press Place order. | The order is not placed. A text error is shown next to the slot. The focus goes to it. |
| TC-167-34 | Slots | Slot on success page | Place an order for the slot 12:30. | The success page shows the date and the slot "12:30 – 13:00". |
| TC-167-35 | Slots | Slots API error | Make `GET /time-slots` fail (stop the backend). | A short message with a retry is shown. No exception text. Place order is disabled until the slots are loaded. The rest of the form is kept. |
| TC-167-36 | SLOT_FULL | Two browsers, last place | 1. In both browsers fill an order for the same slot that has one free place. 2. Place the order in the first browser. 3. Place the order in the second browser. | The first browser gets the order. The second gets a clear error next to the slot. The inputs are kept, the slot is cleared and the slot list is refreshed (the slot is now FULL). No second order is created. |
| TC-167-37 | SLOT_FULL | Choose another slot | After TC-167-36, choose another slot in the second browser and place the order. | The order is placed. The cart is cleared only now. |

## Layout and browsers

| ID | Area | Title | Steps | Expected result |
|---|---|---|---|---|
| TC-167-38 | Layout | Pickup points, slots and zones | Open Pickup points, Slots with Full and Too soon and Order date (the frame "Outside delivery zone" is not in v1.1) at 320, 768 and 1200 px. Compare with the Figma frames. | The same layout as in Figma. No horizontal scroll, no cut text, no overlap. Active targets are at least 44 px. |
| TC-167-39 | Keyboard | Choose café, zone and slot | Choose a café (or a zone) and a slot only with the keyboard. | Every control is reachable. The focus ring is visible. The chosen option is announced (selected state is clear). |
| TC-167-40 | Browsers | Chrome and Firefox | Run TC-167-01, TC-167-10, TC-167-24 and TC-167-36 in both browsers. | The results are the same. If Firefox is not available, write "Chrome only". |

## Run log

Run each part on the day its task is merged. One line per test case: result (Pass, Fail, Blocked), date, browser, width, bug number or request id of the failure.

| Part | Task merged on | Run on | Passed | Failed | Bugs |
|---|---|---|---|---|---|
| Pickup | | | | | |
| Delivery | | | | | |
| Slots | | | | | |

## Notes

- File name: `58-60-pickup-delivery-slots.md` (stories #58, #59, #60). The IDs stay TC-167-xx (task #167).
- Out of scope here: totals and discount numbers (#162), the whole checkout flow and success page (#165), field validation (#166), API rules (#164).
- Answered by the team: the zone and the address are kept when the guest switches back (TC-167-21).
