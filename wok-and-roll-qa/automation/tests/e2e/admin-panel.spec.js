const { test, expect } = require('@playwright/test');

// The admin panel (api-contract.md v1.11): its own login at /admin/login and sections by role.
// The texts are British English: the language is chosen before the page loads.

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    if (!sessionStorage.getItem('qa-language-set')) {
      localStorage.setItem('selectedLanguage', 'en-GB');
      sessionStorage.setItem('qa-language-set', '1');
    }
  });
});

async function logIn(page, email, password) {
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Log in', exact: true }).click();
}

const panelNav = (page) => page.getByRole('navigation', { name: 'Panel sections' });

test('an admin logs in to the panel and sees the overview, every order and one order with its receipt', async ({
  page,
}) => {
  await page.goto('/admin/orders');
  // not logged in: the panel login, and back to the orders after it
  await expect(page).toHaveURL(/\/admin\/login\?next=%2Fadmin%2Forders/);
  await expect(page.getByRole('heading', { level: 1, name: 'Admin panel' })).toBeVisible();
  await logIn(page, 'admin@wokandroll.test', 'AdminDemo2026');
  await expect(page).toHaveURL(/\/admin\/orders$/);

  await expect(panelNav(page).getByRole('link')).toHaveText([
    'Overview',
    'Orders',
    'Bookings',
    'Users',
    'Cafés',
    'Menu',
    'Café orders',
    'Reports',
  ]);
  await expect(panelNav(page).getByRole('link', { name: 'Orders', exact: true })).toHaveAttribute('aria-current', 'page');

  // every row of the filtered list is in Georgia
  await page.getByLabel('Country').selectOption('GEO');
  const table = page.getByRole('table', { name: 'Orders, newest first' });
  await expect(table.getByRole('row').nth(1)).toContainText('Georgia');
  // the total in lari, with the sign of the currency, never its code
  await expect(table.getByRole('row').nth(1)).toContainText(/₾\d+\.\d{2}/);
  await expect(table.getByRole('row').nth(1)).not.toContainText('GEL');

  // one order: its details and its receipt
  const number = (await table.getByRole('row').nth(1).getByRole('link').textContent()).trim();
  await table.getByRole('link', { name: number }).click();
  await expect(page.getByRole('heading', { level: 1, name: 'Order ' + number })).toBeVisible();
  await expect(page.getByRole('region', { name: 'Price summary' })).toContainText(number);

  await panelNav(page).getByRole('link', { name: 'Overview', exact: true }).click();
  await expect(page.getByRole('heading', { level: 1, name: 'Overview' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Orders per day' })).toBeVisible();
  await expect(page.getByRole('table').first()).toContainText('United States');
});

test('staff see only the café orders, a viewer only the reports', async ({ page }) => {
  await page.goto('/admin/login');
  await logIn(page, 'staff@wokandroll.test', 'StaffDemo2026');
  await expect(page).toHaveURL(/\/admin\/cafe-orders$/);
  await expect(panelNav(page).getByRole('link')).toHaveText(['Café orders']);
  // a section of another role leads back to the own one
  await page.goto('/admin/users');
  await expect(page).toHaveURL(/\/admin\/cafe-orders$/);

  await page.getByRole('button', { name: 'Log out' }).click();
  await expect(page).toHaveURL(/\/admin\/login$/);
  await logIn(page, 'viewer@wokandroll.test', 'ViewerDemo2026');
  await expect(page).toHaveURL(/\/admin\/reports$/);
  await expect(panelNav(page).getByRole('link')).toHaveText(['Reports']);
});

test('a customer account has no access to the panel', async ({ page }) => {
  await page.goto('/admin/login');
  await logIn(page, 'customer@wokandroll.test', 'CustomerDemo2026');
  await expect(page.getByText(/This account has no access to the admin panel/)).toBeVisible();
  await expect(page).toHaveURL(/\/admin\/login$/);
  await page.goto('/admin');
  await expect(page).toHaveURL(/\/admin\/login$/);
});
