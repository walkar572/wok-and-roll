// The admin panel API, ADMIN only and read only (API contract §3.20, v1.11). Changes go through
// the APIs that own them: the order status through the staff API (§3.14, changeOrderStatus).
import { parseOrderResponse, requestJson, type OrderResponse, type OrderStatus } from './api';
import type { Role } from './authToken';
import {
  bool,
  list,
  num,
  page,
  pageQuery,
  record,
  str,
  strOrNull,
  type Json,
  type Page,
} from './parse';

export type BookingStatus = 'CONFIRMED' | 'CANCELLED';

export interface Named {
  id: number;
  name: string;
}

export interface Account {
  email: string;
  fullName: string;
}

export interface Overview {
  from: string;
  to: string;
  generatedAt: string;
  orders: {
    total: number;
    byStatus: Record<OrderStatus, number>;
    pickup: number;
    delivery: number;
  };
  bookings: { total: number; cancelled: number; guests: number };
  accounts: { total: number; newInPeriod: number; byRole: Record<string, number> };
  regions: Array<{
    country: string;
    currency: string;
    orders: number;
    revenue: number;
    averageCheck: number;
  }>;
  days: Array<{ date: string; orders: number }>;
  topDishes: Array<{ productId: number; name: string; quantity: number }>;
}

export interface AdminOrder {
  orderNumber: string;
  createdAt: string;
  orderDate: string;
  slotStart: string;
  slotEnd: string;
  country: string;
  currency: string;
  cafe: Named;
  scenario: 'PICKUP' | 'DELIVERY';
  status: OrderStatus;
  contactName: string;
  contactPhone: string;
  account: Account | null;
  itemCount: number;
  total: number;
}

export interface AdminOrderDetails {
  order: OrderResponse;
  contactEmail: string | null;
  comment: string | null;
  account: Account | null;
  updatedAt: string;
}

export interface AdminBooking {
  bookingNumber: string;
  createdAt: string;
  date: string;
  time: string;
  endTime: string;
  country: string;
  cafe: Named;
  guests: number;
  tableSeats: number;
  status: BookingStatus;
  contactName: string;
  contactPhone: string;
  contactEmail: string | null;
  comment: string | null;
  account: Account | null;
}

export interface AdminUser {
  id: number;
  email: string;
  fullName: string;
  phone: string | null;
  role: Role;
  createdAt: string;
  orders: number;
  lastOrderAt: string | null;
}

export interface AdminCafe {
  id: number;
  country: string;
  city: string;
  name: string;
  address: string;
  phone: string | null;
  opensAt: string;
  closesAt: string;
  timeZone: string;
  active: boolean;
  tables: number;
  seats: number;
  slotCapacity: number;
  today: string;
  todayOrders: number;
  todayBookings: number;
}

/** The filters of the order and booking lists; empty ones are not sent. */
export interface ListFilter {
  country?: string;
  cafeId?: string;
  status?: string;
  from?: string;
  to?: string;
  q?: string;
}

const PAGE_SIZE = 25;

function counts(value: unknown, source: string, field: string): Record<string, number> {
  const body = record(value, source, field);
  return Object.fromEntries(Object.keys(body).map((key) => [key, num(body, key, source)]));
}

function named(value: unknown, source: string, field: string): Named {
  const body = record(value, source, field);
  return { id: num(body, 'id', source), name: str(body, 'name', source) };
}

function account(item: Json, source: string): Account | null {
  if (item.account === null || item.account === undefined) return null;
  const body = record(item.account, source, 'account');
  return { email: str(body, 'email', source), fullName: str(body, 'fullName', source) };
}

function query(filter: ListFilter, pageToken?: string | null): string {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(filter)) {
    const text = typeof value === 'string' ? value.trim() : '';
    if (text) params.set(key, text);
  }
  params.set('pageSize', String(PAGE_SIZE));
  if (pageToken) params.set('pageToken', pageToken);
  return `?${params.toString()}`;
}

export async function loadOverview(from: string, to: string): Promise<Overview> {
  const source = 'Admin overview API';
  const body = record(
    await requestJson(
      `/api/v1/admin/overview?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`,
    ),
    source,
  );
  const orders = record(body.orders, source, 'orders');
  const bookings = record(body.bookings, source, 'bookings');
  const accounts = record(body.accounts, source, 'accounts');
  return {
    from: str(body, 'from', source),
    to: str(body, 'to', source),
    generatedAt: str(body, 'generatedAt', source),
    orders: {
      total: num(orders, 'total', source),
      byStatus: counts(orders.byStatus, source, 'byStatus') as Record<OrderStatus, number>,
      pickup: num(orders, 'pickup', source),
      delivery: num(orders, 'delivery', source),
    },
    bookings: {
      total: num(bookings, 'total', source),
      cancelled: num(bookings, 'cancelled', source),
      guests: num(bookings, 'guests', source),
    },
    accounts: {
      total: num(accounts, 'total', source),
      newInPeriod: num(accounts, 'newInPeriod', source),
      byRole: counts(accounts.byRole, source, 'byRole'),
    },
    regions: list(body.regions, source, 'regions').map((value) => {
      const item = record(value, source, 'regions');
      return {
        country: str(item, 'country', source),
        currency: str(item, 'currency', source),
        orders: num(item, 'orders', source),
        revenue: num(item, 'revenue', source),
        averageCheck: num(item, 'averageCheck', source),
      };
    }),
    days: list(body.days, source, 'days').map((value) => {
      const item = record(value, source, 'days');
      return { date: str(item, 'date', source), orders: num(item, 'orders', source) };
    }),
    topDishes: list(body.topDishes, source, 'topDishes').map((value) => {
      const item = record(value, source, 'topDishes');
      return {
        productId: num(item, 'productId', source),
        name: str(item, 'name', source),
        quantity: num(item, 'quantity', source),
      };
    }),
  };
}

function parseOrder(value: unknown): AdminOrder {
  const source = 'Admin orders API';
  const item = record(value, source, 'item');
  return {
    orderNumber: str(item, 'orderNumber', source),
    createdAt: str(item, 'createdAt', source),
    orderDate: str(item, 'orderDate', source),
    slotStart: str(item, 'slotStart', source),
    slotEnd: str(item, 'slotEnd', source),
    country: str(item, 'country', source),
    currency: str(item, 'currency', source),
    cafe: named(item.cafe, source, 'cafe'),
    scenario: str(item, 'scenario', source) as AdminOrder['scenario'],
    status: str(item, 'status', source) as OrderStatus,
    contactName: str(item, 'contactName', source),
    contactPhone: str(item, 'contactPhone', source),
    account: account(item, source),
    itemCount: num(item, 'itemCount', source),
    total: num(item, 'total', source),
  };
}

export async function loadAdminOrders(
  filter: ListFilter,
  pageToken?: string | null,
): Promise<Page<AdminOrder>> {
  return page(
    await requestJson(`/api/v1/admin/orders${query(filter, pageToken)}`),
    'Admin orders API',
    parseOrder,
  );
}

export async function loadAdminOrder(orderNumber: string): Promise<AdminOrderDetails> {
  const source = 'Admin order API';
  const body = record(
    await requestJson(`/api/v1/admin/orders/${encodeURIComponent(orderNumber)}`),
    source,
  );
  return {
    order: parseOrderResponse(body.order),
    contactEmail: strOrNull(body, 'contactEmail', source),
    comment: strOrNull(body, 'comment', source),
    account: account(body, source),
    updatedAt: str(body, 'updatedAt', source),
  };
}

function parseBooking(value: unknown): AdminBooking {
  const source = 'Admin bookings API';
  const item = record(value, source, 'item');
  return {
    bookingNumber: str(item, 'bookingNumber', source),
    createdAt: str(item, 'createdAt', source),
    date: str(item, 'date', source),
    time: str(item, 'time', source),
    endTime: str(item, 'endTime', source),
    country: str(item, 'country', source),
    cafe: named(item.cafe, source, 'cafe'),
    guests: num(item, 'guests', source),
    tableSeats: num(item, 'tableSeats', source),
    status: str(item, 'status', source) as BookingStatus,
    contactName: str(item, 'contactName', source),
    contactPhone: str(item, 'contactPhone', source),
    contactEmail: strOrNull(item, 'contactEmail', source),
    comment: strOrNull(item, 'comment', source),
    account: account(item, source),
  };
}

export async function loadAdminBookings(
  filter: ListFilter,
  pageToken?: string | null,
): Promise<Page<AdminBooking>> {
  return page(
    await requestJson(`/api/v1/admin/bookings${query(filter, pageToken)}`),
    'Admin bookings API',
    parseBooking,
  );
}

function parseUser(value: unknown): AdminUser {
  const source = 'Admin users API';
  const item = record(value, source, 'item');
  return {
    id: num(item, 'id', source),
    email: str(item, 'email', source),
    fullName: str(item, 'fullName', source),
    phone: strOrNull(item, 'phone', source),
    role: str(item, 'role', source) as Role,
    createdAt: str(item, 'createdAt', source),
    orders: num(item, 'orders', source),
    lastOrderAt: strOrNull(item, 'lastOrderAt', source),
  };
}

export async function loadAdminUsers(
  filter: { role?: string; q?: string },
  pageToken?: string | null,
): Promise<Page<AdminUser>> {
  const params = new URLSearchParams(pageQuery(PAGE_SIZE, pageToken).slice(1));
  if (filter.role) params.set('role', filter.role);
  if (filter.q?.trim()) params.set('q', filter.q.trim());
  return page(
    await requestJson(`/api/v1/admin/users?${params.toString()}`),
    'Admin users API',
    parseUser,
  );
}

export async function loadAdminCafes(): Promise<AdminCafe[]> {
  const source = 'Admin cafés API';
  const body = record(await requestJson('/api/v1/admin/cafes'), source);
  return list(body.items, source).map((value) => {
    const item = record(value, source, 'item');
    return {
      id: num(item, 'id', source),
      country: str(item, 'country', source),
      city: str(item, 'city', source),
      name: str(item, 'name', source),
      address: str(item, 'address', source),
      phone: strOrNull(item, 'phone', source),
      opensAt: str(item, 'opensAt', source),
      closesAt: str(item, 'closesAt', source),
      timeZone: str(item, 'timeZone', source),
      active: bool(item, 'active', source),
      tables: num(item, 'tables', source),
      seats: num(item, 'seats', source),
      slotCapacity: num(item, 'slotCapacity', source),
      today: str(item, 'today', source),
      todayOrders: num(item, 'todayOrders', source),
      todayBookings: num(item, 'todayBookings', source),
    };
  });
}
