// Admin catalog (API contract §3.15, #185) and the staff work list (§3.14).
import { parseOrderResponse, requestJson, type OrderResponse, type OrderStatus } from './api';
import {
  bool,
  boolOrNull,
  list,
  num,
  numOrNull,
  page,
  record,
  str,
  strOrNull,
  type Page,
} from './parse';

export interface AdminProduct {
  id: number;
  categoryId: number;
  categoryName: string;
  name: string;
  description: string | null;
  imageUrl: string | null;
  visible: boolean;
  version: number;
  country: string;
  price: number | null;
  currency: string;
  inStock: boolean | null;
  priceVersion: number | null;
}

export interface PriceResult {
  productId: number;
  country: string;
  price: number;
  currency: string;
  inStock: boolean;
  version: number;
}

function parseProduct(value: unknown): AdminProduct {
  const source = 'Admin products API';
  const item = record(value, source, 'item');
  return {
    id: num(item, 'id', source),
    categoryId: num(item, 'categoryId', source),
    categoryName: str(item, 'categoryName', source),
    name: str(item, 'name', source),
    description: strOrNull(item, 'description', source),
    imageUrl: strOrNull(item, 'imageUrl', source),
    visible: bool(item, 'visible', source),
    version: num(item, 'version', source),
    country: str(item, 'country', source),
    price: numOrNull(item, 'price', source),
    currency: str(item, 'currency', source),
    inStock: boolOrNull(item, 'inStock', source),
    priceVersion: numOrNull(item, 'priceVersion', source),
  };
}

/** All products of the catalog with the price of one country (the seed has 10, one page). */
export async function loadAdminProducts(country: string): Promise<AdminProduct[]> {
  const items: AdminProduct[] = [];
  let token: string | null = null;
  do {
    const query = new URLSearchParams({ country, pageSize: '100' });
    if (token) query.set('pageToken', token);
    const result: Page<AdminProduct> = page(
      await requestJson(`/api/v1/admin/products?${query}`),
      'Admin products API',
      parseProduct,
    );
    items.push(...result.items);
    token = result.nextPageToken;
  } while (token);
  return items;
}

export async function updateProductVisibility(
  product: AdminProduct,
  visible: boolean,
): Promise<AdminProduct> {
  return parseProduct(
    await requestJson(
      `/api/v1/admin/products/${product.id}?country=${encodeURIComponent(product.country)}`,
      {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ visible, version: product.version }),
      },
    ),
  );
}

export async function savePrice(
  product: AdminProduct,
  price: number,
  inStock: boolean,
): Promise<PriceResult> {
  const source = 'Admin price API';
  const body = record(
    await requestJson(
      `/api/v1/admin/products/${product.id}/prices/${encodeURIComponent(product.country)}`,
      {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ price, inStock, version: product.priceVersion }),
      },
    ),
    source,
  );
  return {
    productId: num(body, 'productId', source),
    country: str(body, 'country', source),
    price: num(body, 'price', source),
    currency: str(body, 'currency', source),
    inStock: bool(body, 'inStock', source),
    version: num(body, 'version', source),
  };
}

// ---- staff -----------------------------------------------------------------------------------

export interface StaffOrder {
  orderNumber: string;
  slotStart: string;
  slotEnd: string;
  scenario: 'PICKUP' | 'DELIVERY';
  status: OrderStatus;
  contactName: string;
  contactPhone: string;
  deliveryAddress: string | null;
  itemCount: number;
  total: number;
  currency: string;
}

export interface StaffBooking {
  bookingNumber: string;
  time: string;
  endTime: string;
  guests: number;
  tableSeats: number;
  status: string;
  contactName: string;
  contactPhone: string;
}

function parseStaffOrder(value: unknown): StaffOrder {
  const source = 'Staff orders API';
  const item = record(value, source, 'item');
  return {
    orderNumber: str(item, 'orderNumber', source),
    slotStart: str(item, 'slotStart', source),
    slotEnd: str(item, 'slotEnd', source),
    scenario: str(item, 'scenario', source) as StaffOrder['scenario'],
    status: str(item, 'status', source) as OrderStatus,
    contactName: str(item, 'contactName', source),
    contactPhone: str(item, 'contactPhone', source),
    deliveryAddress: strOrNull(item, 'deliveryAddress', source),
    itemCount: num(item, 'itemCount', source),
    total: num(item, 'total', source),
    currency: str(item, 'currency', source),
  };
}

export async function loadStaffOrders(cafeId: number, date: string): Promise<StaffOrder[]> {
  const items: StaffOrder[] = [];
  let token: string | null = null;
  do {
    const query = new URLSearchParams({ cafeId: String(cafeId), date, pageSize: '100' });
    if (token) query.set('pageToken', token);
    const result: Page<StaffOrder> = page(
      await requestJson(`/api/v1/staff/orders?${query}`),
      'Staff orders API',
      parseStaffOrder,
    );
    items.push(...result.items);
    token = result.nextPageToken;
  } while (token);
  return items;
}

export async function changeOrderStatus(
  orderNumber: string,
  status: OrderStatus,
): Promise<OrderResponse> {
  return parseOrderResponse(
    await requestJson(`/api/v1/staff/orders/${encodeURIComponent(orderNumber)}`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ status }),
    }),
  );
}

export async function loadStaffBookings(cafeId: number, date: string): Promise<StaffBooking[]> {
  const source = 'Staff bookings API';
  const body = record(
    await requestJson(
      `/api/v1/staff/bookings?${new URLSearchParams({ cafeId: String(cafeId), date })}`,
    ),
    source,
  );
  return list(body.items, source).map((value) => {
    const item = record(value, source, 'item');
    return {
      bookingNumber: str(item, 'bookingNumber', source),
      time: str(item, 'time', source),
      endTime: str(item, 'endTime', source),
      guests: num(item, 'guests', source),
      tableSeats: num(item, 'tableSeats', source),
      status: str(item, 'status', source),
      contactName: str(item, 'contactName', source),
      contactPhone: str(item, 'contactPhone', source),
    };
  });
}
