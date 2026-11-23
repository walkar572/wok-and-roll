# Test Strategy - Team 12 "Wok & Roll"

Issue #21 · Asif Najafov (QA Manual) · v0.4 · 8 November 2026

Board, columns and the bug flow are in `HOW-WE-WORK.md` (sections 4 and 6). This file has only what is special for QA. What I check and when is in `test-plan.md`.

## 1. Goals

In order of importance:

1. The money is correct. Subtotal → VAT → Amount incl. VAT → Discount → Delivery fee → Total is the same on the page, in the API and in the reports. 5 countries, 2 decimals.
2. The base part works: country switcher, menu with modifiers, cart, checkout, reports (web, PDF, CSV).
3. Web page = PDF = CSV = database.
4. WCAG AA basics, works at 320 / 768 / 1200.
5. Automated tests are green on `dev` before `dev` → `main`.

We do not test: real payment, real courier, load, security attacks, mobile apps, other languages.

## 2. Test levels and types

- **Unit** (price logic, validators): developers, every pull request, in CI
- **API, manual** (Swagger, Postman): Asif, from Wednesday in the sprint
- **API, automated:** Khumoyunmirzo, in CI on `dev`
- **E2E, automated** (Playwright): Khumoyunmirzo, green before `dev` → `main`
- **Integration check** (frontend on the real API): Serhii + QA, every Thursday
- **Manual and exploratory** (new tickets, edge cases, error messages): Asif, Thursday evening - Friday morning
- **Price check** (5 countries × discount levels × delivery): Asif, Sprint 3, due 19 Nov
- **Reports check** (web vs PDF vs CSV vs database): Asif + Khumoyunmirzo, Sprint 4, due 26 Nov
- **Design check** (Figma prototype, #40): Asif, with Komronkhon, before the screens go to the frontend
- **Accessibility and responsive:** Asif with Serhii and Komronkhon, every sprint with UI, audit 26 Nov
- **Regression** (checklist + automated suite): both QA, before every `dev` → `main`

Khumoyunmirzo and I use the same test cases. If one of us is away, the other can take over.

## 3. Bugs

Severity (details: `HOW-WE-WORK.md`, section 6). Critical and major block Done.

- **critical:** a main flow is broken, or the money is wrong. Example: the total on the page is not the same as in the API.
- **major:** works in a wrong way, no easy workaround. Example: prices do not change after the country changes.
- **minor:** cosmetic, or there is an easy workaround. Example: wrong spacing at 320 px.

How I open a bug:

1. Use the Bug form: severity, related ticket, steps, expected result, actual result, environment (example: `Country UZB · Chrome 130 · 320 px · build abc123`).
2. Make the bug a **sub-issue of the ticket**.
3. **Assign the developer** who built the ticket.
4. **Critical or major:** current Sprint, status **Ready**. **Minor:** Backlog; the ticket can still go to Done.
5. The developer writes `Refs #<bug>` in the pull request. I retest.

## 4. Entry and exit criteria per sprint

I can start testing when:

- The tickets met the Definition of Ready (acceptance criteria, Figma link, API contract, Estimate).
- The code is in `dev` by Thursday 20:00 and CI is green.
- Swagger is updated and the test data for the 5 countries is loaded.
- The ticket is in **Ready for Test**.

A ticket goes to Done as soon as it passes the test. Merging `dev` into `main` is a separate step. It happens when:

- All tickets of the sprint are tested and the result is written in the issue.
- There are no open critical or major bugs.
- The automated E2E and API tests are green on `dev`.
- The accessibility and responsive checklists passed on the new screens.
- Sprints with money or reports: the price matrix or the reports check is done and the numbers match.
- Minor bugs are in the backlog with a priority.

If this is not true, the Team Lead decides at the demo what moves to the next sprint. We talk about the reason at the retro.

## 5. Environment and tools

- Browsers: **Chrome and Firefox** (decided). Smallest width 320 px.
- Frontend: React + TypeScript + Vite. Backend: Java. Local run with Docker Compose, and a deployed version for the demo.
- E2E: **Playwright (JavaScript)**. Khumoyunmirzo sets it up in Week 1 (#22).
- One shared file with price examples: **#42 Golden pricing test examples** (Asif + Khumoyunmirzo, due 6 Nov). The unit tests, the automated tests and my manual matrix all use it.
- Tools: GitHub Projects, Swagger, Postman, browser dev tools, axe or Lighthouse, Figma, Teams.
- Test data: see `test-plan.md`, section 3.

## 6. Parts that already have stories

Do not make duplicates:

- Country / region → #50
- Price calculation (the total comes from the API) → #55
- Reports → #66, #67, #68, #69
- Modifiers → #52
- Test folder and test script → #41 (Playwright framework)

Files: the QA documents are in `wok-and-roll-qa/manual/`.

The frontend must not calculate the total itself. It shows what the API returns.

## 7. Mentor questions

Issue #44. 11 questions, posted in Teams for the Q&A on Monday 2 Nov, 18:00. Full list: `mentor-questions.md`. The three blocking ones (currency of the discount levels, rounding, currency of the delivery fee) are answered in the **Decision Log**; I use the answers from there. Two more are decided: the cart keeps its items and recalculates in the new currency when the country changes, and we support Chrome and Firefox.
