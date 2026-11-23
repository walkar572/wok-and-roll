const { test, expect } = require('@playwright/test');
const pricingExamples = require('../../../test-data/pricing-examples.json');
const { addFirecrackerUdon } = require('./helpers/cart');

const kaz = pricingExamples.find((example) => example.country === 'KAZ');
if (!kaz) throw new Error('No pricing example found for KAZ.');

test('country switch updates currency and exact menu price, and survives reload', async ({ page }) => {
  const countrySelect = page.getByRole('combobox', { name: /Country/ });

  await page.goto('/menu');
  await expect(countrySelect).toHaveValue('UZB');

  const udon = page.getByRole('article').filter({
    has: page.getByRole('heading', { name: 'Firecracker udon', exact: true }),
  });
  await expect(udon).toContainText('UZS 48.00');

  await countrySelect.selectOption('KAZ');
  await expect(countrySelect).toHaveValue('KAZ');
  await expect(udon).toContainText('KZT 52.00');

  await page.reload();
  await expect(page.getByRole('combobox', { name: /Country/ })).toHaveValue('KAZ');
  await expect(page.getByRole('article').filter({
    has: page.getByRole('heading', { name: 'Firecracker udon', exact: true }),
  })).toContainText('KZT 52.00');

  await addFirecrackerUdon(page);
  await page.getByRole('link', { name: /^Cart,/ }).click();
  await expect(page.getByRole('heading', { name: 'Your cart', exact: true })).toBeVisible();
  await expect(page.getByText('VAT (' + kaz.expected.vatRate + '%)', { exact: true })).toBeVisible();
  await expect(page.getByRole('region', { name: 'Price summary' })).toContainText('KZT 62.40');
});
