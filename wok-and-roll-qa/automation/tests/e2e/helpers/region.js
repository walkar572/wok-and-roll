const { expect } = require('@playwright/test');
const { citySelect, languageSelect } = require('./text');

// The city and the language are two switchers in the header (contract v1.9), both listboxes
// (Radix Select), not native <select>s: open one and click the option.
// The city gives the prices, the currency and the cafés; the language only the texts.

function citySwitcher(page) {
  return page.getByRole('banner').getByRole('combobox', { name: citySelect });
}

function languageSwitcher(page) {
  return page.getByRole('banner').getByRole('combobox', { name: languageSelect });
}

// The option name is the city and its country: "Olmaota Qozogʻiston · KZT". (\b knows only
// Latin letters, so the end of the city is a space.)
async function chooseCity(page, city) {
  await citySwitcher(page).click();
  await page.getByRole('option', { name: new RegExp('^' + city + '\\s') }).click();
  await expect(citySwitcher(page)).toHaveText(city);
}

// The option name starts with the code: "KK Қазақша", "EN-US English (US)".
async function chooseLanguage(page, code) {
  await languageSwitcher(page).click();
  await page.getByRole('option', { name: new RegExp('^' + code + '\\b') }).click();
  await expect(languageSwitcher(page)).toHaveText(code);
}

module.exports = { citySwitcher, languageSwitcher, chooseCity, chooseLanguage };
