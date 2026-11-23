# Test automation (Khumoyunmirzo)

Playwright (JavaScript) project for E2E and API tests. Decision: [#22](https://github.com/ITPU-Teamwork-2026/t12-wok-and-roll/issues/22). Setup: [#41](https://github.com/ITPU-Teamwork-2026/t12-wok-and-roll/issues/41).

Suggested layout (create it in #41):

```
automation/
  tests/e2e/        country -> menu -> cart -> checkout -> order
  tests/api/        endpoints, status codes, validation errors
  playwright.config.js
  package.json
```

- Expected prices come from [`../test-data/`](../test-data/), never from numbers typed in the test.
- Tests run in CI on every PR into `dev`. Keep them green.
