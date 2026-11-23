const { test, expect } = require('@playwright/test');

test('home shows the country and menu page opens', async ({ page }) => {
  await page.goto('/');
  await expect(page).toHaveTitle(/.+/);
  await expect(page.getByRole('combobox', { name: 'Country' })).toHaveValue('UZB');

  await page.goto('/menu');
  await expect(page).toHaveURL(/\/menu\/?$/);
  await expect(page.getByRole('heading', { name: 'The menu', exact: true })).toBeVisible();
});
