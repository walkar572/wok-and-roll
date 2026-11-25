// Small runtime checks for API responses: a broken answer fails loudly instead of showing wrong
// numbers. Used by the API modules of Sprint 4 (account, booking, reports, admin, staff).

export class ResponseShapeError extends Error {
  constructor(source: string, field: string) {
    super(`${source} returned an invalid response (${field}).`);
    this.name = 'ResponseShapeError';
  }
}

export type Json = Record<string, unknown>;

export function record(value: unknown, source: string, field = 'body'): Json {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) {
    throw new ResponseShapeError(source, field);
  }
  return value as Json;
}

export function list(value: unknown, source: string, field = 'items'): unknown[] {
  if (!Array.isArray(value)) throw new ResponseShapeError(source, field);
  return value;
}

export function str(obj: Json, field: string, source: string): string {
  const value = obj[field];
  if (typeof value !== 'string') throw new ResponseShapeError(source, field);
  return value;
}

export function strOrNull(obj: Json, field: string, source: string): string | null {
  const value = obj[field];
  if (value === null || value === undefined) return null;
  if (typeof value !== 'string') throw new ResponseShapeError(source, field);
  return value;
}

export function num(obj: Json, field: string, source: string): number {
  const value = obj[field];
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    throw new ResponseShapeError(source, field);
  }
  return value;
}

export function numOrNull(obj: Json, field: string, source: string): number | null {
  const value = obj[field];
  if (value === null || value === undefined) return null;
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    throw new ResponseShapeError(source, field);
  }
  return value;
}

export function bool(obj: Json, field: string, source: string): boolean {
  const value = obj[field];
  if (typeof value !== 'boolean') throw new ResponseShapeError(source, field);
  return value;
}

export function boolOrNull(obj: Json, field: string, source: string): boolean | null {
  const value = obj[field];
  if (value === null || value === undefined) return null;
  if (typeof value !== 'boolean') throw new ResponseShapeError(source, field);
  return value;
}

export interface Page<T> {
  items: T[];
  nextPageToken: string | null;
}

export function page<T>(value: unknown, source: string, parseItem: (item: unknown) => T): Page<T> {
  const body = record(value, source);
  return {
    items: list(body.items, source).map(parseItem),
    nextPageToken: strOrNull(body, 'nextPageToken', source),
  };
}

export function pageQuery(pageSize?: number, pageToken?: string | null): string {
  const query = new URLSearchParams();
  if (pageSize) query.set('pageSize', String(pageSize));
  if (pageToken) query.set('pageToken', pageToken);
  const text = query.toString();
  return text ? `?${text}` : '';
}

/** One idempotency key per attempt (API contract §1); the same key is sent again on a retry. */
export function createIdempotencyKey(): string {
  if (typeof crypto.randomUUID === 'function') return crypto.randomUUID();

  const bytes = crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}
