const { test, expect } = require('@playwright/test');
const { addFirecrackerUdon } = require('./helpers/cart');

test('cart quantity updates, values below one are blocked, and remove shows empty state', async ({ page }) => {
  await page.goto('/menu');
  await addFirecrackerUdon(page);
  await page.getByRole('link', { name: /^Cart,/ }).click();

  const cartLine = page.getByRole('article').filter({
    has: page.getByRole('heading', { name: 'Firecracker udon', exact: true }),
  });
  const quantity = cartLine.getByRole('spinbutton', { name: 'Quantity for Firecracker udon' });
  await expect(quantity).toHaveValue('1');

  await quantity.fill('3');
  await expect(quantity).toHaveValue('3');
  await expect(page.getByRole('link', { name: 'Cart, 3 items', exact: true })).toBeVisible();

  await quantity.fill('0');
  const quantityMessage = cartLine.getByText(/^Enter a whole number from 1 to 99\./);
  await expect(quantityMessage).toBeVisible();
  await expect(quantity).toHaveAttribute('aria-invalid', 'true');
  await expect(quantity).toHaveAccessibleDescription(/Enter a whole number from 1 to 99\./);
  await expect(page.getByRole('link', { name: 'Cart, 3 items', exact: true })).toBeVisible();

  await quantity.blur();
  await expect(quantity).toHaveValue('3');
  await expect(quantityMessage).toContainText('The quantity stays 3.');

  await cartLine.getByRole('button', { name: 'Remove', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Your cart is waiting', exact: true })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Cart, 0 items', exact: true })).toBeVisible();
});
