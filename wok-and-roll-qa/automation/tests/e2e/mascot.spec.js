const { test, expect } = require('@playwright/test');

// Panko, the mascot: in the corner of every page of the shop, not in the admin panel. The texts
// are Uzbek, the language of the first visit.

const panko = 'Panko, Wok & Roll oshpazi';

test('Panko says what he will do when clicked; the arrow in the corner tucks him away and brings him back', async ({
  page,
}) => {
  await page.goto('/menu');
  const button = page.getByRole('button', { name: panko });
  await expect(button).toBeVisible();
  await expect(button).toHaveAttribute('aria-expanded', 'false');

  await button.click();
  await expect(button).toHaveAttribute('aria-expanded', 'true');
  await expect(page.getByRole('status').filter({ hasText: 'Salom! Men Pankoman' })).toContainText(
    'menyu haqida',
  );

  // the arrow button hides him, and it is remembered on every page, also after a reload
  await page.getByRole('button', { name: 'Pankoni yashirish' }).click();
  await expect(button).toHaveCount(0);
  await page.goto('/');
  await page.reload();
  await expect(page.getByRole('button', { name: panko })).toHaveCount(0);

  // the same button brings him back
  const show = page.getByRole('button', { name: 'Pankoni koʻrsatish' });
  await expect(show).toHaveAttribute('aria-expanded', 'false');
  await show.click();
  await expect(page.getByRole('button', { name: panko })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Pankoni yashirish' })).toHaveAttribute(
    'aria-expanded',
    'true',
  );
});

test('Panko is not in the admin panel', async ({ page }) => {
  await page.goto('/admin/login');
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  await expect(page.getByRole('button', { name: panko })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Pankoni yashirish' })).toHaveCount(0);
});
