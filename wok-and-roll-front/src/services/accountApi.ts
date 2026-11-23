// Auth and "My orders" (API contract §3.11–3.13, #179, #184).
import { parseOrderResponse, requestJson, type OrderResponse, type OrderStatus } from './api';
import type { Role } from './authToken';
import { num, numOrNull, page, pageQuery, record, str, strOrNull, type Page } from './parse';

export interface AuthResponse {
  token: string;
  role: Role;
}

export interface Me {
  id: number;
  email: string;
  fullName: string;
  phone: string | null;
  role: Role;
}

export interface OrderSummary {
  orderNumber: string;
  createdAt: string;
  orderDate: string;
  slotStart: string;
  country: string;
  currency: string;
  scenario: 'PICKUP' | 'DELIVERY';
  status: OrderStatus;
  itemCount: number;
  total: number;
}

function parseAuth(value: unknown): AuthResponse {
  const body = record(value, 'Auth API');
  return { token: str(body, 'token', 'Auth API'), role: str(body, 'role', 'Auth API') as Role };
}

export async function login(email: string, password: string): Promise<AuthResponse> {
  return parseAuth(
    await requestJson('/api/v1/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password }),
    }),
  );
}

export interface RegisterRequest {
  email: string;
  password: string;
  fullName: string;
  phone?: string | null;
}

export async function register(request: RegisterRequest): Promise<AuthResponse> {
  return parseAuth(
    await requestJson(
      '/api/v1/auth/register',
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(request),
      },
      201,
    ),
  );
}

export async function loadMe(): Promise<Me> {
  const body = record(await requestJson('/api/v1/me'), 'Me API');
  return {
    id: num(body, 'id', 'Me API'),
    email: str(body, 'email', 'Me API'),
    fullName: str(body, 'fullName', 'Me API'),
    phone: strOrNull(body, 'phone', 'Me API'),
    role: str(body, 'role', 'Me API') as Role,
  };
}

function parseSummary(value: unknown): OrderSummary {
  const source = 'My orders API';
  const item = record(value, source, 'item');
  return {
    orderNumber: str(item, 'orderNumber', source),
    createdAt: str(item, 'createdAt', source),
    orderDate: str(item, 'orderDate', source),
    slotStart: str(item, 'slotStart', source),
    country: str(item, 'country', source),
    currency: str(item, 'currency', source),
    scenario: str(item, 'scenario', source) as OrderSummary['scenario'],
    status: str(item, 'status', source) as OrderStatus,
    itemCount: numOrNull(item, 'itemCount', source) ?? 0,
    total: num(item, 'total', source),
  };
}

export async function loadMyOrders(pageToken?: string | null): Promise<Page<OrderSummary>> {
  return page(
    await requestJson(`/api/v1/me/orders${pageQuery(10, pageToken)}`),
    'My orders API',
    parseSummary,
  );
}

export async function loadMyOrder(orderNumber: string): Promise<OrderResponse> {
  return parseOrderResponse(
    await requestJson(`/api/v1/me/orders/${encodeURIComponent(orderNumber)}`),
  );
}
