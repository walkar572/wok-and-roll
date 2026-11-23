const { test, expect } = require('@playwright/test');
const { addFirecrackerUdon } = require('./helpers/cart');

test('adding a dish increments the cart counter', async ({ page }) => {
  await page.goto('/menu');
  await addFirecrackerUdon(page);

  const cartLink = page.getByRole('link', { name: 'Cart, 1 item', exact: true });
  await expect(cartLink).toBeVisible();
  await cartLink.click();
  await expect(page.getByRole('heading', { name: 'Your cart', exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Firecracker udon', exact: true })).toBeVisible();
});

test('cart survives reload', async ({ page }) => {
  await page.goto('/menu');
  await addFirecrackerUdon(page);
  await page.getByRole('link', { name: 'Cart, 1 item', exact: true }).click();

  await page.reload();
  await expect(page.getByRole('heading', { name: 'Your cart', exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Firecracker udon', exact: true })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Cart, 1 item', exact: true })).toBeVisible();
});
