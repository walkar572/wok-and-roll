const { test, expect } = require('@playwright/test');
const { uz } = require('./helpers/text');

// The map itself needs WebGL and the OpenFreeMap tiles; the list next to it does not. The tests
// use the list, which is the accessible way to the same cafés.
test('home lists the cafés of the country and books a table at the chosen one', async ({ page }) => {
  await page.goto('/');
  const section = page.getByRole('region', { name: uz.cafesTitle });
  await expect(section.getByRole('heading', { level: 2, name: uz.cafesTitle })).toBeVisible();

  const cafes = section.getByRole('article');
  await expect(cafes).toHaveCount(2);
  await expect(cafes.nth(0).getByRole('heading', { level: 3 })).toHaveText(uz.chilonzor);
  await expect(cafes.nth(1).getByRole('heading', { level: 3 })).toHaveText(uz.yunusobod);
  await expect(cafes.nth(1)).toContainText(uz.cafeHours);
  await expect(cafes.nth(1)).toContainText(uz.cafeStatus);

  await section.getByRole('link', { name: uz.bookAt(uz.yunusobod) }).click();
  await expect(page).toHaveURL(/\/booking\?cafe=\d+$/);
  const cafe = page.locator('#booking-cafe');
  await expect(cafe).toBeVisible();
  await expect(cafe.locator('option:checked')).toHaveText(uz.yunusobod);
});

test('"View the menu" next to the favourites opens the menu', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('link', { name: uz.viewMenu }).click();
  await expect(page).toHaveURL(/\/menu\/?$/);
  await expect(page.getByRole('heading', { name: uz.menuTitle, exact: true })).toBeVisible();
});
