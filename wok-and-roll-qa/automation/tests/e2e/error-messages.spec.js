const { test, expect } = require('@playwright/test');
const { addFirecrackerUdon } = require('./helpers/cart');
const { uz } = require('./helpers/text');

// Refs #197: a failed request shows a clear text for the guest, never "Failed to fetch".
const productsList = /\/api\/v1\/products(\?|$)/;

test('menu shows a clear message when the products request fails', async ({ page }) => {
  await page.route(productsList, (route) => route.abort());
  await page.goto('/menu');

  const error = page.getByRole('alert');
  await expect(error).toContainText(uz.menuError);
  await expect(error).not.toContainText('Failed to fetch');
  await expect(page.getByRole('button', { name: uz.tryAgain, exact: true })).toBeVisible();
});

test('cart shows a clear message and no old totals when the quote request fails', async ({ page }) => {
  await page.goto('/menu');
  await addFirecrackerUdon(page);
  await page.route('**/api/v1/cart/quote', (route) => route.abort());
  await page.getByRole('link', { name: uz.cartLink }).click();

  const error = page.getByRole('alert');
  await expect(error).toContainText(uz.pricesError);
  await expect(error).not.toContainText('Failed to fetch');
  await expect(page.getByRole('region', { name: uz.priceSummary })).toHaveCount(0);
});
