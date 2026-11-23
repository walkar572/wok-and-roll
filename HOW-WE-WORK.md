# How we work in GitHub (our "Jira")

**Team 12 · Wok & Roll.** Reading time: about 4 minutes. If you read only one part, read **3. Your daily routine**.

**Board:** https://github.com/orgs/ITPU-Teamwork-2026/projects/13
**Questions:** ask Guzal (backlog and process) or Maksudbek (technical) in Teams.

---

## 1. Jira words → GitHub words

| In Jira | In our GitHub | How to spot it |
|---|---|---|
| Epic | Issue, type **Feature** | label `epic`, title starts with `[Epic N]` |
| Story | Issue, type **Task** | label `user-story`, sub-issue of an epic |
| Sub-task | Issue, type **Task** | sub-issue of a story or epic |
| Bug | Issue, type **Bug** | label `severity: …` |
| Sprint | **Sprint** field | Sprint 0 … Sprint 5 (one week each) |
| Component | `area: …` label | e.g. `area: cart`, `area: reports` |
| Release / stage | **Milestone** | Week 1, Stage 1–5, Defense |
| Story points | **Estimate** field | 1, 2, 3, 5, 8 |
| Board | **Sprint Board** view | current sprint only |

**Rule #1: if it is not on the board, it is not planned.**

---

## 2. The board views (tabs at the top)

- **Sprint Board:** what we do this week. Start here every day.
- **Product Backlog:** everything not done yet, grouped by stage. Guzal keeps the top ready.
- **By Role:** who has how much work this sprint (left panel = people).
- **Roadmap:** epics and stories on a timeline.
- **My items:** only your work.

---

## 3. Your daily routine (5 minutes)

1. Open **My items** or **Sprint Board**.
2. **Pick a task:** take one in **Ready** from the current sprint. Assign yourself and move it to **In progress**.
3. **Create a branch** with the issue number: `feature/25-country-switcher` or `fix/40-cart-counter`.
4. **Open a pull request.** In the description write `Refs #25` and fill the Definition of Done checklist. Move the issue to **In review**.
5. **After the merge**, move the issue to **Ready for Test** so QA can pick it up.
6. **Before the 19:00 daily**, make sure your cards show the truth.

> Use `Refs #25`, **not** `Closes #25`. "Closes" would close the issue on merge and skip QA.

**Stuck for more than 4 hours?** Add the `blocked` label, write the reason as a comment, tag the person and Maksudbek, and say it at the daily.

---

## 4. Status columns: who moves what

| Status | Meaning | Who moves it here |
|---|---|---|
| Backlog | Not planned yet | Auto (new issues) / Guzal |
| Ready | Clear, small, estimated, in this sprint | Guzal at planning |
| In progress | Someone is working on it (max 2 per person) | Owner |
| In review | PR is open and waits for approval | Owner |
| Ready for Test | Merged, waiting for QA | Owner |
| Testing | QA is checking it | Asif / Khumoyunmirzo |
| Done | QA approved. The issue closes automatically | QA |

**QA found a problem?** QA opens a **Bug** (see 6) and moves the task back to **In progress**.

---

## 5. Creating an issue

1. **Issues → New issue → choose a form:** User story, Task, Bug or Epic.
2. **Fill the form.** Keep a task to **one day or less**; if it is bigger, split it.
3. **On the right side, set:**
   - **Assignees:** one owner (a second person only if you really pair).
   - **Labels:** one `area: …` label.
   - **Milestone:** the stage.
   - **Relationships → Parent:** the story or epic it belongs to.
4. **In the project panel, set:** **Role**, **Priority**, **Target date** (due date). Leave **Sprint** empty; we choose it at Monday planning.

**Priority:** High = base features (300 points) · Medium = bonus · Low = nice to have · Urgent = blocker or critical bug.

---

## 6. Bugs (mostly QA)

- **Open it:** use the **Bug report** form, choose the severity, link the story (`#25`), add steps, expected vs actual, and a screenshot.
- **Severity:**
  - **critical** = a main flow is broken or money totals are wrong.
  - **major** = works wrong, no easy workaround.
  - **minor** = cosmetic.
- **Critical and major bugs block Done.** Fix them before new work.

---

## 7. Our week

| When (Tashkent time) | What |
|---|---|
| Mon 09:00 | Sprint planning: pick work from Product Backlog into the sprint, set the sprint goal |
| Mon–Fri 19:00 | Daily stand-up in Teams, 15 min: what I did, what I will do, what blocks me |
| Thu 20:00 | All sprint code is merged, so QA can test |
| Fri 09:00 | Demo + retro |
| Sun 23:59 | 360 form submitted |

**Estimates:** at planning we vote with planning poker (1, 2, 3, 5, 8). Anything above 8 gets split.

---

## 8. Five simple rules

1. **One owner per task.** The owner keeps the status up to date the same day.
2. **Decisions about a task go in the issue comments**, not in private chats, so everyone can see them.
3. **Don't move someone else's card** without asking them.
4. **No PR merges without 1 approval and green CI.**
5. **Can't make a deadline?** Tell Maksudbek at least 24 hours before.

Full rules (Definition of Ready / Done, RACI): Project Plan in Google Drive › *T12 Wok & Roll*.

---

## 9. Commits, authorship and AI

The technical mentor grades each person's code **by the commit history**. So:

1. **Push at least once per working day.** Open a **Draft PR on the first day** (`Refs #N`) and mark it "Ready for review" when you finish.
2. **Commit only your own work, from your own account.** If you use a teammate's code or patch, add `Co-authored-by: Name <email>` to the commit message, or ask them to push it themselves.
3. **AI tools are allowed.** You must understand every line you push and be able to explain it in a code review.
4. **Don't build complex things from scratch.** Use the libraries in [`docs/architecture.md`](docs/architecture.md).

Decisions and API rules: [`docs/decision-log.md`](docs/decision-log.md) · [`docs/architecture.md`](docs/architecture.md).
