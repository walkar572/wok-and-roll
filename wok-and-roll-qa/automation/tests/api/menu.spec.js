const { test, expect } = require('@playwright/test');

const apiBaseUrl = process.env.API_BASE_URL || 'http://127.0.0.1:8080';
const countries = [
  { code: 'UZB', currency: 'UZS', vatRate: 15, cities: ['Tashkent'] },
  { code: 'KAZ', currency: 'KZT', vatRate: 20, cities: ['Almaty'] },
  { code: 'GEO', currency: 'GEL', vatRate: 12, cities: ['Tbilisi'] },
  { code: 'UKR', currency: 'UAH', vatRate: 8, cities: ['Kyiv'] },
  { code: 'CHN', currency: 'CNY', vatRate: 18, cities: ['Shanghai'] },
  { code: 'GBR', currency: 'GBP', vatRate: 20, cities: ['London'] },
  { code: 'USA', currency: 'USD', vatRate: 7, cities: ['Boston'] },
];

// Real price levels (migration V2026_11_22_0900): Tashkent level converted to every currency.
const seededUdonPrices = {
  UZB: { currency: 'UZS', price: 55000 },
  KAZ: { currency: 'KZT', price: 2190 },
  GEO: { currency: 'GEL', price: 11.9 },
  UKR: { currency: 'UAH', price: 179 },
  CHN: { currency: 'CNY', price: 31 },
  GBR: { currency: 'GBP', price: 3.45 },
  USA: { currency: 'USD', price: 4.35 },
};

const sets = [
  'Wok & Roll set for two',
  'Philadelphia set',
  'Salmon set',
  'Dragon set',
  'Classic set',
  'Party set',
  'Lunch combo',
];

function hasAtMostTwoDecimalPlaces(value) {
  return Number.isFinite(value) && Math.abs(value * 100 - Math.round(value * 100)) < 1e-8;
}

test.describe('menu API', () => {
  test('returns the seven countries with their currency, VAT and café cities from the seed', async ({ request }) => {
    const response = await request.get(apiBaseUrl + '/api/v1/countries');
    expect(response.status()).toBe(200);
    const actual = await response.json();
    expect(actual).toHaveLength(countries.length);
    expect(actual.map(({ code }) => code)).toEqual(countries.map(({ code }) => code));
    for (const [index, expected] of countries.entries()) {
      expect(actual[index]).toMatchObject(expected);
      expect(actual[index].name).toEqual(expect.any(String));
    }
  });

  test('returns the seeded menu and local-currency prices for every country', async ({ request }) => {
    for (const country of countries) {
      const [categoriesResponse, productsResponse] = await Promise.all([
        request.get(apiBaseUrl + '/api/v1/categories', { params: { country: country.code } }),
        request.get(apiBaseUrl + '/api/v1/products', { params: { country: country.code } }),
      ]);
      expect(categoriesResponse.status(), country.code + ' categories').toBe(200);
      expect(productsResponse.status(), country.code + ' products').toBe(200);
      const categories = await categoriesResponse.json();
      const products = await productsResponse.json();
      expect(categories.map(({ name }) => name)).toEqual([
        'Sushi & rolls',
        'Sets',
        'From the wok',
        'Sides',
        'Desserts',
        'Drinks',
      ]);
      // v1.8: 24 dishes, edamame left the menu for the peanuts; v1.10: 6 new sets.
      expect(products).toHaveLength(30);
      expect(products.map(({ name }) => name)).not.toContain('Edamame');
      expect(products.find(({ name }) => name === 'Chili garlic peanuts')).toMatchObject({
        weightGrams: 120,
        inStock: true,
      });
      for (const product of products) {
        expect(product.id).toEqual(expect.any(Number));
        expect(product.categoryId).toEqual(expect.any(Number));
        expect(product.name).toEqual(expect.any(String));
        expect(product.description === null || typeof product.description === 'string').toBe(true);
        expect(product.imageUrl === null || typeof product.imageUrl === 'string').toBe(true);
        for (const field of ['pieces', 'weightGrams', 'volumeMl']) {
          const value = product[field];
          expect(value === null || (Number.isInteger(value) && value > 0), product.name + ' ' + field).toBe(true);
        }
        expect(product.currency).toBe(country.currency);
        expect(hasAtMostTwoDecimalPlaces(product.price), country.code + ' ' + product.name + ' price').toBe(true);
        expect(product.inStock).toEqual(expect.any(Boolean));
        expect(product.hasModifiers).toEqual(expect.any(Boolean));
      }
      const udon = products.find(({ name }) => name === 'Firecracker udon');
      expect(udon).toBeDefined();
      expect(udon).toMatchObject(seededUdonPrices[country.code]);
      expect(udon).toMatchObject({ pieces: null, weightGrams: 350, volumeMl: null });
      const salmon = products.find(({ name }) => name === 'Salmon avocado roll');
      expect(salmon).toMatchObject({ description: 'Salmon, avocado, cucumber.', pieces: 8, weightGrams: 250 });
      const lemonade = products.find(({ name }) => name === 'Yuzu lemonade');
      expect(lemonade).toMatchObject({ weightGrams: null, volumeMl: 400 });
      for (const product of products) {
        expect(product.imageUrl, product.name + ' has a photo').toMatch(/^\/images\/[a-z-]+\.png$/);
      }
      const outOfStock = products.find(({ name }) => name === 'Tuna nigiri');
      expect(outOfStock).toBeDefined();
      expect(outOfStock.inStock).toBe(false);
    }
  });

  // v1.10: a set shows its dishes and their sum; it is cheaper than they are, in every country.
  test('every set is cheaper than its dishes one by one, in every currency', async ({ request }) => {
    for (const country of countries) {
      const response = await request.get(apiBaseUrl + '/api/v1/products', { params: { country: country.code } });
      expect(response.status(), country.code).toBe(200);
      const products = await response.json();
      const byId = new Map(products.map((product) => [product.id, product]));
      const found = products.filter(({ setItems }) => setItems.length > 0);
      expect(found.map(({ name }) => name), country.code).toEqual(sets);
      for (const set of found) {
        const label = country.code + ' ' + set.name;
        expect(set.price, label).toBeLessThan(set.regularPrice);
        // The regular price is the sum of the dishes of the set in this country.
        const sum = set.setItems.reduce(
          (total, { productId, quantity }) => total + Math.round(byId.get(productId).price * 100) * quantity,
          0,
        );
        expect(Math.round(set.regularPrice * 100), label).toBe(sum);
      }
      for (const dish of products.filter(({ setItems }) => setItems.length === 0)) {
        expect(dish.regularPrice, country.code + ' ' + dish.name).toBeNull();
      }
    }
  });

  test('filters by category and returns an empty list for an unknown category', async ({ request }) => {
    const categoriesResponse = await request.get(apiBaseUrl + '/api/v1/categories', { params: { country: 'UZB' } });
    expect(categoriesResponse.status()).toBe(200);
    const categories = await categoriesResponse.json();
    const wokCategory = categories.find(({ name }) => name === 'From the wok');
    expect(wokCategory).toBeDefined();
    const filteredResponse = await request.get(apiBaseUrl + '/api/v1/products', {
      params: { country: 'UZB', categoryId: String(wokCategory.id) },
    });
    expect(filteredResponse.status()).toBe(200);
    const filtered = await filteredResponse.json();
    expect(filtered.length).toBeGreaterThan(0);
    expect(filtered.every(({ categoryId }) => categoryId === wokCategory.id)).toBe(true);
    const unknownCategoryResponse = await request.get(apiBaseUrl + '/api/v1/products', {
      params: { country: 'UZB', categoryId: '999999' },
    });
    expect(unknownCategoryResponse.status()).toBe(200);
    await expect(unknownCategoryResponse.json()).resolves.toEqual([]);
  });

  test('returns the standard validation error for an unknown country', async ({ request }) => {
    for (const path of ['/api/v1/categories', '/api/v1/products']) {
      const response = await request.get(apiBaseUrl + path, { params: { country: 'FRA' } });
      expect(response.status(), path).toBe(400);
      await expect(response.json()).resolves.toMatchObject({
        code: 'VALIDATION_ERROR',
        message: expect.any(String),
        fields: { country: expect.any(String) },
      });
    }
  });

  test('seeded products have numeric prices rounded to two decimal places', async ({ request }) => {
    const response = await request.get(apiBaseUrl + '/api/v1/products', { params: { country: 'UZB' } });
    expect(response.status()).toBe(200);
    const products = await response.json();
    expect(products.every(({ price }) => typeof price === 'number' && hasAtMostTwoDecimalPlaces(price))).toBe(true);
  });

  test('omits a modifier without a price in the selected country', async ({ request }) => {
    const productsResponse = await request.get(apiBaseUrl + '/api/v1/products', { params: { country: 'CHN' } });
    expect(productsResponse.status()).toBe(200);
    const products = await productsResponse.json();
    const miso = products.find(({ name }) => name === 'Miso mushroom rice');
    expect(miso).toBeDefined();
  
    const detailResponse = await request.get(apiBaseUrl + '/api/v1/products/' + miso.id, {
      params: { country: 'CHN' },
    });
    expect(detailResponse.status()).toBe(200);
    const detail = await detailResponse.json();
    expect(detail.name).toBe('Miso mushroom rice');
    expect(detail.hasModifiers).toBe(true);
    expect(detail.modifiers.map(({ name }) => name)).toEqual(['Regular', 'Large']);
    expect(detail.modifiers.map(({ name }) => name)).not.toContain('Extra tofu');
  });
});
