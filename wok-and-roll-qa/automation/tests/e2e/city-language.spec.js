const { test, expect } = require('@playwright/test');
const pricingExamples = require('../../../test-data/pricing-examples.json');
const { addFirecrackerUdon } = require('./helpers/cart');
const { kk, uz } = require('./helpers/text');
const { chooseCity, chooseLanguage, citySwitcher, languageSwitcher } = require('./helpers/region');

const kaz = pricingExamples.find((example) => example.country === 'KAZ');
if (!kaz) throw new Error('No pricing example found for KAZ.');

// Intl puts a no-break space between thousands, so the patterns use \s.
const udonInUzbekistan = /55\s000,00\s*soʻm/;
const udonInKazakhstan = /2\s190,00\s*₸/;
// One udon in KAZ: 2,190.00 + VAT 20 % 438.00 = 2,628.00, below the 4,000 threshold: no discount.
const cartTotalInKazakhstan = /2\s628,00\s*₸/;

const udonCard = (page, name) =>
  page.getByRole('article').filter({ has: page.getByRole('heading', { name, exact: true }) });

test('the city changes the prices and the currency, the language stays; both survive a reload', async ({ page }) => {
  await page.goto('/menu');
  await expect(citySwitcher(page)).toHaveText(uz.tashkent);
  await expect(languageSwitcher(page)).toHaveText('UZ');
  await expect(page.locator('html')).toHaveAttribute('lang', /^uz/);
  await expect(udonCard(page, uz.udon)).toContainText(udonInUzbekistan);

  // Almaty: tenge, and still Uzbek (contract v1.9: the city does not depend on the language).
  await chooseCity(page, uz.almaty);
  await expect(page.locator('html')).toHaveAttribute('lang', /^uz/);
  await expect(udonCard(page, uz.udon)).toContainText(udonInKazakhstan);

  // Kazakh: the texts change, the prices stay.
  await chooseLanguage(page, 'KK');
  await expect(page.locator('html')).toHaveAttribute('lang', /^kk/);
  await expect(udonCard(page, kk.udon)).toContainText(udonInKazakhstan);

  await page.reload();
  await expect(citySwitcher(page)).toHaveText(kk.almaty);
  await expect(languageSwitcher(page)).toHaveText('KK');
  await expect(udonCard(page, kk.udon)).toContainText(udonInKazakhstan);

  await addFirecrackerUdon(page, kk);
  await page.getByRole('link', { name: kk.cartLink }).click();
  await expect(page.getByRole('heading', { name: kk.cartTitle, exact: true })).toBeVisible();
  await expect(page.getByText(kk.vat(kaz.expected.vatRate), { exact: true })).toBeVisible();
  await expect(page.getByRole('region', { name: kk.priceSummary })).toContainText(cartTotalInKazakhstan);
});

test('British English and pounds in London; the brand words of the title stay', async ({ page }) => {
  const title = page.getByRole('heading', { level: 1 });
  await page.goto('/');
  // textContent: the <br> between the two brand words gives no text
  await expect(title).toHaveText(/^Wok\.\s*Roll\. Yana bir bor\.$/);

  await chooseCity(page, uz.london);
  await expect(title).toHaveText(/^Wok\.\s*Roll\. Yana bir bor\.$/);
  await chooseLanguage(page, 'EN-GB');
  await expect(page.locator('html')).toHaveAttribute('lang', 'en-GB');
  await expect(title).toHaveText(/^Wok\.\s*Roll\. Repeat\.$/);

  await page.goto('/menu');
  const udon = udonCard(page, 'Firecracker udon');
  await expect(udon).toContainText('£3.45');
  await expect(udon).toContainText('350 g');
  await expect(page.getByRole('link', { name: /^Basket, dishes:/ })).toBeVisible();
});

test('American English and dollars in Boston, with roll sets cheaper than their rolls', async ({ page }) => {
  await page.goto('/menu');
  await chooseLanguage(page, 'EN-US');
  await chooseCity(page, uz.boston);
  await expect(page.locator('html')).toHaveAttribute('lang', 'en-US');
  await expect(page.getByText('Prices for Boston, United States')).toBeVisible();
  await expect(udonCard(page, 'Firecracker udon')).toContainText('$4.35');
  await expect(page.getByRole('link', { name: /^Cart, dishes:/ })).toBeVisible();

  // Philadelphia set: $15.75 instead of 2 Philadelphia rolls and a California roll ($18.85).
  const set = udonCard(page, 'Philadelphia set');
  await expect(set).toContainText(/2\s×\sPhiladelphia roll, California roll/);
  await expect(set).toContainText('$15.75');
  await expect(set).toContainText('Separately: $18.85');
  await expect(set).toContainText('Save 16%');

  // The modal lists the dishes of the set too.
  await set.getByRole('button', { name: /Philadelphia set/ }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByRole('listitem')).toHaveText([/2\s×\sPhiladelphia roll/, 'California roll']);
  await expect(dialog).toContainText('Separately: $18.85');
});
