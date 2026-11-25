const { test, expect } = require('@playwright/test');

// Errors of the checkout and the booking forms against the real API (contract 3.5, 3.10).
// English, Tashkent: the texts are those of en.ts.

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('selectedLanguage', 'en-GB');
    localStorage.setItem('selectedCountry', 'UZB');
    localStorage.setItem('selectedCity', 'Tashkent');
  });
});

/** The texts that describe the focused field (its error among them). */
function focusedDescription(page) {
  return page.evaluate(() =>
    (document.activeElement.getAttribute('aria-describedby') || '')
      .split(/\s+/)
      .filter(Boolean)
      .map((id) => document.getElementById(id)?.textContent || '')
      .join(' '),
  );
}

test('the error list at the top starts with the field that gets the focus (#161)', async ({ page }) => {
  await page.goto('/menu');
  const card = page.locator('.menu-card').filter({ hasNot: page.locator('.menu-card__stock') }).first();
  await card.locator('.menu-card__action').click();
  await page.locator('dialog.modal[open]').getByRole('button', { name: /^Add/ }).click();
  await page.goto('/checkout');

  for (const type of ['Collection', 'Delivery']) {
    await page.getByRole('radio', { name: type }).click();
    await page.getByRole('button', { name: 'Place order' }).click();
    const list = page.getByRole('alert').filter({ hasText: 'Check these details' }).getByRole('listitem');
    await expect(list.first()).toBeVisible();
    expect(await focusedDescription(page), type).toContain(await list.first().innerText());
  }
});

test('no free table: the form stays, and every attempt has its own Idempotency-Key (#183)', async ({ page }) => {
  const keys = [];
  await page.route('**/api/v1/bookings', (route) => {
    if (route.request().method() !== 'POST') return route.continue();
    keys.push(route.request().headers()['idempotency-key']);
    // as the API answers it, here also without field errors
    return route.fulfill({
      status: 409,
      json: { code: 'NO_TABLE_AVAILABLE', message: 'No free table', fields: keys.length === 1 ? { time: 'No free table' } : {} },
    });
  });

  await page.goto('/booking');
  const cafe = page.getByLabel('Café', { exact: true });
  await expect(cafe.locator('option').nth(1)).toBeAttached();
  if (!(await cafe.inputValue())) await cafe.selectOption({ index: 1 });
  const time = page.getByLabel('Time', { exact: true });
  await expect(time).toBeEnabled();
  const free = await time.evaluate((el) => [...el.options].find((o) => o.value && !o.disabled).value);
  await time.selectOption(free);
  await page.getByLabel('Guests').fill('3');
  await page.getByLabel('Name', { exact: true }).fill('Test Guest');
  await page.getByLabel('Phone', { exact: true }).fill('+998901234567');

  const book = page.getByRole('button', { name: 'Book a table' });
  for (let attempt = 1; attempt <= 3; attempt += 1) {
    await book.click();
    await expect.poll(() => keys.length).toBe(attempt);
    await expect(page.getByText('There is no free table for this time and group.').first()).toBeVisible();
  }

  await expect(page.getByLabel('Guests')).toHaveValue('3');
  await expect(page.getByText('See you soon!')).toHaveCount(0);
  expect(new Set(keys).size).toBe(3);
});
