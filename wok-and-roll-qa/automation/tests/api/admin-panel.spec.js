const { test, expect } = require('@playwright/test');

// The admin panel API (api-contract.md §3.20, v1.11): ADMIN only, read only. Runs on the seed data:
// the sales history Jun - Nov 2026 and the booking history 26 Oct - 23 Nov 2026.

const apiBaseUrl = process.env.API_BASE_URL || 'http://127.0.0.1:8080';

const DEMO = {
  ADMIN: ['admin@wokandroll.test', 'AdminDemo2026'],
  STAFF: ['staff@wokandroll.test', 'StaffDemo2026'],
  VIEWER: ['viewer@wokandroll.test', 'ViewerDemo2026'],
  CUSTOMER: ['customer@wokandroll.test', 'CustomerDemo2026'],
};

const ENDPOINTS = [
  '/api/v1/admin/overview?from=2026-11-01&to=2026-11-22',
  '/api/v1/admin/orders',
  '/api/v1/admin/bookings',
  '/api/v1/admin/users',
  '/api/v1/admin/cafes',
];

async function tokenOf(request, role) {
  const [email, password] = DEMO[role];
  const response = await request.post(apiBaseUrl + '/api/v1/auth/login', { data: { email, password } });
  expect(response.status(), role + ' login').toBe(200);
  return (await response.json()).token;
}

async function asAdmin(request, path) {
  const token = await tokenOf(request, 'ADMIN');
  return request.get(apiBaseUrl + path, { headers: { Authorization: 'Bearer ' + token } });
}

test('only an admin opens the panel API: 401 without a token, 403 for the other roles', async ({ request }) => {
  for (const path of ENDPOINTS) {
    expect((await request.get(apiBaseUrl + path)).status(), path).toBe(401);
  }
  for (const role of ['CUSTOMER', 'STAFF', 'VIEWER']) {
    const token = await tokenOf(request, role);
    for (const path of ENDPOINTS) {
      const response = await request.get(apiBaseUrl + path, { headers: { Authorization: 'Bearer ' + token } });
      expect(response.status(), role + ' ' + path).toBe(403);
    }
  }
  const token = await tokenOf(request, 'ADMIN');
  for (const path of ENDPOINTS) {
    const response = await request.get(apiBaseUrl + path, { headers: { Authorization: 'Bearer ' + token } });
    expect(response.status(), 'ADMIN ' + path).toBe(200);
  }
});

test('the overview has all four statuses, every country, every day and the top 5 dishes', async ({ request }) => {
  const response = await asAdmin(request, '/api/v1/admin/overview?from=2026-11-01&to=2026-11-22');
  const body = await response.json();
  expect(Object.keys(body.orders.byStatus)).toEqual(['NEW', 'CONFIRMED', 'DONE', 'CANCELLED']);
  const byStatus = Object.values(body.orders.byStatus).reduce((sum, count) => sum + count, 0);
  expect(body.orders.total).toBe(byStatus);
  expect(body.orders.pickup + body.orders.delivery).toBe(body.orders.total);
  expect(body.regions.map(({ country }) => country)).toEqual(['UZB', 'KAZ', 'GEO', 'UKR', 'CHN', 'GBR', 'USA']);
  expect(body.days).toHaveLength(22);
  expect(body.topDishes).toHaveLength(5);
  expect(body.bookings.total).toBeGreaterThan(0);
  expect(Object.keys(body.accounts.byRole)).toEqual(['ADMIN', 'STAFF', 'VIEWER', 'CUSTOMER']);
});

test('the overview revenue is the sales-by-region report', async ({ request }) => {
  const token = await tokenOf(request, 'ADMIN');
  const headers = { Authorization: 'Bearer ' + token };
  const overview = await (
    await request.get(apiBaseUrl + '/api/v1/admin/overview?from=2026-11-01&to=2026-11-22', { headers })
  ).json();
  const report = await (
    await request.get(apiBaseUrl + '/api/v1/reports/sales-by-region?from=2026-11-01&to=2026-11-22', { headers })
  ).json();
  for (const region of report.regions) {
    const match = overview.regions.find(({ country }) => country === region.country);
    expect(match.revenue, region.country).toBe(region.total);
    expect(match.orders, region.country).toBe(region.orders);
  }
});

test('the orders filter by country and status, and the search finds one number', async ({ request }) => {
  const filtered = await (
    await asAdmin(request, '/api/v1/admin/orders?country=GEO&status=CANCELLED&pageSize=100')
  ).json();
  expect(filtered.items.length).toBeGreaterThan(0);
  for (const order of filtered.items) {
    expect(order.country).toBe('GEO');
    expect(order.currency).toBe('GEL');
    expect(order.status).toBe('CANCELLED');
  }
  const number = filtered.items[0].orderNumber;
  const found = await (await asAdmin(request, '/api/v1/admin/orders?q=' + number.toLowerCase())).json();
  expect(found.items.map(({ orderNumber }) => orderNumber)).toEqual([number]);
});

test('one order has its lines, totals and what only the admin sees; an unknown one is 404', async ({ request }) => {
  const list = await (await asAdmin(request, '/api/v1/admin/orders?q=customer@wokandroll.test')).json();
  const number = list.items[0].orderNumber;
  const response = await asAdmin(request, '/api/v1/admin/orders/' + number);
  expect(response.status()).toBe(200);
  const body = await response.json();
  expect(body.order.orderNumber).toBe(number);
  expect(body.order.lines.length).toBeGreaterThan(0);
  expect(body.account.email).toBe('customer@wokandroll.test');
  expect(body.updatedAt).toBeTruthy();

  const unknown = await asAdmin(request, '/api/v1/admin/orders/WR-00000000');
  expect(unknown.status()).toBe(404);
  expect((await unknown.json()).code).toBe('NOT_FOUND');
});

test('every café has a booking history and the bookings filter by café and date', async ({ request }) => {
  const cafes = (await (await asAdmin(request, '/api/v1/admin/cafes')).json()).items;
  expect(cafes).toHaveLength(14);
  for (const cafe of cafes) {
    expect(cafe.tables, cafe.name).toBe(6);
    expect(cafe.timeZone, cafe.name).toBeTruthy();
  }
  const boston = cafes.find(({ city }) => city === 'Boston');
  const bookings = await (
    await asAdmin(
      request,
      `/api/v1/admin/bookings?cafeId=${boston.id}&from=2026-11-23&to=2026-11-23`,
    )
  ).json();
  expect(bookings.items.length).toBeGreaterThan(0);
  for (const booking of bookings.items) {
    expect(booking.cafe.id).toBe(boston.id);
    expect(booking.date).toBe('2026-11-23');
    expect(booking.guests).toBeLessThanOrEqual(booking.tableSeats);
  }
});

test('the accounts have their role and orders, never a password hash', async ({ request }) => {
  const users = (await (await asAdmin(request, '/api/v1/admin/users?pageSize=100')).json()).items;
  const emails = users.map(({ email }) => email);
  for (const [email] of Object.values(DEMO)) expect(emails).toContain(email);
  for (const user of users) {
    expect(user).not.toHaveProperty('passwordHash');
    expect(user).not.toHaveProperty('password_hash');
  }
  const customer = users.find(({ email }) => email === 'customer@wokandroll.test');
  expect(customer.role).toBe('CUSTOMER');
  expect(customer.orders).toBeGreaterThan(0);
});

test('a wrong filter is a 400 VALIDATION_ERROR', async ({ request }) => {
  for (const path of [
    '/api/v1/admin/orders?status=LOST',
    '/api/v1/admin/orders?country=FRA',
    '/api/v1/admin/orders?from=2026-11-22&to=2026-11-01',
    '/api/v1/admin/bookings?status=NEW',
    '/api/v1/admin/users?role=OWNER',
    '/api/v1/admin/overview?from=2025-01-01&to=2026-11-22',
  ]) {
    const response = await asAdmin(request, path);
    expect(response.status(), path).toBe(400);
    expect((await response.json()).code, path).toBe('VALIDATION_ERROR');
  }
});
