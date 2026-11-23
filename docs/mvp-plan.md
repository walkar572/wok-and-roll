# MVP plan: Sprints 3–5

Refs #142 · Owner: Maksudbek (Team Lead) · 14 Nov 2026 · Board: GitHub Project 13 (fields Sprint, Start date, Target date) · API: [`api-contract.md`](api-contract.md) v1.1

## 1. Goal

Every MVP feature, base and bonus, is merged by **Thu 26 Nov 20:00**. The Sprint 4 demo on Fri 27 Nov shows the whole MVP. **Sprint 5 is only testing, bug fixing and the defense**: no new features.

| Sprint | Dates | Goal |
|---|---|---|
| Sprint 3 · Stage 3–4 | 16–22 Nov | Checkout works end to end for pickup and delivery: totals by the formula from the API, validation, time slots, the order saved with a number. Auth merged. Report APIs in Swagger, PDF started. |
| Sprint 4 · Stage 4–5 | 23–29 Nov | Reports page with 2 charts, PDF and CSV equal to the database; table booking; log in, My orders and role-based access; admin page if above the cut line. Feature freeze. |
| Sprint 5 · Defense | 30 Nov – 6 Dec | Full regression, accessibility and responsive audit, bug fixes only, release `v1.0.0`, rehearsals, defense. |

## 2. Timeline

| When | What |
|---|---|
| Mon 16 Nov 09:00 | Sprint 3 planning: walk through the contract v1.1 and this plan, then merge them (#142). Merge #134 (auth) and the Stage 2 work that is left first. Unfinished Sprint 2 items move to Sprint 3. |
| Mon 16 Nov 12:00 | Figma links in the Stage 3–4 stories (#170) |
| Tue 17 Nov 09:00 | Cafés, zones, tables migration merged (#143) |
| Tue 17 Nov 20:00 | Order tables (#144), quote totals (#145), cafés and zones APIs (#146, #147) merged |
| Wed 18 Nov 20:00 | Slot reservation service merged (#148); Sprint 3 PRs ready for review |
| Thu 19 Nov 20:00 | Sprint 3 code merged: orders API (#149), checkout page (#157), region report API (#153) |
| Fri 20 Nov 09:00 | Sprint 3 demo and retro |
| Sun 22 Nov 20:00 | Last Sprint 3 items: month report (#154), PDF part 1 (#155), validation UI (#161); 360 form #4 (#48) |
| Mon 23 Nov 09:00 | Sprint 4 planning; sales history seed merged (#171) |
| Wed 25 Nov 12:00 | **Cut-line check** (section 5) |
| Thu 26 Nov 20:00 | **Feature freeze**: the last feature PR is merged |
| Fri 27 Nov 09:00 | Sprint 4 demo (whole MVP) and retro; `main` updated and tagged `v1.0.0-rc1`; Project Report (#74) |
| Mon 30 Nov – Wed 2 Dec | Regression (#71), accessibility and responsive audit (#70), bug fixes only |
| Wed 2 Dec 20:00 | **Code freeze**: the last bug fix is merged |
| Thu 3 Dec | `v1.0.0` tagged on `main`; rehearsal 2 runs on this build (#72, #76) |

## 3. Who builds what

Story points of the sub-issues. Each sub-issue has its owner, sprint, start and target date on the board.

| Person | Sprint 3 | Sprint 4 |
|---|---|---|
| Timur | #144, #145, #149, #150 (11 SP) | #171, #172, #176 (8 SP) |
| Abdulaziz | #143, #146, #147, #148 (9 SP) | #174, #175 (7 SP) |
| Nodir | #151, #153, #154, #155 (11 SP) | #173, #177, #178 (9 SP) |
| Serhii | #156, #157, #158, #159, #160, #161 (11 SP) | #179, #180, #181, #182, #183, #184, #185, #186 (17 SP) |
| Asif | #162, #165, #166, #167 (9 SP) | #187, #188, #191, #192, #194 (6 SP) |
| Khumoyunmirzo | #163, #164, #168 (7 SP) | #169, #189, #190, #193, #195 (10 SP) |
| Komronkhon | #170 (2 SP) | #196 (2 SP) |
| Maksudbek | #142, #152 (5 SP) | — (0 SP) |
| Guzal | Sprint events, demo script, #48 | #73 write-up, #74 Project Report, #49 |

Maksudbek also reviews every PR the same day, keeps the contract and runs #72 (freeze, release). Sprint 5: Asif #70, Khumoyunmirzo #71, everyone fixes their own bugs.

## 4. Critical path

1. **Auth first:** #134 merges on Mon 16 Nov, because the order and booking tables point to `app_user` and the reports need roles.
2. **Checkout:** cafés and zones migration (#143) → order tables (#144) and quote totals (#145) → slot service (#148) → orders API (#149) → checkout page (#157) → E2E (#169).
3. **Reports:** order tables (#144) → report APIs (#153, #154) → seed (#171) → reports page (#180, #181) → PDF (#155, #173) and CSV (#172) → cross-check (#188).
4. **Accounts and roles:** auth hardening (#151) → login page (#179) → My orders (#184), role-based UI (#186), admin page (#185).

Every API is planned to be merged at least one day before its screen. Until then the frontend uses mocks copied from the contract examples.

## 5. Cut line

Checked on **Wed 25 Nov 12:00** by the Team Lead with the board. If Sprint 4 is late, we cut in this order; a cut API stays in Swagger and its screen moves to "after MVP":

1. Admin page UI (#185, #64, +5)
2. Staff API (#176, #65)
3. My orders page (#184, #63, +5)

Never cut: totals, orders and validation (#55–#57), reports with PDF and CSV (#66–#69), pickup, delivery and time slots (#58–#60), booking (#61).

## 6. Rules for these two sprints

- Draft PR on the first day of a task, ready for review by Wed 20:00, merged by Thu 20:00 (Sprint 2 retro).
- `dev` takes only PRs with green CI. A new endpoint is in Swagger and in the contract in the same PR.
- QA tests a task on the day it is merged; E2E and API tests are written together with the feature.
- Stuck for more than 4 hours: `blocked` label, a comment, and tell the Team Lead at the daily.

## 7. Risks

| Risk | What we do |
|---|---|
| Serhii builds all screens: 28 SP in two sprints, much more than in Sprint 2 | Simple screens from the ready components and Radix; the API is merged a day before each screen; the cut line; the Team Lead checks the frontend progress at every daily and decides on help on Wed 18 Nov if the checkout is late |
| Sprints 3 and 4 plan about 60 SP each; in Sprint 2 we merged 24 SP of the 42 planned | Daily check of the board; the cut line on Wed 25 Nov; Sprint 5 has no feature work, so it is a real buffer for bugs |
| Two guests take the last slot or table | Checked in the database (atomic upsert, exclusion constraint) and by concurrency tests (#148, #174, #168) |
| Late merges again | Ready for review Wed 20:00, merged Thu 20:00; Draft PR on day 1 |
| No shared server | QA uses Docker Compose and CI; the release build is tagged |
