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

test('the menu marks spicy and vegetarian dishes; the tab title has a short dash', async ({ page }) => {
  await page.goto('/menu');
  await expect(page).toHaveTitle(/ - Wok & Roll$/);
  // contract v1.16: red for spicy, green for vegetarian, under the dish
  await expect(page.locator('.menu-card .dish-mark--spicy').first()).toHaveText('Achchiq');
  await expect(page.locator('.menu-card .dish-mark--vegetarian').first()).toHaveText('Vegetarian');
});
