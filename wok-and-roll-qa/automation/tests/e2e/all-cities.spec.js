const { test, expect } = require('@playwright/test');
const { uz } = require('./helpers/text');
const { chooseCity, citySwitcher } = require('./helpers/region');

// "All cities" (contract v1.12): every café of every city on the home page, but no prices and no
// ordering until one city is chosen, so nothing can be ordered "in all cities".

test('All cities shows every café and asks for a city before the menu', async ({ page }) => {
  await page.goto('/');
  await citySwitcher(page).click();
  await page.getByRole('option', { name: new RegExp('^' + uz.allCities) }).click();
  await expect(citySwitcher(page)).toHaveText(uz.allCities);

  // the 14 cafés of the 7 cities, city by city
  const cafes = page.getByRole('region', { name: uz.cafesTitle });
  await expect(cafes.getByRole('article')).toHaveCount(14);
  await expect(cafes.getByRole('heading', { level: 3, name: uz.boston })).toBeVisible();

  // no dishes and no prices in the menu: the city list instead
  await page.goto('/menu');
  await expect(page.getByRole('heading', { name: uz.chooseCity })).toBeVisible();
  await expect(page.getByRole('article')).toHaveCount(0);

  // the choice survives a reload, and one tap on a city brings the dishes in its currency
  await page.reload();
  await expect(citySwitcher(page)).toHaveText(uz.allCities);
  await page.getByRole('button', { name: new RegExp('^' + uz.boston) }).click();
  await expect(citySwitcher(page)).toHaveText(uz.boston);
  await expect(page.getByRole('article').first()).toContainText('$');
});

test('a city chosen in the header ends All cities', async ({ page }) => {
  await page.goto('/');
  await citySwitcher(page).click();
  await page.getByRole('option', { name: new RegExp('^' + uz.allCities) }).click();
  await chooseCity(page, uz.london);
  await page.goto('/menu');
  await expect(page.getByRole('heading', { name: uz.chooseCity })).toHaveCount(0);
  await expect(page.getByRole('article').first()).toContainText('£');
});
