# Test cases: checkout validation and Stage 2 regression

Task #166 · Story #57 · Asif Najafov (QA Manual) · v0.3 · 22 November 2026

Part 1 checks one case per validation rule of the checkout, with valid and invalid edge values, on the UI. Part 2 is the Stage 2 regression list (country, menu, modifiers, cart). The API side of the rules is automated by Khumoyunmirzo (#164), so API checks are added here only where his tests do not cover a rule.

## Before the test

- Build from `dev` with #161 (checkout validation) merged. Backend and database are running, seed data for the 5 countries is loaded.
- Browsers: Chrome and Firefox. Widths: 320, 768 and 1200 px (run the whole list in Chrome at 1200, and the "Layout" and "Keyboard" cases at all widths). If Firefox is not available, write "Chrome only".
- Rules (`docs/api-contract.md` section 3.9 and `docs/design/checkout-handoff.md`):
  - Name: required, 2 to 100 characters, spaces at the ends are removed.
  - Phone: required, `+` and 8 to 15 digits. The UI removes spaces before sending.
  - Email: optional. If typed, it must be a valid email, at most 254 characters. It is saved in lower case.
  - Comment: optional, at most 300 characters.
  - The first café and the first delivery zone are pre-selected when the list loads, so "nothing chosen" cannot happen in the UI.
  - Delivery address (Delivery only): required, 5 to 200 characters, spaces at the ends are removed.
  - Order date: from today to 7 days ahead in the country time zone.
  - Slot: required, a free slot of the café.
  - Scenario: Pickup or Delivery. Pickup needs a café. Delivery needs a zone and an address.
  - Quantity: whole number 1 to 99 (see `54-cart-validation.md`).
- Error rules on the page: every error is a text next to its field (not only a colour). The focus goes to the first wrong field. The field has `aria-invalid` and `aria-describedby`. A general text is directly above the Place order button. Errors that come later from the API are announced (live region). The typed values are kept. No order is created while data is wrong. QA checks the status, the `code` and the `fields` keys, not the exact text.
- Tools: DevTools Network tab (a rejected form must not send `POST /orders`), a screen reader (NVDA on Windows) for TC-57-40.
- How to read the result: Pass or Fail, with the actual text or number and the request id of a failed answer.

## Part 1. Validation rules

| ID | Field | Title | Steps | Expected result |
|---|---|---|---|---|
| TC-57-01 | Name | Valid: 2 characters | Type `Al` as name. Fill the rest correctly. Place the order. | Accepted. The order is placed. |
| TC-57-02 | Name | Valid: 100 characters | Type a name of exactly 100 characters. | Accepted. |
| TC-57-03 | Name | Invalid: empty | Leave the name empty. Place the order. | A text error next to the field ("required"). No `POST /orders`. |
| TC-57-04 | Name | Invalid: 1 character | Type `A`. | A text error (too short). |
| TC-57-05 | Name | Invalid: 101 characters | Type a name of 101 characters (paste it). | A text error (too long), or the field stops at 100 characters. |
| TC-57-06 | Name | Spaces only | Type 5 spaces. | A text error. The spaces are not accepted as a name. |
| TC-57-07 | Name | Spaces at the ends | Type `  Aziz Karimov  `. | Accepted. The success page shows `Aziz Karimov` without extra spaces. |
| TC-57-08 | Name | Special characters | Type `Zhānar O'Neil-Іванов`. | Accepted. The characters are shown correctly on the success page. |
| TC-57-09 | Phone | Valid: 8 digits | Type `+12345678`. | Accepted. |
| TC-57-10 | Phone | Valid: 15 digits | Type `+123456789012345`. | Accepted. |
| TC-57-11 | Phone | Valid with spaces | Type `+998 90 123 45 67`. | Accepted. The request has `+998901234567` (no spaces). |
| TC-57-12 | Phone | Invalid: empty | Leave the phone empty. | A text error ("required"). |
| TC-57-13 | Phone | Invalid: 7 digits | Type `+1234567`. | A text error. |
| TC-57-14 | Phone | Invalid: 16 digits | Type `+1234567890123456`. | A text error. |
| TC-57-15 | Phone | Invalid: no plus | Type `998901234567`. | A text error (the format needs `+`). |
| TC-57-16 | Phone | Invalid: letters | Type `+99890abc4567`. | A text error. No `NaN` or broken field. |
| TC-57-17 | Email | Empty is valid | Leave the email empty. | Accepted. The order works. |
| TC-57-18 | Email | Valid | Type `Aziz@Mail.uz`. | Accepted. The email is not shown on the UI and the order answer has no email, so check the lower case in the database: `aziz@mail.uz`. The API side is also in #164. |
| TC-57-19 | Email | Invalid formats | Try `aziz`, `aziz@`, `@mail.uz`, `a b@mail.uz`, `aziz@mail`. | A text error for each. No `POST /orders`. |
| TC-57-20 | Email | Length 254 and 255 | Type a valid email of 254 characters, then 255. | 254 accepted. 255: a text error, or the field stops at 254 characters (write down what you see). |
| TC-57-21 | Comment | Valid: empty and 300 | Leave it empty. Then type exactly 300 characters. | Both accepted. |
| TC-57-22 | Comment | Invalid: 301 | Type 301 characters. | The field stops at 300 characters (the team confirmed). There is no counter. Paste 301 characters: only 300 stay. |
| TC-57-23 | Delivery address | Valid: 5 and 200 | Choose Delivery. Type an address of 5, then of 200 characters. | Both accepted. |
| TC-57-24 | Delivery address | Invalid: 4 and 201 | Type an address of 4, then of 201 characters. | A text error for 4. For 201 (paste it): a text error, or the field stops at 200 characters. |
| TC-57-25 | Delivery address | Empty and spaces | Leave it empty. Then type only spaces. | A text error for both. Ends are trimmed (`  Amir Temur 15  ` is accepted). |
| TC-57-26 | Delivery address | Pickup does not need it | Choose Pickup. | There is no address field. The order works without an address. `deliveryAddress` and `deliveryZoneId` are null or missing in the request (both are fine). |
| TC-57-27 | Delivery zone | Zone is pre-selected | Choose Delivery. | N/A for "zone not chosen": the first zone is pre-selected. The zone list error is tested in TC-167-41. |
| TC-57-28 | Pickup café | Café is pre-selected | Choose Pickup. | N/A for "café not chosen": the first café is pre-selected. The café list error is tested in TC-167-07. |
| TC-57-29 | Order date | Valid: today and today + 7 | Choose today, then today + 7. | Both accepted. |
| TC-57-30 | Order date | Invalid: yesterday and today + 8 | Try yesterday and today + 8 (type it if the field allows it). | Not selectable, or a text error. The page asks for today or a later date. |
| TC-57-31 | Order date | Empty | Try to clear the date. | N/A: the date cannot be cleared on the page (the team confirmed on 22 Nov). |
| TC-57-32 | Slot | Slot is required | Do not choose a slot. | A text error next to the slot. |
| TC-57-33 | Slot | Slot disabled | Try to choose a "Full" or a "Past" slot. | It cannot be chosen. |
| TC-57-34 | Whole form | Everything empty | Press Place order with an empty form. | Every required field has a text error. The focus goes to the first wrong field. A general text is above the button. No `POST /orders`. The guest stays on Checkout. |
| TC-57-35 | Whole form | Several errors, fix one by one | After TC-57-34, fix the fields one by one. | The error of a field disappears when the value is right. The other errors stay until they are fixed. |
| TC-57-36 | Whole form | Typed values are kept | Fill the form, make one field wrong, press Place order. | The wrong field shows its error. All other values stay in their fields. |
| TC-57-37 | Whole form | Server error next to the field | If the API sends `fields` for a field (for example `slotStart`), see it on the page. Use a full slot for the test (see `58-60-pickup-delivery-slots.md`, TC-167-36). | The text is shown next to that field. The values are kept. The focus goes to it. |
| TC-57-38 | Quantity | Limit on the way to checkout | In the cart type 0, 1, 99, 100. Go to Checkout. | 0 and 100 are not accepted. 1 and 99 work and checkout totals are right. The details are in `54-cart-validation.md`. |
| TC-57-39 | Security | HTML in the fields | Type `<b>x</b>` as name and `<script>alert(1)</script>` as comment. Place the order. | The text is shown as plain text on the success page. Nothing runs. No broken layout. |
| TC-57-40 | Screen reader | Error texts are read | With NVDA, press Place order with an empty form. | The first error and the field name are read. After a fix the change is announced. Every error text is connected to its field. |
| TC-57-41 | Keyboard | Fix errors without the mouse | Fix every error only with Tab, Shift+Tab, Enter and arrow keys. | All fields can be reached. The focus ring is visible. The focus does not jump away while typing. |
| TC-57-42 | Layout | Errors at 320, 768, 1200 | Open the form with all errors at the three widths. Compare with Figma "Field validation" (115:6493 / 115:7358 / 115:8223). | The same layout as in Figma. Texts are not cut. No horizontal scroll. Error text does not hide the next field. |
| TC-57-43 | Browsers | Chrome and Firefox | Run TC-57-03, TC-57-11, TC-57-19, TC-57-30 and TC-57-34 in both browsers. | The results are the same. If Firefox is not available, write "Chrome only". |

API checks that Khumoyunmirzo's tests (#164) may not cover (ask him which of these are automated, then run the rest by hand with Swagger or the Network tab):

| ID | Title | Request | Expected |
|---|---|---|---|
| TC-57-A01 | Several problems at once | `POST /orders` with an empty name, a bad phone and a past date together | 400. `fields` has `contactName`, `contactPhone` and `orderDate` together. |
| TC-57-A02 | Pickup with delivery fields | Pickup with a `deliveryZoneId` | 400. A field error. No order. |
| TC-57-A03 | Delivery with café | Delivery with a `cafeId` | 400. A field error. No order. |

## Part 2. Stage 2 regression

Run after the checkout is on `dev`, to see that Stage 2 still works. Test cases are in the files `50-country-switcher.md`, `51-menu.md`, `52-modifiers.md` and `54-cart-validation.md`. Run in Chrome at 1200 px. Run the cases marked 320 also at 320 px. A new problem is a bug under the story of that file.

| ID | Area | What to run | Expected result |
|---|---|---|---|
| TC-57-R01 | Country | TC-50-01, TC-50-03, TC-50-04, TC-50-06, TC-50-07, TC-50-09 | The switcher is in the header on every page. First visit is UZB. The menu, the cart and the currency follow the country. The choice is saved. |
| TC-57-R02 | Country | TC-50-10, TC-50-15, TC-50-16 | Prices have 2 decimals. The cart keeps its items after a country change and after a reload. |
| TC-57-R03 | Country + checkout | Change the country on the checkout page | Totals are requoted. The café, zone and slot of the old country are cleared. |
| TC-57-R04 | Menu | TC-51-01, TC-51-02, TC-51-04, TC-51-06, TC-51-07, TC-51-13 | Categories and cards of the country. Out of stock cannot be added. The category filter works. |
| TC-57-R05 | Menu | TC-51-08, TC-51-09, TC-51-10 | Loading, empty and error states are clear (with text, no "Failed to fetch"). |
| TC-57-R06 | Menu | TC-51-11, TC-51-12 | 320 / 768 / 1200 px as in Figma (the minor bugs of 17 Nov, #213 card layout at 320 and #214 cropped photos, are fixed in f94d04f; check that they stay fixed). |
| TC-57-R07 | Modifiers | TC-52-01, TC-52-03, TC-52-04, TC-52-05, TC-52-07 | The window opens. One SIZE, first size selected, several EXTRA. The price on the button changes. |
| TC-57-R08 | Modifiers | TC-52-10, TC-52-12, TC-52-13, TC-52-14, TC-52-19 | Prices per country. Different modifiers make different lines. Modifiers are shown in the cart. Extra tofu is hidden in CHN. |
| TC-57-R09 | Cart | TC-54-01, TC-54-03, TC-54-05, TC-54-08, TC-54-09, TC-54-10 | Plus and minus, wrong quantities, limit 99, remove, empty state. |
| TC-57-R10 | Cart | TC-54-12, TC-54-13, TC-54-16, TC-54-17 | API errors have a text. The cart works at 320 px. Tap targets are 44 px. |
| TC-57-R11 | Cart | Add dishes, reload on `/cart`, move between pages | The cart is kept (#117). The header counter is right on every page. |
| TC-57-R12 | Cart | Subtotal, VAT, Amount incl. VAT, Discount, Total | The numbers come from the quote and match `55-pricing-matrix.md` for one row per country. |
| TC-57-R13 | Keyboard | TC-50-11, TC-51-14, TC-52-17, TC-54-14 | The country switcher, the menu filter, the modifiers window and the cart work with the keyboard. |
| TC-57-R14 | Previous bugs | #99 (tab titles), #100 (44 px targets at 320), #101 (colour tokens), #138, #139, #197, #198 | None of them came back. (#200, no "+" on modifier prices, is fixed and closed on 22 Nov; the prices now show as "+UZS 12.00".) Tab titles are "Home / Menu / Cart / Checkout / Reports - Wok & Roll". |

## Run log

Run 1 on 22 Nov, dev at f94d04f (#161 merged, real backend). The cafés and time slots APIs (#223, #224) are not merged yet, so Place order is disabled on dev. For the rules below I enabled the button in the browser tools and submitted the form (UZB, one Salmon avocado roll, Delivery).

One line per failed case: ID, expected, actual, request id, bug number.

| ID | Expected | Actual | Request id | Bug |
|---|---|---|---|---|
| none | | No case failed in this run. | | |

Run in this part (22 Nov), Pass:

| ID | Result |
|---|---|
| TC-57-03, 04, 06 | Pass. Empty, 1 character and only spaces: "Enter a name from 2 to 100 characters." |
| TC-57-12, 13, 15, 16 | Pass. Empty, 7 digits, no plus, letters: "Enter a phone number with + and 8 to 15 digits." |
| TC-57-19 | Partly. `not-an-email` gives "Enter a valid email address." The other 4 formats are not run. |
| TC-57-24, 25 | Partly. Empty and 4 characters give "Enter an address from 5 to 200 characters." Spaces only and 201 characters are not run. |
| TC-57-05, 20, 22, 24 (length) | Partly. The fields have a maximum length of 100 (name), 254 (email), 300 (comment) and 200 (address) in the page. Pasting is not run. No counter on the comment. |
| TC-57-34 | Pass. An empty form shows an error at each wrong field, the focus goes to a wrong field and a text above the button ("Check these details before you place your order") lists the errors. No `POST /orders`. |
| TC-57-36 | Pass. The other values stay when one field is wrong. |
| TC-57-26 | Pass for the pickup tab: there is no address field. |

Not run yet (waits for #223 and #224, the real order): TC-57-01, 02, 07 to 11, 14, 17, 18, 21, 23, 29 to 33, 35, 37 to 43, A01 to A03 and part 2.

## Notes

- Out of scope here: totals numbers (#162), pickup, zones and slots in detail (#167), the whole order flow (#165), API rules (#164).
- Answered by the team: the comment field stops at 300 characters and has no counter.
- Error texts seen on dev (22 Nov): name "Enter a name from 2 to 100 characters.", phone "Enter a phone number with + and 8 to 15 digits.", email "Enter a valid email address.", address "Enter an address from 5 to 200 characters.", slot "Choose an available time slot.".
- Minor note: in the general text above the button the errors are listed as name, phone, address, slot, but on the page the address field is above the name field.
