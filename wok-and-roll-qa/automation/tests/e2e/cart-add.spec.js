const { test, expect } = require('@playwright/test');
const { addFirecrackerUdon } = require('./helpers/cart');
const { uz } = require('./helpers/text');

test('adding a dish increments the cart counter', async ({ page }) => {
  await page.goto('/menu');
  await addFirecrackerUdon(page);

  const cartLink = page.getByRole('link', { name: uz.cart(1), exact: true });
  await expect(cartLink).toBeVisible();
  await cartLink.click();
  await expect(page.getByRole('heading', { name: uz.cartTitle, exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: uz.udon, exact: true })).toBeVisible();
});

test('cart survives reload', async ({ page }) => {
  await page.goto('/menu');
  await addFirecrackerUdon(page);
  await page.getByRole('link', { name: uz.cart(1), exact: true }).click();

  await page.reload();
  await expect(page.getByRole('heading', { name: uz.cartTitle, exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: uz.udon, exact: true })).toBeVisible();
  await expect(page.getByRole('link', { name: uz.cart(1), exact: true })).toBeVisible();
});
