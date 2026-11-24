const { expect } = require('@playwright/test');
const { uz } = require('./text');

/** Adds one Firecracker udon (the first size is chosen already). Texts of the given language. */
async function addFirecrackerUdon(page, text = uz) {
  const productCard = page.getByRole('article').filter({
    has: page.getByRole('heading', { name: text.udon, exact: true }),
  });
  await productCard.getByRole('button', { name: text.udonAction }).click();

  const details = page.getByRole('dialog');
  await expect(details.getByRole('heading', { name: text.udon, exact: true })).toBeVisible();
  await details.getByRole('button', { name: text.add }).click();
}

module.exports = { addFirecrackerUdon };
