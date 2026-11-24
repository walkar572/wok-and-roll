const { test, expect } = require('@playwright/test');
const { uz } = require('./helpers/text');
const { citySwitcher, languageSwitcher } = require('./helpers/region');

test('home shows the city and the language, and the menu page opens', async ({ page }) => {
  await page.goto('/');
  await expect(page).toHaveTitle(/.+/);
  await expect(citySwitcher(page)).toHaveText(uz.tashkent);
  await expect(languageSwitcher(page)).toHaveText('UZ');

  await page.goto('/menu');
  await expect(page).toHaveURL(/\/menu\/?$/);
  await expect(page.getByRole('heading', { name: uz.menuTitle, exact: true })).toBeVisible();
});
