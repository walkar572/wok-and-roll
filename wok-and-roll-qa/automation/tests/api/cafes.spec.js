const { test, expect } = require('@playwright/test');

const apiBaseUrl = process.env.API_BASE_URL || 'http://127.0.0.1:8080';

// The city of the two seeded cafés of every country: south, north, west, east (degrees).
const cities = {
  UZB: [41.2, 41.4, 69.1, 69.4], // Tashkent
  KAZ: [43.1, 43.4, 76.8, 77.1], // Almaty
  GEO: [41.6, 41.8, 44.7, 44.9], // Tbilisi
  UKR: [50.3, 50.6, 30.3, 30.7], // Kyiv
  CHN: [31.1, 31.4, 121.3, 121.7], // Shanghai
  GBR: [51.4, 51.6, -0.3, 0.1], // London
};

test.describe('cafés API', () => {
  for (const [country, [south, north, west, east]] of Object.entries(cities)) {
    test(`${country}: both cafés have a place on the map in their city (v1.7)`, async ({ request }) => {
      const response = await request.get(apiBaseUrl + '/api/v1/cafes', { params: { country } });
      expect(response.status()).toBe(200);
      const { items } = await response.json();

      expect(items).toHaveLength(2);
      for (const cafe of items) {
        expect(typeof cafe.latitude, cafe.name).toBe('number');
        expect(typeof cafe.longitude, cafe.name).toBe('number');
        expect(cafe.latitude, cafe.name).toBeGreaterThan(south);
        expect(cafe.latitude, cafe.name).toBeLessThan(north);
        expect(cafe.longitude, cafe.name).toBeGreaterThan(west);
        expect(cafe.longitude, cafe.name).toBeLessThan(east);
      }
    });
  }
});
