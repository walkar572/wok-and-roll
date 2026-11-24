const { test, expect } = require('@playwright/test');

// Panko, the mascot: in the corner of every page of the shop, not in the admin panel. The texts
// are Uzbek, the language of the first visit.

const panko = 'Panko, Wok & Roll oshpazi';

test('Panko says hello, tells what he will do when clicked, and hides until the next visit', async ({ page }) => {
  await page.goto('/menu');
  const button = page.getByRole('button', { name: panko });
  await expect(button).toBeVisible();
  await expect(button).toHaveAttribute('aria-expanded', 'false');

  await button.click();
  await expect(button).toHaveAttribute('aria-expanded', 'true');
  await expect(page.getByRole('status').filter({ hasText: 'Salom! Men Pankoman' })).toContainText(
    'menyu haqida',
  );

  await page.getByRole('button', { name: 'Pankoni yashirish' }).click();
  await expect(button).toHaveCount(0);
  // hidden for this visit, on every page
  await page.goto('/');
  await expect(page.getByRole('button', { name: panko })).toHaveCount(0);
});

test('Panko is not in the admin panel', async ({ page }) => {
  await page.goto('/admin/login');
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  await expect(page.getByRole('button', { name: panko })).toHaveCount(0);
});
