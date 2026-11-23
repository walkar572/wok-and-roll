const { test, expect } = require('@playwright/test');
const { randomUUID } = require('crypto');

// Slot-capacity tests require a fresh seeded database with no pre-existing QA orders.

const apiBaseUrl = process.env.API_BASE_URL || 'http://127.0.0.1:8080';
const countryCode = 'UZB';

function localDateOffset(days, timeZone = 'Asia/Tashkent') {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(new Date());
  const values = Object.fromEntries(parts.map(({ type, value }) => [type, value]));
  const date = new Date(Date.UTC(Number(values.year), Number(values.month) - 1, Number(values.day) + days));
  return date.toISOString().slice(0, 10);
}

async function countryTimeZone(request, country) {
  const response = await request.get(apiBaseUrl + '/api/v1/countries');
  expect(response.status()).toBe(200);
  const countries = await response.json();
  const match = countries.find(({ code }) => code === country);
  expect(match).toBeDefined();
  return match.timeZone;
}

// The seed migration assigns the first UZB café ID 1 (Wok & Roll Chilanzar).
// TODO(#146): use GET /api/v1/cafes?country=UZB and remove the hard-coded café ID 1.
function seededPickupCafe(country = countryCode) {
  expect(country).toBe('UZB');
  return { id: 1 };
}

async function zonesFor(request, country = countryCode) {
  const response = await request.get(apiBaseUrl + '/api/v1/delivery-zones', { params: { country } });
  expect(response.status(), country + ' delivery zones').toBe(200);
  return (await response.json()).items;
}

async function itemsFor(request, country = countryCode) {
  const response = await request.get(apiBaseUrl + '/api/v1/products', { params: { country } });
  expect(response.status(), country + ' products').toBe(200);
  const products = await response.json();
  const udon = products.find(({ name, inStock }) => name === 'Firecracker udon' && inStock);
  expect(udon, country + ' in-stock Firecracker udon').toBeDefined();
  const detailResponse = await request.get(apiBaseUrl + '/api/v1/products/' + udon.id, { params: { country } });
  expect(detailResponse.status()).toBe(200);
  const detail = await detailResponse.json();
  const regular = detail.modifiers.find(({ type, name, priceDelta }) => type === 'SIZE' && name === 'Regular' && priceDelta === 0);
  expect(regular, country + ' free Regular size').toBeDefined();
  return [{ productId: udon.id, quantity: 2, modifierIds: [regular.id] }];
}

async function orderContext(request, { scenario = 'PICKUP', dayOffset = 1, slotStart = '10:00' } = {}) {
  const [zones, items] = await Promise.all([zonesFor(request), itemsFor(request)]);
  const timeZone = await countryTimeZone(request, countryCode);
  const orderDate = localDateOffset(dayOffset, timeZone);
  const zone = scenario === 'DELIVERY' ? zones.find(({ fee }) => fee === 20) : undefined;
  if (scenario === 'DELIVERY') expect(zone, 'seed zone with 20.00 fee').toBeDefined();
  const cafe = scenario === 'DELIVERY' ? { id: zone.cafeId } : seededPickupCafe();
  expect(cafe).toBeDefined();
  // Seeded cafés open at 10:00; the order API validates this slot directly.
  const slot = { start: slotStart };
  const quoteRequest = { country: countryCode, scenario, items };
  if (scenario === 'DELIVERY') quoteRequest.deliveryZoneId = zone.id;
  const quoteResponse = await request.post(apiBaseUrl + '/api/v1/cart/quote', { data: quoteRequest });
  const quoteText = await quoteResponse.text();
  expect(quoteResponse.status(), 'quote: ' + quoteText).toBe(200);
  const quote = JSON.parse(quoteText);
  const order = {
    country: countryCode,
    orderDate,
    scenario,
    slotStart: slot.start,
    contactName: 'QA API Guest',
    contactPhone: '+998901234567',
    contactEmail: 'qa@example.test',
    comment: 'Automated API test',
    expectedTotal: quote.total,
    items,
  };
  if (scenario === 'PICKUP') order.cafeId = cafe.id;
  else {
    order.deliveryZoneId = zone.id;
    order.deliveryAddress = 'Amir Temur 15, apt 4';
  }
  return { order, quote, cafe, zone, slot, orderDate };
}

async function expectApiError(response, status, code, field) {
  expect(response.status()).toBe(status);
  const error = await response.json();
  expect(error.code).toBe(code);
  if (field) expect(Object.keys(error.fields)).toContain(field);
}

async function postOrder(request, order, headers = {}) {
  return request.post(apiBaseUrl + '/api/v1/orders', { data: order, headers });
}

function expectTotalsToMatchOrder(order, quote) {
  for (const field of ['subtotal', 'vatRate', 'vat', 'amountInclVat', 'discountRate', 'discount', 'deliveryFee', 'total']) {
    expect(order[field], field).toBe(quote[field]);
  }
}

test.describe('orders API', () => {
  test('creates pickup and delivery orders with totals equal to the latest quote', async ({ request }) => {
    for (const scenario of ['PICKUP', 'DELIVERY']) {
      const context = await orderContext(request, { scenario });
      const response = await postOrder(request, context.order);
      const responseText = await response.text();
      expect(response.status(), scenario + ': ' + responseText).toBe(201);
      const created = JSON.parse(responseText);
      expect(created.orderNumber).toMatch(/^WR-[0-9A-HJKMNP-TV-Z]{8}$/);
      expect(created.status).toBe('NEW');
      expect(created.scenario).toBe(scenario);
      expect(created.country).toBe(countryCode);
      expect(created.orderDate).toBe(context.orderDate);
      expect(created.slotStart).toBe(context.slot.start);
      expect(created.cafe).toMatchObject({ id: context.cafe.id });
      expectTotalsToMatchOrder(created, context.quote);
      expect(created.lines).toHaveLength(context.quote.lines.length);
      if (scenario === 'PICKUP') {
        expect(created.deliveryZone).toBeNull();
        expect(created.deliveryAddress).toBeNull();
        expect(created.deliveryFee).toBe(0);
      } else {
        expect(created.deliveryZone).toMatchObject({ id: context.zone.id, name: context.zone.name });
        expect(created.deliveryAddress).toBe(context.order.deliveryAddress);
        expect(created.deliveryFee).toBe(20);
      }
    }
  });

  test('returns the expected status, code and field key for order validation failures', async ({ request }) => {
    const context = await orderContext(request);
    await expectApiError(await postOrder(request, { ...context.order, country: 'USA' }), 400, 'UNKNOWN_COUNTRY', 'country');

    const tooFarAhead = { ...context.order, orderDate: localDateOffset(8, await countryTimeZone(request, countryCode)) };
    await expectApiError(await postOrder(request, tooFarAhead), 400, 'VALIDATION_ERROR', 'orderDate');

    const unknownCafe = { ...context.order, cafeId: 999999999 };
    await expectApiError(await postOrder(request, unknownCafe), 400, 'UNKNOWN_CAFE', 'cafeId');

    const offGridSlot = { ...context.order, slotStart: '10:15' };
    await expectApiError(await postOrder(request, offGridSlot), 400, 'INVALID_SLOT', 'slotStart');

    const afterLastSlot = { ...context.order, slotStart: '22:00' };
    await expectApiError(await postOrder(request, afterLastSlot), 400, 'INVALID_SLOT', 'slotStart');

    const badQuantity = { ...context.order, items: [{ ...context.order.items[0], quantity: 0 }] };
    await expectApiError(await postOrder(request, badQuantity), 400, 'VALIDATION_ERROR', 'items[0].quantity');

    const missingSizeOrder = {
      ...context.order,
      items: [{ ...context.order.items[0], modifierIds: [] }],
    };
    await expectApiError(await postOrder(request, missingSizeOrder), 400, 'INVALID_MODIFIER', 'items[0].modifierIds');

    const unknownProductOrder = {
      ...context.order,
      items: [{ productId: 999999999, quantity: 1, modifierIds: [] }],
    };
    await expectApiError(await postOrder(request, unknownProductOrder), 400, 'UNKNOWN_PRODUCT', 'items[0].productId');

    const outOfStockResponse = await request.get(apiBaseUrl + '/api/v1/products', { params: { country: countryCode } });
    expect(outOfStockResponse.status()).toBe(200);
    const outOfStockProducts = await outOfStockResponse.json();
    const tuna = outOfStockProducts.find(({ name, inStock }) => name === 'Tuna nigiri' && !inStock);
    expect(tuna).toBeDefined();
    const outOfStockOrder = { ...context.order, items: [{ productId: tuna.id, quantity: 1, modifierIds: [] }] };
    await expectApiError(await postOrder(request, outOfStockOrder), 400, 'OUT_OF_STOCK', 'items[0].productId');

    const deliveryBase = await orderContext(request, { scenario: 'DELIVERY' });
    const unknownZone = { ...deliveryBase.order, deliveryZoneId: 999999999 };
    await expectApiError(await postOrder(request, unknownZone), 400, 'UNKNOWN_DELIVERY_ZONE', 'deliveryZoneId');

    const past = { ...context.order, orderDate: localDateOffset(-1, await countryTimeZone(request, countryCode)) };
    await expectApiError(await postOrder(request, past), 400, 'PAST_DATE', 'orderDate');

    const combinedInvalid = {
      ...deliveryBase.order,
      orderDate: localDateOffset(-1, await countryTimeZone(request, countryCode)),
      deliveryAddress: 'ab',
      slotStart: '12:15x',
      contactName: '',
      contactPhone: 'bad',
      contactEmail: 'not-an-email',
      comment: 'x'.repeat(301),
      items: [{ ...deliveryBase.order.items[0], quantity: 0 }],
    };
    const combinedResponse = await postOrder(request, combinedInvalid);
    expect(combinedResponse.status()).toBe(400);
    const combinedError = await combinedResponse.json();
    expect(combinedError.code).toBe('PAST_DATE');
    for (const field of ['orderDate', 'deliveryAddress', 'slotStart', 'contactName', 'contactPhone', 'contactEmail', 'comment', 'items[0].quantity']) {
      expect(Object.keys(combinedError.fields)).toContain(field);
    }
  });

  test('replays the same idempotency key once and rejects a different body', async ({ request }, testInfo) => {
    const slotStart = testInfo.retry === 0 ? '10:00' : '10:30';
    const context = await orderContext(request, { dayOffset: 2, slotStart });
    const key = randomUUID();
    const firstResponse = await postOrder(request, context.order, { 'Idempotency-Key': key });
    expect(firstResponse.status()).toBe(201);
    const first = await firstResponse.json();
    const replayResponse = await postOrder(request, context.order, { 'Idempotency-Key': key });
    expect(replayResponse.status()).toBe(201);
    expect(replayResponse.headers()['idempotent-replayed']).toBe('true');
    expect(await replayResponse.json()).toEqual(first);

    await expectApiError(await postOrder(request, { ...context.order, contactName: 'Different Guest' }, { 'Idempotency-Key': key }), 422, 'IDEMPOTENCY_KEY_REUSED');
    await expectApiError(await postOrder(request, context.order, { 'Idempotency-Key': 'not-a-uuid' }), 400, 'VALIDATION_ERROR', 'Idempotency-Key');

    // The initial order and its replay use one place total, so four more orders fill the five-place slot.
    for (let index = 0; index < 4; index += 1) {
      const response = await postOrder(request, { ...context.order, contactName: 'QA Replay Capacity ' + index });
      expect(response.status(), 'capacity after idempotent replay ' + (index + 1)).toBe(201);
    }
    await expectApiError(await postOrder(request, context.order), 409, 'SLOT_FULL');
  });

  test('fills a seeded slot to capacity and rejects the next order', async ({ request }, testInfo) => {
    const timeZone = await countryTimeZone(request, countryCode);
    const orderDate = localDateOffset(7, timeZone);
    const items = await itemsFor(request);
    const cafe = seededPickupCafe();
    const capacity = 5;
    const slotStart = testInfo.retry === 0 ? '10:30' : '11:00';
    const quoteResponse = await request.post(apiBaseUrl + '/api/v1/cart/quote', {
      data: { country: countryCode, scenario: 'PICKUP', items },
    });
    expect(quoteResponse.status()).toBe(200);
    const quote = await quoteResponse.json();
    const order = {
      country: countryCode, orderDate, scenario: 'PICKUP', cafeId: cafe.id, slotStart,
      contactName: 'QA Capacity Guest', contactPhone: '+998901234567', expectedTotal: quote.total, items,
    };
    for (let index = 0; index < capacity; index += 1) {
      const response = await postOrder(request, { ...order, contactName: 'QA Capacity Guest ' + index });
      expect(response.status(), 'capacity reservation ' + (index + 1)).toBe(201);
    }
    await expectApiError(await postOrder(request, order), 409, 'SLOT_FULL');
  });

  test('rejects a stale expectedTotal without reserving a slot', async ({ request }, testInfo) => {
    const slotStart = testInfo.retry === 0 ? '11:00' : '10:00';
    const context = await orderContext(request, { dayOffset: 6, slotStart });
    const wrongTotalOrder = { ...context.order, expectedTotal: Number((context.quote.total + 1).toFixed(2)) };
    await expectApiError(await postOrder(request, wrongTotalOrder), 409, 'PRICE_CHANGED');

    // Prove the rejected request did not consume capacity by filling the seeded slot afterward.
    for (let index = 0; index < 5; index += 1) {
      const response = await postOrder(request, { ...context.order, contactName: 'QA Price Guest ' + index });
      expect(response.status(), 'unreserved capacity after PRICE_CHANGED ' + (index + 1)).toBe(201);
    }
  });
});
