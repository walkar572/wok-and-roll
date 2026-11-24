const { test, expect } = require('@playwright/test');
const { countrySelect, uz } = require('./helpers/text');

test('home shows the country and menu page opens', async ({ page }) => {
  await page.goto('/');
  await expect(page).toHaveTitle(/.+/);
  await expect(page.getByRole('combobox', { name: countrySelect })).toHaveValue('UZB');

  await page.goto('/menu');
  await expect(page).toHaveURL(/\/menu\/?$/);
  await expect(page.getByRole('heading', { name: uz.menuTitle, exact: true })).toBeVisible();
});
