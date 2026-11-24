const { test, expect } = require('@playwright/test');
const pricingExamples = require('../../../test-data/pricing-examples.json');
const { addFirecrackerUdon } = require('./helpers/cart');
const { countrySelect: countrySelectName, kk, uz } = require('./helpers/text');

const kaz = pricingExamples.find((example) => example.country === 'KAZ');
if (!kaz) throw new Error('No pricing example found for KAZ.');

// Intl puts a no-break space between thousands, so the patterns use \s.
const udonInUzbekistan = /55\s000,00\s*soʻm/;
const udonInKazakhstan = /2\s190,00\s*₸/;
// One udon in KAZ: 2,190.00 + VAT 20 % 438.00 = 2,628.00, below the 4,000 threshold: no discount.
const cartTotalInKazakhstan = /2\s628,00\s*₸/;

test('country switch changes the language, the currency and the exact price, and survives reload', async ({ page }) => {
  const countrySelect = page.getByRole('combobox', { name: countrySelectName });

  await page.goto('/menu');
  await expect(countrySelect).toHaveValue('UZB');
  await expect(page.locator('html')).toHaveAttribute('lang', /^uz/);

  const udon = (text) => page.getByRole('article').filter({
    has: page.getByRole('heading', { name: text.udon, exact: true }),
  });
  await expect(udon(uz)).toContainText(udonInUzbekistan);

  await countrySelect.selectOption('KAZ');
  await expect(countrySelect).toHaveValue('KAZ');
  await expect(page.locator('html')).toHaveAttribute('lang', /^kk/);
  await expect(udon(kk)).toContainText(udonInKazakhstan);

  await page.reload();
  await expect(page.getByRole('combobox', { name: countrySelectName })).toHaveValue('KAZ');
  await expect(udon(kk)).toContainText(udonInKazakhstan);

  await addFirecrackerUdon(page, kk);
  await page.getByRole('link', { name: kk.cartLink }).click();
  await expect(page.getByRole('heading', { name: kk.cartTitle, exact: true })).toBeVisible();
  await expect(page.getByText(kk.vat(kaz.expected.vatRate), { exact: true })).toBeVisible();
  await expect(page.getByRole('region', { name: kk.priceSummary })).toContainText(cartTotalInKazakhstan);
});

test('the United Kingdom gets British English and pounds; the brand words of the title stay', async ({ page }) => {
  const title = page.getByRole('heading', { level: 1 });
  await page.goto('/');
  // textContent: the <br> between the two brand words gives no text
  await expect(title).toHaveText(/^Wok\.\s*Roll\. Yana bir bor\.$/);

  await page.getByRole('combobox', { name: countrySelectName }).selectOption('GBR');
  await expect(page.locator('html')).toHaveAttribute('lang', 'en-GB');
  await expect(title).toHaveText(/^Wok\.\s*Roll\. Repeat\.$/);

  await page.goto('/menu');
  const udon = page.getByRole('article').filter({
    has: page.getByRole('heading', { name: 'Firecracker udon', exact: true }),
  });
  await expect(udon).toContainText('£3.45');
  await expect(udon).toContainText('350 g');
  await expect(page.getByRole('link', { name: /^Basket, dishes:/ })).toBeVisible();
});
