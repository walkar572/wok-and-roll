# Decision Log

## 2026-11-01 — QA automation approach (Issue #22)

- **Status:** Proposed for team review
- **Owner and accountable:** Khumoyunmirzo Makhsudov (QA Automation)
- **Consulted:** Asif Najafov, Maksudbek Makhmudov

### Decision

Use Playwright with JavaScript for end-to-end browser tests and API tests. Use `@axe-core/playwright` for automated accessibility scans on the main user flows.

### Test organization

Keep all automation code under `wok-and-roll-qa/automation/`. Test folders must not be created at the repository root.

- `wok-and-roll-qa/automation/tests/e2e/` — browser end-to-end tests
- `wok-and-roll-qa/automation/tests/api/` — API tests
- `wok-and-roll-qa/automation/tests/accessibility/` — accessibility tests
- `wok-and-roll-qa/automation/tests/fixtures/` — shared test fixtures
- `wok-and-roll-qa/automation/tests/helpers/` — reusable helpers

Name test files by feature and use the `.spec.js` suffix.

### CI

Run the Playwright checks in GitHub Actions for **pull requests into `dev`**. The workflow should report a green check before merge and publish the Playwright HTML report when a run fails. Do not describe this decision as running on pushes to `main`.

### Rationale and implementation

Playwright supports browser and API checks in one JavaScript-based tool, fits the React frontend, and can run in GitHub Actions. axe-core adds repeatable accessibility checks. Wire the agreed commands into package scripts when the automation scaffold is added.
