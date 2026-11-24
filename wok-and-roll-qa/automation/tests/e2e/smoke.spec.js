const { test, expect } = require('@playwright/test');
const { uz } = require('./helpers/text');
const { countrySwitcher } = require('./helpers/country');

test('home shows the country and menu page opens', async ({ page }) => {
  await page.goto('/');
  await expect(page).toHaveTitle(/.+/);
  await expect(countrySwitcher(page)).toHaveText('UZB');

  await page.goto('/menu');
  await expect(page).toHaveURL(/\/menu\/?$/);
  await expect(page.getByRole('heading', { name: uz.menuTitle, exact: true })).toBeVisible();
});
