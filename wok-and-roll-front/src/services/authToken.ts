// The login token (API contract §8): localStorage key `wr.auth`, dropped when `exp` has passed.
// The token is never put in a URL; React escapes output and we never use dangerouslySetInnerHTML.

export type Role = 'ADMIN' | 'STAFF' | 'VIEWER' | 'CUSTOMER';

export interface StoredAuth {
  token: string;
  role: Role;
  email: string;
  userId: number;
  /** Seconds since the epoch, from the JWT `exp` claim. */
  expiresAt: number;
}

const STORAGE_KEY = 'wr.auth';
const ROLES: Role[] = ['ADMIN', 'STAFF', 'VIEWER', 'CUSTOMER'];

type Listener = (auth: StoredAuth | null, reason?: 'expired' | 'rejected') => void;
const listeners = new Set<Listener>();

function decodePayload(token: string): Record<string, unknown> | null {
  const part = token.split('.')[1];
  if (!part) return null;
  try {
    const base64 = part.replace(/-/g, '+').replace(/_/g, '/');
    const json = atob(base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '='));
    const payload: unknown = JSON.parse(json);
    return typeof payload === 'object' && payload !== null
      ? (payload as Record<string, unknown>)
      : null;
  } catch {
    return null;
  }
}

/** Reads role, email, user id and expiry from the token. Returns null for a broken token. */
export function parseToken(token: string): StoredAuth | null {
  const payload = decodePayload(token);
  if (!payload) return null;
  const { role, email, sub, exp } = payload;
  if (
    typeof role !== 'string' ||
    !ROLES.includes(role as Role) ||
    typeof exp !== 'number' ||
    typeof sub !== 'string'
  ) {
    return null;
  }
  return {
    token,
    role: role as Role,
    email: typeof email === 'string' ? email : '',
    userId: Number(sub),
    expiresAt: exp,
  };
}

function isExpired(auth: StoredAuth): boolean {
  return auth.expiresAt * 1000 <= Date.now();
}

function notify(auth: StoredAuth | null, reason?: 'expired' | 'rejected') {
  listeners.forEach((listener) => listener(auth, reason));
}

export function readAuth(): StoredAuth | null {
  try {
    const saved = window.localStorage.getItem(STORAGE_KEY);
    if (!saved) return null;
    const auth = parseToken(saved);
    if (!auth) {
      window.localStorage.removeItem(STORAGE_KEY);
      return null;
    }
    if (isExpired(auth)) {
      window.localStorage.removeItem(STORAGE_KEY);
      notify(null, 'expired');
      return null;
    }
    return auth;
  } catch {
    return null;
  }
}

export function saveToken(token: string): StoredAuth {
  const auth = parseToken(token);
  if (!auth) throw new Error('The server returned a token that cannot be read.');
  try {
    window.localStorage.setItem(STORAGE_KEY, token);
  } catch {
    // Storage blocked (private mode): the session still works until the page is reloaded.
  }
  memoryAuth = auth;
  notify(auth);
  return auth;
}

let memoryAuth: StoredAuth | null = null;

/** The current token for a request, or undefined. An expired token is dropped first. */
export function currentToken(): string | undefined {
  const auth = readAuth() ?? (memoryAuth && !isExpired(memoryAuth) ? memoryAuth : null);
  return auth?.token;
}

/** Logout happens in the client: the server keeps no sessions (API contract §3.11). */
export function clearAuth(reason?: 'expired' | 'rejected') {
  memoryAuth = null;
  try {
    window.localStorage.removeItem(STORAGE_KEY);
  } catch {
    // ignore
  }
  notify(null, reason);
}

export function onAuthChange(listener: Listener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export const AUTH_STORAGE_KEY = STORAGE_KEY;
