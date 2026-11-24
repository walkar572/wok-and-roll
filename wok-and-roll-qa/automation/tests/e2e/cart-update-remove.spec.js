const { test, expect } = require('@playwright/test');
const { addFirecrackerUdon } = require('./helpers/cart');
const { uz } = require('./helpers/text');

test('cart quantity updates, values below one are blocked, and remove shows empty state', async ({ page }) => {
  await page.goto('/menu');
  await addFirecrackerUdon(page);
  await page.getByRole('link', { name: uz.cartLink }).click();

  const cartLine = page.getByRole('article').filter({
    has: page.getByRole('heading', { name: uz.udon, exact: true }),
  });
  const quantity = cartLine.getByRole('spinbutton', { name: uz.quantityOf(uz.udon) });
  await expect(quantity).toHaveValue('1');

  await quantity.fill('3');
  await expect(quantity).toHaveValue('3');
  await expect(page.getByRole('link', { name: uz.cart(3), exact: true })).toBeVisible();

  await quantity.fill('0');
  const quantityMessage = cartLine.getByText(uz.quantityRule);
  await expect(quantityMessage).toBeVisible();
  await expect(quantity).toHaveAttribute('aria-invalid', 'true');
  await expect(quantity).toHaveAccessibleDescription(uz.quantityRule);
  await expect(page.getByRole('link', { name: uz.cart(3), exact: true })).toBeVisible();

  await quantity.blur();
  await expect(quantity).toHaveValue('3');
  await expect(quantityMessage).toContainText(uz.quantityStays(3));

  await cartLine.getByRole('button', { name: uz.removeOf(uz.udon), exact: true }).click();
  await expect(page.getByRole('heading', { name: uz.emptyCart, exact: true })).toBeVisible();
  await expect(page.getByRole('link', { name: uz.cart(0), exact: true })).toBeVisible();
});
