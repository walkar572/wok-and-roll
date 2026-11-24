const { test, expect } = require('@playwright/test');
const { uz } = require('./helpers/text');
const { countrySwitcher } = require('./helpers/country');

test('the header stays on screen while the page scrolls, with the basket one tap away', async ({ page }) => {
  await page.goto('/');
  // The page is long only when the cafés and the favourites are there.
  await expect(page.getByRole('region', { name: uz.cafesTitle }).getByRole('article')).toHaveCount(2);
  await expect(page.locator('.favorite-card')).toHaveCount(4);
  await page.locator('footer').scrollIntoViewIfNeeded();
  await expect.poll(() => page.evaluate(() => window.scrollY)).toBeGreaterThan(1000);

  const header = page.locator('header.site-header');
  await expect.poll(async () => (await header.boundingBox())?.y).toBe(0);
  await page.getByRole('link', { name: uz.cartLink }).click();
  await expect(page.getByRole('heading', { name: uz.cartTitle, exact: true })).toBeVisible();
});

test.describe('mobile menu', () => {
  test.use({ viewport: { width: 390, height: 760 } });

  test('opens under its button, and the country list never opens over it', async ({ page }) => {
    await page.goto('/');
    const toggle = page.getByRole('button', { name: uz.openMenu });
    await toggle.click();

    const menu = page.locator('#mobile-navigation');
    await expect(menu).toBeVisible();
    await expect(page.getByRole('button', { name: uz.closeMenu })).toHaveAttribute('aria-expanded', 'true');
    await expect(menu.getByRole('link', { name: uz.menuTitle, exact: true })).toBeVisible();

    // The country list closes the menu: the two never cover each other.
    await countrySwitcher(page).click();
    await expect(page.getByRole('listbox')).toBeVisible();
    await expect(menu).toBeHidden();
    await page.keyboard.press('Escape');
    await expect(page.getByRole('listbox')).toBeHidden();

    // Escape closes the menu and gives the focus back to its button.
    await toggle.click();
    await expect(menu).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(menu).toBeHidden();
    await expect(toggle).toBeFocused();
  });
});
