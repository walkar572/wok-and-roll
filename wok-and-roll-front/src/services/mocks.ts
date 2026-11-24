// Temporary contract fixtures (API contract §3.5) for local work without a backend.
// Enabled only with VITE_API_MOCKS=true in .env.local. Delete this file when the APIs are merged.
import {
  ApiRequestError,
  type Cafe,
  type CartQuote,
  type CartQuoteRequest,
  type CountryCode,
  type DeliveryZone,
  type OrderRequest,
  type OrderResponse,
  type TimeSlot,
  type TimeSlots,
} from './api';

export function mockQuoteCart(request: CartQuoteRequest): CartQuote {
  const isContractExampleCart =
    request.country === 'UZB' &&
    request.items.length === 2 &&
    request.items[0].productId === 5 &&
    request.items[0].quantity === 2 &&
    request.items[0].modifierIds.join(',') === '2,3' &&
    request.items[1].productId === 8 &&
    request.items[1].quantity === 1 &&
    request.items[1].modifierIds.length === 0;
  const isContractExampleScenario =
    request.scenario === 'PICKUP'
      ? request.deliveryZoneId === undefined
      : request.deliveryZoneId === 1;

  if (!isContractExampleCart || !isContractExampleScenario) {
    throw new Error('The checkout quote mock only supports the cart example in API contract §3.5.');
  }

  return {
    country: 'UZB',
    currency: 'UZS',
    scenario: request.scenario,
    lines: [
      {
        productId: 5,
        name: 'Firecracker udon',
        modifiers: [
          { id: 2, type: 'SIZE', name: 'Large', priceDelta: 12 },
          { id: 3, type: 'EXTRA', name: 'Extra chicken', priceDelta: 9 },
        ],
        quantity: 2,
        unitPrice: 69,
        lineTotal: 138,
      },
      {
        productId: 8,
        name: 'Gyoza',
        modifiers: [],
        quantity: 1,
        unitPrice: 32,
        lineTotal: 32,
      },
    ],
    subtotal: 170,
    vatRate: 15,
    vat: 25.5,
    amountInclVat: 195.5,
    discountRate: 3,
    discount: 5.87,
    deliveryFee: request.scenario === 'PICKUP' ? 0 : 20,
    total: request.scenario === 'PICKUP' ? 189.63 : 209.63,
  };
}

export function mockLoadCafes(country: CountryCode): Cafe[] {
  if (country !== 'UZB') {
    throw new Error('The café mock only supports the API contract example country, UZB.');
  }

  return [
    {
      id: 1,
      country: 'UZB',
      name: 'Wok & Roll Chilanzar',
      address: '12 Bunyodkor Ave, Tashkent',
      phone: '+998712000001',
      opensAt: '10:00',
      closesAt: '22:00',
      timeZone: 'Asia/Tashkent',
      location: { latitude: 41.272187, longitude: 69.203167 },
    },
  ];
}

export function mockLoadDeliveryZones(country: CountryCode): DeliveryZone[] {
  if (country !== 'UZB') {
    throw new Error('The delivery-zone mock only supports the API contract example country, UZB.');
  }

  return [
    {
      id: 1,
      country: 'UZB',
      name: 'Tashkent: Center',
      cafeId: 1,
      fee: 20,
      currency: 'UZS',
      deliveryMinutes: 45,
    },
  ];
}

export function mockLoadTimeSlots(cafeId: number, date: string): TimeSlots {
  if (cafeId !== 1) {
    throw new Error('The time-slot mock only supports the API contract example café, id 1.');
  }

  const slots: TimeSlot[] = [
    { start: '09:00', end: '09:30', capacity: 5, remaining: 5, available: false, reason: 'PAST' },
    { start: '10:00', end: '10:30', capacity: 5, remaining: 5, available: true, reason: null },
    { start: '10:30', end: '11:00', capacity: 5, remaining: 0, available: false, reason: 'FULL' },
    { start: '21:30', end: '22:00', capacity: 5, remaining: 4, available: true, reason: null },
  ];
  return { cafeId, date, timeZone: 'Asia/Tashkent', slots };
}

export function mockPlaceOrder(request: OrderRequest): OrderResponse {
  const quote = mockQuoteCart({
    country: request.country,
    scenario: request.scenario,
    ...(request.deliveryZoneId === null ? {} : { deliveryZoneId: request.deliveryZoneId }),
    items: request.items,
  });
  if (quote.total !== request.expectedTotal) {
    throw new ApiRequestError(
      409,
      'The order total changed. Please review the new total.',
      'PRICE_CHANGED',
      {
        expectedTotal: 'The order total changed. Please review the new total.',
      },
    );
  }

  const slotEndByStart: Record<string, string> = {
    '10:00': '10:30',
    '21:30': '22:00',
  };

  return {
    ...quote,
    orderNumber: 'WR-7K3M9Q2X',
    status: 'NEW',
    createdAt: '2026-11-18T09:15:00Z',
    orderDate: request.orderDate,
    slotStart: request.slotStart,
    slotEnd: slotEndByStart[request.slotStart] ?? '22:00',
    cafe: {
      id: 1,
      name: 'Wok & Roll Chilanzar',
      address: '12 Bunyodkor Ave, Tashkent',
    },
    deliveryZone: request.scenario === 'DELIVERY' ? { id: 1, name: 'Tashkent: Center' } : null,
    deliveryAddress: request.scenario === 'DELIVERY' ? request.deliveryAddress : null,
    contactName: request.contactName,
    contactPhone: request.contactPhone,
  };
}
