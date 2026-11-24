const { expect } = require('@playwright/test');
const { countrySelect } = require('./text');

// The country switcher is a listbox (Radix Select), not a native <select>: open it and click the
// option. The option name starts with the code: "KAZ Қазақстан Қазақша · KZT".
function countrySwitcher(page) {
  return page.getByRole('combobox', { name: countrySelect });
}

async function chooseCountry(page, code) {
  await countrySwitcher(page).click();
  await page.getByRole('option', { name: new RegExp('^' + code + '\\b') }).click();
  await expect(countrySwitcher(page)).toHaveText(code);
}

module.exports = { countrySwitcher, chooseCountry };
