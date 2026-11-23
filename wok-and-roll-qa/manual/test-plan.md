# Test Plan and Checklists - Team 12 "Wok & Roll"

Issue #39 · Asif Najafov (QA Manual) · v0.3 · Due 4 November 2026

How we test is in `test-strategy.md` (same folder). Board and bug flow are in `HOW-WE-WORK.md`. This file is what I check and when.

Decisions from the mentor Q&A (blocking questions 1-3: currency of the discount levels, rounding, currency of the delivery fee) are in the **Decision Log**. I use them from there and do not copy them here.

## 1. My sprint

- **Monday, after planning:** I read the tickets. A ticket without acceptance criteria goes back now, not on Thursday.
- **Tuesday - Wednesday:** I test the API that is already in `dev`.
- **Thursday 20:00:** the code is in `dev`, CI is green, the owners move their tickets to **Ready for Test**.
- **Thursday evening - Friday morning:** I take tickets from Ready for Test and move them to Testing. New tickets first, then regression, then UI / accessibility / responsive.
- **A ticket goes to Done as soon as it passes** (Testing → Done). I do not wait for the demo.
- **Friday 09:00, demo:** short QA summary - what is tested, open bugs by severity.
- **`dev` → `main`** is a separate step, after the exit criteria in the strategy (section 4).
- **Friday, retro:** one QA action with an owner.

If a ticket is still In review on Friday morning, I say it at the Thursday daily.

## 2. What I test in each sprint

- **Sprint 1 (2-8 Nov):** design check of the Figma prototype (#40), code check of the first frontend components (#37), Swagger is reachable, the checklists below start.
- **Sprint 2 (9-15 Nov):** country switcher (#50), menu, modifiers (#52), cart. Due 12 Nov.
- **Sprint 3 (16-22 Nov):** correct totals (#55), checkout and errors, pickup / delivery, time slots. Price matrix due 19 Nov.
- **Sprint 4 (23-29 Nov):** reports (#66-#69), table booking, account, admin, roles. Due 26 Nov: reports check, accessibility and responsive audit, code freeze.
- **Sprint 5 (30 Nov - 6 Dec):** the demo script as a test case.

Who to ask: the **modifiers API** is Nodir (#52). The **CSV export** is Timur (#69). For other tickets, see the Assignee.

## 3. Test data and browsers

**Browsers:** Chrome and Firefox (decided). Every checklist runs in both, at 320, 768 and 1200 px.

**Data:**

- 5 countries (UZB, KAZ, GEO, UKR, CHN) with prices and currency
- A product that exists only in some countries, one that is out of stock, one with several modifiers
- Orders around every discount level: just below, exactly on, just above
- Several months of sales for the reports
- Price examples from #42

## 4. Checklists

Pass or fail on every line. A failed line becomes a Bug. I run these on every new or changed screen.

**UI consistency**

- [ ] Colours, fonts and spacing match the design system and the Figma frame
- [ ] The same component looks the same everywhere (Button, Input, Modal)
- [ ] All states exist: hover, focus, disabled, loading
- [ ] Empty state: empty cart, no products in this country, no report data
- [ ] Loading and error state for every API call, with a message a guest understands
- [ ] Prices: currency of the chosen country, 2 decimals, same format on card, cart, checkout, report, PDF, CSV
- [ ] The cart counter in the header updates at once
- [ ] One wording style ("Add to cart" everywhere)

**Accessibility (WCAG AA basics)**

- [ ] Text contrast at least 4.5:1 (big text 3:1)
- [ ] Everything works with the keyboard only (Tab, Enter, Space, Esc), no keyboard trap
- [ ] The focus ring is always visible, tab order follows the screen
- [ ] Every input has a real label, not only a placeholder
- [ ] Errors are text and tied to the field, not only a red colour
- [ ] Images have `alt`, headings are in order, buttons are `<button>`
- [ ] The page has `lang` and its own title
- [ ] Zoom 200% does not break the layout
- [ ] Charts have a text alternative (table or short summary)
- [ ] axe or Lighthouse scan done and the findings checked

**Responsive (check at 320, 768 and 1200)**

- [ ] No horizontal scroll, nothing cut or covered
- [ ] Long product names and long totals do not break the card
- [ ] Navigation works (burger or stacked)
- [ ] Cart and checkout form can be used at 320, the main button is reachable without zoom
- [ ] Tap targets are about 44×44 px
- [ ] Modals and dropdowns fit the screen
- [ ] Tables and charts scroll or restructure

**Cart in browser storage**

- [ ] The cart survives a reload and closing the browser
- [ ] Two tabs: the cart is not wrong or duplicated
- [ ] Country change: the cart keeps its items and recalculates the prices in the new currency (decided)
- [ ] Broken or empty saved data does not crash the page
- [ ] A product that is no longer available is not shown with the old price
- [ ] Logout does not leave another user's cart on the device

**Regression (before every `dev` → `main`)**

- [ ] Choose a country: prices and currency change
- [ ] Modifiers change the price
- [ ] Cart: counter, quantity, remove; quantity below 1 is blocked
- [ ] Checkout: clear errors, a correct order is created
- [ ] Page total = API total
- [ ] Reports load, filters work, the chart matches the data
- [ ] PDF and CSV have the same numbers as the page
- [ ] Login, register, own orders only; a normal user cannot open the admin pages

## 5. Price matrix (due 19 Nov, #55 and #42)

For each of the 5 countries:

- One product, quantity 1, no discount, no delivery
- Quantity 3, and with modifiers (modifiers are part of the unit price, then × quantity)
- VAT of the country (UZB 15%, KAZ 20%, GEO 12%, UKR 8%, CHN 18%)
- Discount just below, exactly on and just above every level (the level is chosen by Amount incl. VAT; the currency of the levels is in the Decision Log)
- Delivery is added last and does not change the discount level; pickup has no fee
- Rounding to 2 decimals (the rule is in the Decision Log)
- Country change: the cart keeps its items, everything recalculates in the new currency, no old currency is left
- Page = API = database

The expected numbers come from #42, not from my own calculation.

## 6. Reports check (due 26 Nov)

For the same filter, the numbers must be the same in four places: the chart, the PDF, the CSV and a direct database query. I check: one region, all regions, a period with no data, a period across a month, the grouping by month. Any difference is a **critical** bug.

## 7. How I open a bug

I use the Bug form (severity, related ticket, steps, expected result, actual result, environment). Then:

1. Make the bug a **sub-issue of the ticket**.
2. **Assign the developer** who built the ticket.
3. **Critical or major:** current Sprint, status **Ready**. **Minor:** Backlog; the ticket can still go to Done.

## 8. Approval

- Author: Asif Najafov (QA Manual)
- Consulted: Guzal
- Approved by: Maksudbek, 8 Nov

Definition of Done for #39: the plan is in `wok-and-roll-qa/manual/`, linked in the issue, reviewed by the Team Lead, and used in the Sprint 1 testing.
