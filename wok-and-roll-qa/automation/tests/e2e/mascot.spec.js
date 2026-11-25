const { test, expect } = require('@playwright/test');

// Panko, the mascot: in the corner of every page of the shop, not in the admin panel. The texts
// are Uzbek, the language of the first visit.

const panko = 'Wok & Roll oshpazi Panko bilan chat';

test('a click on Panko opens the chat beside him; the arrow in the corner tucks him away and brings him back', async ({
  page,
}) => {
  await page.goto('/menu');
  const button = page.getByRole('button', { name: panko });
  await expect(button).toBeVisible();
  await expect(button).toHaveAttribute('aria-expanded', 'false');

  // the chat opens and Escape closes it, back to Panko (panko-chat.spec.js: the chat itself)
  await button.click();
  await expect(button).toHaveAttribute('aria-expanded', 'true');
  const chat = page.getByRole('dialog', { name: 'Panko' });
  await expect(chat).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(chat).toHaveCount(0);
  await expect(button).toBeFocused();
  await button.click();
  await expect(chat).toBeVisible();

  // the arrow button hides him, and it is remembered on every page, also after a reload
  await page.getByRole('button', { name: 'Pankoni yashirish' }).click();
  await expect(button).toHaveCount(0);
  await expect(chat).toHaveCount(0);
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
