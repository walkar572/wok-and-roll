import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type PropsWithChildren,
} from 'react';
import {
  loadMe,
  login as loginRequest,
  register as registerRequest,
  type Me,
  type RegisterRequest,
} from '../services/accountApi';
import {
  AUTH_STORAGE_KEY,
  clearAuth,
  onAuthChange,
  readAuth,
  saveToken,
  type Role,
  type StoredAuth,
} from '../services/authToken';

interface AuthContextValue {
  auth: StoredAuth | null;
  me: Me | null;
  /** A message after the session ended by itself (expired or refused token). */
  notice: string | null;
  dismissNotice: () => void;
  login: (email: string, password: string) => Promise<StoredAuth>;
  register: (request: RegisterRequest) => Promise<StoredAuth>;
  logout: () => void;
  hasRole: (...roles: Role[]) => boolean;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export const ROLE_LABELS: Record<Role, string> = {
  ADMIN: 'Admin',
  STAFF: 'Staff',
  VIEWER: 'Viewer',
  CUSTOMER: 'Customer',
};

export function AuthProvider({ children }: PropsWithChildren) {
  const [auth, setAuth] = useState<StoredAuth | null>(() => readAuth());
  const [me, setMe] = useState<Me | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(
    () =>
      onAuthChange((next, reason) => {
        setAuth(next);
        if (!next) setMe(null);
        if (reason === 'expired' || reason === 'rejected') {
          setNotice('Your session has ended. Please log in again.');
        }
      }),
    [],
  );

  // Another tab logged in or out.
  useEffect(() => {
    function sync(event: StorageEvent) {
      if (event.key === AUTH_STORAGE_KEY || event.key === null) setAuth(readAuth());
    }
    window.addEventListener('storage', sync);
    return () => window.removeEventListener('storage', sync);
  }, []);

  // Log out at the moment the token expires, not only on the next request.
  useEffect(() => {
    if (!auth) return undefined;
    const delay = auth.expiresAt * 1000 - Date.now();
    const timer = window.setTimeout(
      () => clearAuth('expired'),
      Math.max(0, Math.min(delay, 2 ** 31 - 1)),
    );
    return () => window.clearTimeout(timer);
  }, [auth]);

  useEffect(() => {
    let active = true;
    if (!auth) return undefined;
    loadMe()
      .then((result) => {
        if (active) setMe(result);
      })
      .catch(() => {
        // A refused token is cleared by the API client; other errors keep the session.
      });
    return () => {
      active = false;
    };
  }, [auth]);

  const login = useCallback(async (email: string, password: string) => {
    const response = await loginRequest(email.trim().toLowerCase(), password);
    setNotice(null);
    return saveToken(response.token);
  }, []);

  const register = useCallback(async (request: RegisterRequest) => {
    const response = await registerRequest(request);
    setNotice(null);
    return saveToken(response.token);
  }, []);

  const logout = useCallback(() => {
    clearAuth();
    setNotice(null);
  }, []);

  const hasRole = useCallback(
    (...roles: Role[]) => Boolean(auth && roles.includes(auth.role)),
    [auth],
  );

  const value = useMemo(
    () => ({
      auth,
      me,
      notice,
      dismissNotice: () => setNotice(null),
      login,
      register,
      logout,
      hasRole,
    }),
    [auth, me, notice, login, register, logout, hasRole],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used within an AuthProvider');
  return context;
}
