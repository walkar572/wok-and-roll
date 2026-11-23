const { expect } = require('@playwright/test');

async function addFirecrackerUdon(page) {
  const productCard = page.getByRole('article').filter({
    has: page.getByRole('heading', { name: 'Firecracker udon', exact: true }),
  });
  await productCard.getByRole('button', { name: /^(Details|Customize), Firecracker udon$/ }).click();

  const details = page.getByRole('dialog');
  await expect(details.getByRole('heading', { name: 'Firecracker udon', exact: true })).toBeVisible();
  await details.getByRole('button', { name: /^Add ·/ }).click();
}

module.exports = { addFirecrackerUdon };
