import { formatMoney } from '../i18n/format';
import { getLocale, tOptional } from '../i18n/runtime';
import { clearAuth, currentToken } from './authToken';

export type CountryCode = string;
export type OrderStatus = 'NEW' | 'CONFIRMED' | 'DONE' | 'CANCELLED';
const ORDER_STATUSES: OrderStatus[] = ['NEW', 'CONFIRMED', 'DONE', 'CANCELLED'];

export interface Country {
  code: CountryCode;
  name: string;
  currency: string;
  vatRate: number;
  timeZone: string;
  /** Cities with a café (contract v1.9), A to Z; the guest chooses one of them. */
  cities: string[];
}

export interface Category {
  id: number;
  name: string;
}

export interface Product {
  id: number;
  categoryId: number;
  name: string;
  description: string | null;
  imageUrl: string | null;
  /** Pieces in a portion (rolls, gyoza); null when not counted. */
  pieces: number | null;
  weightGrams: number | null;
  /** Drinks have a volume instead of a weight. */
  volumeMl: number | null;
  price: number;
  currency: string;
  inStock: boolean;
  hasModifiers: boolean;
  /** A set (contract v1.10): its dishes one by one in this country; null for a dish. */
  regularPrice: number | null;
  /** The dishes of a set in their order; empty for a dish. */
  setItems: SetItem[];
}

/** One dish of a set and how many of it the set has. */
export interface SetItem {
  productId: number;
  name: string;
  quantity: number;
}

export interface ProductModifier {
  id: number;
  type: 'SIZE' | 'EXTRA';
  name: string;
  priceDelta: number;
}

export interface ProductDetail extends Product {
  modifiers: ProductModifier[];
}

export interface CartQuoteRequest {
  country: CountryCode;
  scenario: 'PICKUP' | 'DELIVERY';
  deliveryZoneId?: number;
  items: Array<{
    productId: number;
    quantity: number;
    modifierIds: number[];
  }>;
}

export interface CartQuoteLine {
  productId: number;
  name: string;
  modifiers: ProductModifier[];
  quantity: number;
  unitPrice: number;
  lineTotal: number;
}

export interface CartQuote {
  country: CountryCode;
  currency: string;
  scenario: 'PICKUP' | 'DELIVERY';
  lines: CartQuoteLine[];
  subtotal: number;
  vatRate: number;
  vat: number;
  amountInclVat: number;
  discountRate: number;
  discount: number;
  deliveryFee: number;
  total: number;
}

/** WGS 84 degrees of a café on the map (contract v1.7). */
export interface CafeLocation {
  latitude: number;
  longitude: number;
}

export interface Cafe {
  id: number;
  country: CountryCode;
  name: string;
  /** English, as in the address (contract v1.9). */
  city: string;
  address: string;
  phone: string | null;
  opensAt: string;
  closesAt: string;
  timeZone: string;
  /** Null when the café is not on the map yet. */
  location: CafeLocation | null;
}

export interface DeliveryZone {
  id: number;
  country: CountryCode;
  name: string;
  cafeId: number;
  fee: number;
  currency: string;
  deliveryMinutes: number;
}

export interface TimeSlot {
  start: string;
  end: string;
  capacity: number;
  remaining: number;
  available: boolean;
  reason: 'FULL' | 'PAST' | null;
}

export interface TimeSlots {
  cafeId: number;
  date: string;
  timeZone: string;
  slots: TimeSlot[];
}

export interface OrderRequest {
  country: CountryCode;
  orderDate: string;
  scenario: 'PICKUP' | 'DELIVERY';
  cafeId: number | null;
  deliveryZoneId: number | null;
  deliveryAddress: string | null;
  slotStart: string;
  contactName: string;
  contactPhone: string;
  contactEmail?: string | null;
  comment?: string | null;
  expectedTotal: number;
  items: CartQuoteRequest['items'];
}

export interface OrderResponse extends Omit<CartQuote, 'scenario'> {
  orderNumber: string;
  status: OrderStatus;
  createdAt: string;
  orderDate: string;
  slotStart: string;
  slotEnd: string;
  scenario: 'PICKUP' | 'DELIVERY';
  cafe: { id: number; name: string; address: string };
  deliveryZone: { id: number; name: string } | null;
  deliveryAddress: string | null;
  contactName: string;
  contactPhone: string;
}

export class ApiRequestError extends Error {
  status: number;
  code: string;
  fields: Record<string, string>;
  /** X-Request-Id of the failed call, for bug reports (API contract §1). */
  requestId?: string;
  /** Seconds from the Retry-After header (429 TOO_MANY_ATTEMPTS). */
  retryAfterSeconds?: number;

  constructor(status: number, message: string, code: string, fields: Record<string, string>) {
    super(message);
    this.name = 'ApiRequestError';
    this.status = status;
    this.code = code;
    this.fields = fields;
  }
}

// Codes whose message is about the request itself (shape, path or method), not something a
// guest can act on (API contract, error table).
const TECHNICAL_CODES = new Set([
  'VALIDATION_ERROR',
  'MALFORMED_REQUEST',
  'BAD_REQUEST',
  'NOT_FOUND',
  'METHOD_NOT_ALLOWED',
]);
// Opt-in: set VITE_API_MOCKS=true in .env.local to use src/services/mocks.ts instead of the
// backend. Never active in a production build.
const USE_API_MOCKS = import.meta.env.DEV && import.meta.env.VITE_API_MOCKS === 'true';

/**
 * Text for the guest. Business errors from the API (4xx with their own code, for example
 * OUT_OF_STOCK) are written for people and are shown as they are. Network errors ("Failed to
 * fetch"), server errors and broken answers get the page's own clear text instead.
 */
export function guestErrorMessage(error: unknown, fallback: string): string {
  if (
    error instanceof ApiRequestError &&
    error.status >= 400 &&
    error.status < 500 &&
    !TECHNICAL_CODES.has(error.code)
  ) {
    // Business errors get the text of their code in the language of the interface.
    return tOptional(`apiErrors.${error.code}`) ?? error.message;
  }
  return fallback;
}

export const fallbackCountries: Country[] = [
  {
    code: 'UZB',
    name: 'Uzbekistan',
    currency: 'UZS',
    vatRate: 15,
    timeZone: 'Asia/Tashkent',
    cities: ['Tashkent'],
  },
  {
    code: 'KAZ',
    name: 'Kazakhstan',
    currency: 'KZT',
    vatRate: 20,
    timeZone: 'Asia/Almaty',
    cities: ['Almaty'],
  },
  {
    code: 'GEO',
    name: 'Georgia',
    currency: 'GEL',
    vatRate: 12,
    timeZone: 'Asia/Tbilisi',
    cities: ['Tbilisi'],
  },
  {
    code: 'UKR',
    name: 'Ukraine',
    currency: 'UAH',
    vatRate: 8,
    timeZone: 'Europe/Kyiv',
    cities: ['Kyiv'],
  },
  {
    code: 'CHN',
    name: 'China',
    currency: 'CNY',
    vatRate: 18,
    timeZone: 'Asia/Shanghai',
    cities: ['Shanghai'],
  },
  {
    code: 'GBR',
    name: 'United Kingdom',
    currency: 'GBP',
    vatRate: 20,
    timeZone: 'Europe/London',
    cities: ['London'],
  },
  {
    code: 'USA',
    name: 'United States',
    currency: 'USD',
    vatRate: 7,
    timeZone: 'America/New_York',
    cities: ['Boston'],
  },
];

export function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

export function isPositiveInteger(value: unknown): value is number {
  return typeof value === 'number' && Number.isInteger(value) && value > 0;
}

/**
 * Sends a request with the login token (if any) and turns every error into ApiRequestError, so
 * the pages handle errors in one way (API contract §4). A 401 for a request that carried a token
 * means the token is no longer valid: it is deleted and the guest flow works again.
 */
export async function apiFetch(path: string, init?: RequestInit): Promise<Response> {
  const headers = new Headers(init?.headers);
  if (!headers.has('Accept')) headers.set('Accept', 'application/json');
  const token = currentToken();
  if (token) headers.set('Authorization', `Bearer ${token}`);
  const response = await fetch(path, { ...init, headers });

  if (!response.ok) {
    if (response.status === 401 && token) clearAuth('rejected');
    const body: unknown = await response.json().catch(() => null);
    const fields: Record<string, string> = {};

    if (isRecord(body) && isRecord(body.fields)) {
      for (const [field, message] of Object.entries(body.fields)) {
        if (typeof message === 'string') fields[field] = message;
      }
    }

    if (isRecord(body) && typeof body.code === 'string' && typeof body.message === 'string') {
      const error = new ApiRequestError(response.status, body.message, body.code, fields);
      if (typeof body.requestId === 'string') error.requestId = body.requestId;
      const retryAfter = Number(response.headers.get('Retry-After'));
      if (Number.isFinite(retryAfter) && retryAfter > 0) error.retryAfterSeconds = retryAfter;
      throw error;
    }

    throw new Error(`Request failed (${response.status} ${response.statusText}).`);
  }

  return response;
}

export async function requestJson(
  path: string,
  init?: RequestInit,
  expectedStatus?: number,
): Promise<unknown> {
  const response = await apiFetch(path, init);

  if (expectedStatus !== undefined && response.status !== expectedStatus) {
    throw new Error(`Request expected status ${expectedStatus} but received ${response.status}.`);
  }

  return response.json();
}

function parseModifier(value: unknown, source: string): ProductModifier {
  if (
    !isRecord(value) ||
    typeof value.id !== 'number' ||
    !Number.isInteger(value.id) ||
    value.id <= 0 ||
    (value.type !== 'SIZE' && value.type !== 'EXTRA') ||
    typeof value.name !== 'string' ||
    !value.name.trim() ||
    typeof value.priceDelta !== 'number' ||
    !Number.isFinite(value.priceDelta) ||
    value.priceDelta < 0
  ) {
    throw new Error(`${source} returned an invalid modifier.`);
  }

  return {
    id: value.id,
    type: value.type,
    name: value.name,
    priceDelta: value.priceDelta,
  };
}

function parseCartQuote(value: unknown): CartQuote {
  if (
    !isRecord(value) ||
    typeof value.country !== 'string' ||
    !/^[A-Z]{3}$/.test(value.country) ||
    typeof value.currency !== 'string' ||
    !/^[A-Z]{3}$/.test(value.currency) ||
    !Array.isArray(value.lines) ||
    typeof value.subtotal !== 'number' ||
    !Number.isFinite(value.subtotal) ||
    value.subtotal < 0 ||
    typeof value.vatRate !== 'number' ||
    !Number.isFinite(value.vatRate) ||
    value.vatRate < 0 ||
    value.vatRate > 100 ||
    typeof value.vat !== 'number' ||
    !Number.isFinite(value.vat) ||
    value.vat < 0 ||
    typeof value.amountInclVat !== 'number' ||
    !Number.isFinite(value.amountInclVat) ||
    value.amountInclVat < 0 ||
    (value.scenario !== 'PICKUP' && value.scenario !== 'DELIVERY') ||
    typeof value.discountRate !== 'number' ||
    !Number.isFinite(value.discountRate) ||
    value.discountRate < 0 ||
    value.discountRate > 100 ||
    typeof value.discount !== 'number' ||
    !Number.isFinite(value.discount) ||
    value.discount < 0 ||
    typeof value.deliveryFee !== 'number' ||
    !Number.isFinite(value.deliveryFee) ||
    value.deliveryFee < 0 ||
    typeof value.total !== 'number' ||
    !Number.isFinite(value.total) ||
    value.total < 0
  ) {
    throw new Error('Cart quote API returned an invalid response.');
  }

  const lines = value.lines.map((line: unknown): CartQuoteLine => {
    if (
      !isRecord(line) ||
      typeof line.productId !== 'number' ||
      !Number.isInteger(line.productId) ||
      line.productId <= 0 ||
      typeof line.name !== 'string' ||
      !line.name.trim() ||
      !Array.isArray(line.modifiers) ||
      typeof line.quantity !== 'number' ||
      !Number.isInteger(line.quantity) ||
      line.quantity < 1 ||
      line.quantity > 99 ||
      typeof line.unitPrice !== 'number' ||
      !Number.isFinite(line.unitPrice) ||
      line.unitPrice < 0 ||
      typeof line.lineTotal !== 'number' ||
      !Number.isFinite(line.lineTotal) ||
      line.lineTotal < 0
    ) {
      throw new Error('Cart quote API returned an invalid line.');
    }

    return {
      productId: line.productId,
      name: line.name,
      modifiers: line.modifiers.map((modifier: unknown) =>
        parseModifier(modifier, 'Cart quote API'),
      ),
      quantity: line.quantity,
      unitPrice: line.unitPrice,
      lineTotal: line.lineTotal,
    };
  });

  return {
    country: value.country,
    currency: value.currency,
    scenario: value.scenario,
    lines,
    subtotal: value.subtotal,
    vatRate: value.vatRate,
    vat: value.vat,
    amountInclVat: value.amountInclVat,
    discountRate: value.discountRate,
    discount: value.discount,
    deliveryFee: value.deliveryFee,
    total: value.total,
  };
}

export function parseOrderResponse(value: unknown): OrderResponse {
  if (!isRecord(value)) throw new Error('Orders API returned an invalid response.');
  const quote = parseCartQuote(value);
  const deliveryZone = parseOrderDeliveryZone(value.deliveryZone);
  if (
    typeof value.orderNumber !== 'string' ||
    !value.orderNumber.trim() ||
    !ORDER_STATUSES.includes(value.status as OrderStatus) ||
    typeof value.createdAt !== 'string' ||
    typeof value.orderDate !== 'string' ||
    !/^\d{4}-\d{2}-\d{2}$/.test(value.orderDate) ||
    typeof value.slotStart !== 'string' ||
    !/^\d{2}:\d{2}$/.test(value.slotStart) ||
    typeof value.slotEnd !== 'string' ||
    !/^\d{2}:\d{2}$/.test(value.slotEnd) ||
    !isRecord(value.cafe) ||
    !isPositiveInteger(value.cafe.id) ||
    typeof value.cafe.name !== 'string' ||
    !value.cafe.name.trim() ||
    typeof value.cafe.address !== 'string' ||
    !value.cafe.address.trim() ||
    (value.deliveryAddress !== null && typeof value.deliveryAddress !== 'string') ||
    typeof value.contactName !== 'string' ||
    typeof value.contactPhone !== 'string'
  ) {
    throw new Error('Orders API returned an invalid response.');
  }

  return {
    ...quote,
    orderNumber: value.orderNumber,
    status: value.status as OrderStatus,
    createdAt: value.createdAt,
    orderDate: value.orderDate,
    slotStart: value.slotStart,
    slotEnd: value.slotEnd,
    scenario: quote.scenario,
    cafe: {
      id: value.cafe.id,
      name: value.cafe.name,
      address: value.cafe.address,
    },
    deliveryZone: deliveryZone === null ? null : deliveryZone,
    deliveryAddress: value.deliveryAddress,
    contactName: value.contactName,
    contactPhone: value.contactPhone,
  };
}

function parseOrderDeliveryZone(value: unknown): { id: number; name: string } | null {
  if (value === null) return null;
  if (
    !isRecord(value) ||
    !isPositiveInteger(value.id) ||
    typeof value.name !== 'string' ||
    !value.name.trim()
  ) {
    throw new Error('Orders API returned an invalid delivery zone.');
  }
  return { id: value.id, name: value.name };
}

function parseCountries(value: unknown): Country[] {
  if (!Array.isArray(value) || value.length === 0) {
    throw new Error('Countries API must return at least one country.');
  }

  const parsed = value.map((item: unknown): Country => {
    if (
      !isRecord(item) ||
      typeof item.code !== 'string' ||
      !/^[A-Z]{3}$/.test(item.code) ||
      typeof item.name !== 'string' ||
      !item.name.trim() ||
      typeof item.currency !== 'string' ||
      !/^[A-Z]{3}$/.test(item.currency) ||
      typeof item.vatRate !== 'number' ||
      !Number.isFinite(item.vatRate) ||
      item.vatRate < 0 ||
      item.vatRate > 100 ||
      (item.timeZone !== undefined &&
        (typeof item.timeZone !== 'string' || !item.timeZone.trim())) ||
      (item.cities !== undefined && !isNameList(item.cities))
    ) {
      throw new Error('Countries API returned an invalid country.');
    }

    return {
      code: item.code,
      name: item.name,
      currency: item.currency,
      vatRate: item.vatRate,
      timeZone:
        typeof item.timeZone === 'string'
          ? item.timeZone
          : (fallbackCountries.find(({ code }) => code === item.code)?.timeZone ?? 'UTC'),
      // An older server sends no cities: the country is still offered, by its name.
      cities: isNameList(item.cities) ? item.cities : [],
    };
  });

  if (new Set(parsed.map(({ code }) => code)).size !== parsed.length) {
    throw new Error('Countries API returned duplicate country codes.');
  }

  return parsed;
}

function isNameList(value: unknown): value is string[] {
  return (
    Array.isArray(value) && value.every((name) => typeof name === 'string' && name.trim() !== '')
  );
}

function parseCategory(item: unknown): Category {
  if (
    !isRecord(item) ||
    typeof item.id !== 'number' ||
    !Number.isInteger(item.id) ||
    item.id <= 0 ||
    typeof item.name !== 'string' ||
    !item.name.trim()
  ) {
    throw new Error('Categories API returned an invalid category.');
  }

  return { id: item.id, name: item.name };
}

/** A portion amount: a positive whole number, or null (missing in older answers: null too). */
function isAmount(value: unknown): value is number | null | undefined {
  return (
    value === null ||
    value === undefined ||
    (typeof value === 'number' && Number.isInteger(value) && value > 0)
  );
}

function parseProduct(item: unknown): Product {
  if (
    !isRecord(item) ||
    typeof item.id !== 'number' ||
    !Number.isInteger(item.id) ||
    item.id <= 0 ||
    typeof item.categoryId !== 'number' ||
    !Number.isInteger(item.categoryId) ||
    item.categoryId <= 0 ||
    typeof item.name !== 'string' ||
    !item.name.trim() ||
    (item.description !== null && typeof item.description !== 'string') ||
    (item.imageUrl !== null && typeof item.imageUrl !== 'string') ||
    !isAmount(item.pieces) ||
    !isAmount(item.weightGrams) ||
    !isAmount(item.volumeMl) ||
    typeof item.price !== 'number' ||
    !Number.isFinite(item.price) ||
    item.price < 0 ||
    typeof item.currency !== 'string' ||
    !/^[A-Z]{3}$/.test(item.currency) ||
    typeof item.inStock !== 'boolean' ||
    typeof item.hasModifiers !== 'boolean' ||
    (item.regularPrice != null &&
      (typeof item.regularPrice !== 'number' ||
        !Number.isFinite(item.regularPrice) ||
        item.regularPrice < 0)) ||
    (item.setItems !== undefined && !Array.isArray(item.setItems))
  ) {
    throw new Error('Products API returned an invalid product.');
  }

  return {
    id: item.id,
    categoryId: item.categoryId,
    name: item.name,
    description: item.description,
    imageUrl: item.imageUrl,
    pieces: item.pieces ?? null,
    weightGrams: item.weightGrams ?? null,
    volumeMl: item.volumeMl ?? null,
    price: item.price,
    currency: item.currency,
    inStock: item.inStock,
    hasModifiers: item.hasModifiers,
    regularPrice: typeof item.regularPrice === 'number' ? item.regularPrice : null,
    setItems: Array.isArray(item.setItems) ? item.setItems.map(parseSetItem) : [],
  };
}

function parseSetItem(value: unknown): SetItem {
  if (
    !isRecord(value) ||
    !isPositiveInteger(value.productId) ||
    typeof value.name !== 'string' ||
    !value.name.trim() ||
    !isPositiveInteger(value.quantity)
  ) {
    throw new Error('Products API returned an invalid dish of a set.');
  }
  return { productId: value.productId, name: value.name, quantity: value.quantity };
}

function parseProductDetails(item: unknown): ProductDetail {
  const product = parseProduct(item);
  if (!isRecord(item) || !Array.isArray(item.modifiers)) {
    throw new Error('Product details API returned an invalid modifier list.');
  }

  const modifiers = item.modifiers.map((modifier: unknown) =>
    parseModifier(modifier, 'Product details API'),
  );

  return { ...product, modifiers };
}

function isDegrees(value: unknown, limit: number): value is number {
  return typeof value === 'number' && Number.isFinite(value) && Math.abs(value) <= limit;
}

// Both degrees or none (an older server sends none): a café without them is not on the map.
function parseCafeLocation(latitude: unknown, longitude: unknown): CafeLocation | null {
  if (latitude == null && longitude == null) return null;
  if (!isDegrees(latitude, 90) || !isDegrees(longitude, 180)) {
    throw new Error('Cafés API returned an invalid café location.');
  }
  return { latitude, longitude };
}

function parseCafe(value: unknown): Cafe {
  if (
    !isRecord(value) ||
    !isPositiveInteger(value.id) ||
    typeof value.country !== 'string' ||
    typeof value.name !== 'string' ||
    !value.name.trim() ||
    (value.city !== undefined && typeof value.city !== 'string') ||
    typeof value.address !== 'string' ||
    !value.address.trim() ||
    (value.phone !== null && typeof value.phone !== 'string') ||
    typeof value.opensAt !== 'string' ||
    !/^\d{2}:\d{2}$/.test(value.opensAt) ||
    typeof value.closesAt !== 'string' ||
    !/^\d{2}:\d{2}$/.test(value.closesAt) ||
    typeof value.timeZone !== 'string' ||
    !value.timeZone.trim()
  ) {
    throw new Error('Cafés API returned an invalid café.');
  }
  return {
    id: value.id,
    country: value.country,
    name: value.name,
    // An older server sends no city: the café belongs to the first city of its country.
    city: typeof value.city === 'string' ? value.city : '',
    address: value.address,
    phone: value.phone,
    opensAt: value.opensAt,
    closesAt: value.closesAt,
    timeZone: value.timeZone,
    location: parseCafeLocation(value.latitude, value.longitude),
  };
}

function parseDeliveryZone(value: unknown): DeliveryZone {
  if (
    !isRecord(value) ||
    !isPositiveInteger(value.id) ||
    typeof value.country !== 'string' ||
    typeof value.name !== 'string' ||
    !value.name.trim() ||
    !isPositiveInteger(value.cafeId) ||
    typeof value.fee !== 'number' ||
    !Number.isFinite(value.fee) ||
    value.fee < 0 ||
    typeof value.currency !== 'string' ||
    !/^[A-Z]{3}$/.test(value.currency) ||
    typeof value.deliveryMinutes !== 'number' ||
    !Number.isInteger(value.deliveryMinutes) ||
    value.deliveryMinutes < 0
  ) {
    throw new Error('Delivery zones API returned an invalid zone.');
  }
  return {
    id: value.id,
    country: value.country,
    name: value.name,
    cafeId: value.cafeId,
    fee: value.fee,
    currency: value.currency,
    deliveryMinutes: value.deliveryMinutes,
  };
}

function parseTimeSlot(value: unknown): TimeSlot {
  if (
    !isRecord(value) ||
    typeof value.start !== 'string' ||
    !/^\d{2}:\d{2}$/.test(value.start) ||
    typeof value.end !== 'string' ||
    !/^\d{2}:\d{2}$/.test(value.end) ||
    typeof value.capacity !== 'number' ||
    !Number.isInteger(value.capacity) ||
    value.capacity < 0 ||
    typeof value.remaining !== 'number' ||
    !Number.isInteger(value.remaining) ||
    value.remaining < 0 ||
    value.remaining > value.capacity ||
    typeof value.available !== 'boolean' ||
    (value.reason !== null && value.reason !== 'FULL' && value.reason !== 'PAST')
  ) {
    throw new Error('Time slots API returned an invalid slot.');
  }
  return {
    start: value.start,
    end: value.end,
    capacity: value.capacity,
    remaining: value.remaining,
    available: value.available,
    reason: value.reason,
  };
}

function parseTimeSlots(value: unknown): TimeSlots {
  if (
    !isRecord(value) ||
    !isPositiveInteger(value.cafeId) ||
    typeof value.date !== 'string' ||
    !/^\d{4}-\d{2}-\d{2}$/.test(value.date) ||
    typeof value.timeZone !== 'string' ||
    !value.timeZone.trim() ||
    !Array.isArray(value.slots)
  ) {
    throw new Error('Time slots API returned an invalid response.');
  }
  return {
    cafeId: value.cafeId,
    date: value.date,
    timeZone: value.timeZone,
    slots: value.slots.map(parseTimeSlot),
  };
}

let countriesRequest: Promise<Country[]> | undefined;

export function loadCountries(): Promise<Country[]> {
  if (!countriesRequest) {
    countriesRequest = requestJson('/api/v1/countries')
      .then(parseCountries)
      .catch((error: unknown) => {
        countriesRequest = undefined;
        throw error;
      });
  }

  return countriesRequest;
}

export async function loadCategories(country: CountryCode): Promise<Category[]> {
  const query = new URLSearchParams({ country });
  const response = await requestJson(`/api/v1/categories?${query}`);
  if (!Array.isArray(response)) {
    throw new Error('Categories API must return a list.');
  }
  return response.map(parseCategory);
}

export async function loadProducts(country: CountryCode, categoryId?: number): Promise<Product[]> {
  const query = new URLSearchParams({ country });
  if (categoryId !== undefined) query.set('categoryId', String(categoryId));

  const response = await requestJson(`/api/v1/products?${query}`);
  if (!Array.isArray(response)) {
    throw new Error('Products API must return a list.');
  }
  return response.map(parseProduct);
}

export async function loadProductDetails(id: number, country: CountryCode): Promise<ProductDetail> {
  const query = new URLSearchParams({ country });
  return parseProductDetails(await requestJson(`/api/v1/products/${id}?${query}`));
}

export async function quoteCart(request: CartQuoteRequest): Promise<CartQuote> {
  if (USE_API_MOCKS) {
    const { mockQuoteCart } = await import('./mocks');
    return mockQuoteCart(request);
  }

  const response = await requestJson('/api/v1/cart/quote', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  });
  const quote = parseCartQuote(response);
  if (
    quote.country !== request.country ||
    quote.scenario !== request.scenario ||
    quote.lines.length !== request.items.length ||
    quote.lines.some(
      (line, index) =>
        line.productId !== request.items[index].productId ||
        line.quantity !== request.items[index].quantity,
    )
  ) {
    throw new Error('Cart quote API returned a response that does not match the request.');
  }
  return quote;
}

export async function loadCafes(country: CountryCode): Promise<Cafe[]> {
  if (USE_API_MOCKS) {
    const { mockLoadCafes } = await import('./mocks');
    return mockLoadCafes(country);
  }

  const query = new URLSearchParams({ country });
  const response = await requestJson(`/api/v1/cafes?${query}`);
  if (!isRecord(response) || !Array.isArray(response.items)) {
    throw new Error('Cafés API must return an items list.');
  }
  return response.items.map(parseCafe);
}

export async function loadDeliveryZones(country: CountryCode): Promise<DeliveryZone[]> {
  if (USE_API_MOCKS) {
    const { mockLoadDeliveryZones } = await import('./mocks');
    return mockLoadDeliveryZones(country);
  }

  const query = new URLSearchParams({ country });
  const response = await requestJson(`/api/v1/delivery-zones?${query}`);
  if (!isRecord(response) || !Array.isArray(response.items)) {
    throw new Error('Delivery zones API must return an items list.');
  }
  return response.items.map(parseDeliveryZone);
}

export async function loadTimeSlots(cafeId: number, date: string): Promise<TimeSlots> {
  if (USE_API_MOCKS) {
    const { mockLoadTimeSlots } = await import('./mocks');
    return mockLoadTimeSlots(cafeId, date);
  }

  const query = new URLSearchParams({ cafeId: String(cafeId), date });
  return parseTimeSlots(await requestJson(`/api/v1/time-slots?${query}`));
}

export async function placeOrder(
  request: OrderRequest,
  idempotencyKey: string,
): Promise<OrderResponse> {
  if (!idempotencyKey) throw new Error('An idempotency key is required to place an order.');
  if (USE_API_MOCKS) {
    const { mockPlaceOrder } = await import('./mocks');
    return mockPlaceOrder(request);
  }

  return parseOrderResponse(
    await requestJson(
      '/api/v1/orders',
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Idempotency-Key': idempotencyKey,
        },
        body: JSON.stringify(request),
      },
      201,
    ),
  );
}

/** Money in the format of the interface language (176 249,50 soʻm, ¥31.00), always 2 decimals. */
export function formatPrice(amount: number, currency: string): string {
  return formatMoney(amount, currency, getLocale());
}
