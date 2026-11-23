import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { Button, ErrorMessage, Input } from '../../components/common';
import { useAuth } from '../../context/AuthContext';
import { ApiRequestError } from '../../services/api';
import type { Role } from '../../services/authToken';

type Mode = 'login' | 'register';
type Field = 'email' | 'password' | 'fullName' | 'phone';

const EMAIL = /^[^\s@]+@[^\s@.]+(\.[^\s@.]+)+$/;

const DEMO_ACCOUNTS: Array<{ role: string; email: string; password: string }> = [
  { role: 'Admin', email: 'admin@wokandroll.test', password: 'AdminDemo2026' },
  { role: 'Staff', email: 'staff@wokandroll.test', password: 'StaffDemo2026' },
  { role: 'Viewer', email: 'viewer@wokandroll.test', password: 'ViewerDemo2026' },
  { role: 'Customer', email: 'customer@wokandroll.test', password: 'CustomerDemo2026' },
];

/** Only a path inside this site, never another origin ("//evil.test" is refused). */
function safeNext(value: string | null): string | null {
  return value && value.startsWith('/') && !value.startsWith('//') && !value.startsWith('/login')
    ? value
    : null;
}

function homeFor(role: Role): string {
  if (role === 'ADMIN' || role === 'VIEWER') return '/reports';
  if (role === 'STAFF') return '/staff';
  return '/account';
}

function validate(mode: Mode, values: Record<Field, string>) {
  const errors: Partial<Record<Field, string>> = {};
  const email = values.email.trim();
  if (!email) errors.email = 'Enter your email.';
  else if (email.length > 255 || !EMAIL.test(email)) errors.email = 'Enter a valid email address.';
  if (!values.password) errors.password = 'Enter your password.';
  else if (mode === 'register' && (values.password.length < 8 || values.password.length > 72)) {
    errors.password = 'Use 8 to 72 characters.';
  }
  if (mode === 'register') {
    const name = values.fullName.trim();
    if (!name) errors.fullName = 'Enter your name.';
    else if (name.length > 150) errors.fullName = 'Use 150 characters or fewer.';
    const phone = values.phone.replace(/\s/g, '');
    if (phone && !/^\+\d{8,15}$/.test(phone)) {
      errors.phone = 'Enter a phone number with + and 8 to 15 digits, or leave it empty.';
    }
  }
  return errors;
}

/** Log in and register (#179, API contract §3.11). */
export function LoginPage() {
  const { login, register, notice, dismissNotice } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const next = safeNext(params.get('next'));
  const [mode, setMode] = useState<Mode>('login');
  const [values, setValues] = useState<Record<Field, string>>({
    email: '',
    password: '',
    fullName: '',
    phone: '',
  });
  const [errors, setErrors] = useState<Partial<Record<Field, string>>>({});
  const [formError, setFormError] = useState<string>();
  const [sending, setSending] = useState(false);
  const formRef = useRef<HTMLFormElement>(null);
  const [focusError, setFocusError] = useState(false);

  useEffect(() => {
    if (!focusError) return;
    formRef.current
      ?.querySelector<HTMLElement>('[aria-invalid="true"], [data-invalid="true"]')
      ?.focus();
    setFocusError(false);
  }, [focusError, errors]);

  function update(field: Field, value: string) {
    setValues((current) => ({ ...current, [field]: value }));
    setErrors((current) => ({ ...current, [field]: undefined }));
    setFormError(undefined);
  }

  function switchMode(nextMode: Mode) {
    setMode(nextMode);
    setErrors({});
    setFormError(undefined);
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const found = validate(mode, values);
    setErrors(found);
    setFormError(undefined);
    if (Object.keys(found).length > 0) {
      setFocusError(true);
      return;
    }
    setSending(true);
    try {
      const auth =
        mode === 'login'
          ? await login(values.email, values.password)
          : await register({
              email: values.email.trim().toLowerCase(),
              password: values.password,
              fullName: values.fullName.trim(),
              phone: values.phone.replace(/\s/g, '') || null,
            });
      navigate(next ?? homeFor(auth.role), { replace: true });
    } catch (error) {
      if (error instanceof ApiRequestError) {
        if (error.code === 'INVALID_CREDENTIALS') {
          setFormError('The email or the password is wrong.');
        } else if (error.code === 'TOO_MANY_ATTEMPTS') {
          const minutes = Math.max(1, Math.ceil((error.retryAfterSeconds ?? 900) / 60));
          setFormError(
            `Too many attempts. Try again in ${minutes} ${minutes === 1 ? 'minute' : 'minutes'}.`,
          );
        } else if (Object.keys(error.fields).length > 0) {
          const fieldErrors: Partial<Record<Field, string>> = {};
          for (const [field, message] of Object.entries(error.fields)) {
            if (
              field === 'email' ||
              field === 'password' ||
              field === 'fullName' ||
              field === 'phone'
            ) {
              fieldErrors[field] =
                error.code === 'EMAIL_TAKEN'
                  ? 'An account with this email already exists. Log in instead.'
                  : message;
            }
          }
          setErrors(fieldErrors);
          setFocusError(true);
          if (Object.keys(fieldErrors).length === 0) setFormError(error.message);
        } else {
          setFormError(
            error.status >= 500 ? 'Something went wrong. Please try again.' : error.message,
          );
        }
      } else {
        setFormError('We could not reach the server. Check your connection and try again.');
      }
    } finally {
      setSending(false);
    }
  }

  const title = mode === 'login' ? 'Log in' : 'Create an account';

  return (
    <section className="auth-page" aria-labelledby="auth-title">
      <p className="eyebrow">Your account</p>
      <h1 id="auth-title">{title}</h1>
      <p className="page-description">
        {mode === 'login'
          ? 'Log in to see your orders. Staff and admins get their tools here too.'
          : 'An account keeps your order history in one place.'}
      </p>

      <div className="auth-card">
        <div className="segmented segmented--full" role="group" aria-label="Log in or register">
          <button
            type="button"
            aria-pressed={mode === 'login'}
            className="segmented__tab"
            onClick={() => switchMode('login')}
          >
            Log in
          </button>
          <button
            type="button"
            aria-pressed={mode === 'register'}
            className="segmented__tab"
            onClick={() => switchMode('register')}
          >
            Register
          </button>
        </div>

        {notice && (
          <ErrorMessage variant="info" title="Session ended">
            {notice}{' '}
            <button type="button" className="link-button" onClick={dismissNotice}>
              Dismiss
            </button>
          </ErrorMessage>
        )}
        {formError && <ErrorMessage title={`${title} failed`}>{formError}</ErrorMessage>}

        <form ref={formRef} className="auth-form" onSubmit={submit} noValidate>
          {mode === 'register' && (
            <Input
              label="Full name"
              autoComplete="name"
              value={values.fullName}
              maxLength={150}
              error={errors.fullName}
              onChange={(event) => update('fullName', event.target.value)}
            />
          )}
          <Input
            label="Email"
            type="email"
            autoComplete="email"
            inputMode="email"
            value={values.email}
            maxLength={255}
            error={errors.email}
            onChange={(event) => update('email', event.target.value)}
          />
          <Input
            label="Password"
            type="password"
            autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
            value={values.password}
            maxLength={72}
            hint={mode === 'register' ? '8 to 72 characters.' : undefined}
            error={errors.password}
            onChange={(event) => update('password', event.target.value)}
          />
          {mode === 'register' && (
            <Input
              label="Phone (optional)"
              type="tel"
              autoComplete="tel"
              value={values.phone}
              hint="International format, for example +998901234567."
              error={errors.phone}
              onChange={(event) => update('phone', event.target.value)}
            />
          )}
          <Button type="submit" size="large" loading={sending}>
            {mode === 'login' ? 'Log in' : 'Create account'}
          </Button>
        </form>

        <details className="demo-accounts">
          <summary>Demo accounts</summary>
          <p>For the demo and testing only. Choose one to fill the form.</p>
          <ul>
            {DEMO_ACCOUNTS.map((account) => (
              <li key={account.email}>
                <button
                  type="button"
                  className="link-button"
                  onClick={() => {
                    switchMode('login');
                    setValues((current) => ({
                      ...current,
                      email: account.email,
                      password: account.password,
                    }));
                  }}
                >
                  {account.role}: {account.email}
                </button>
              </li>
            ))}
          </ul>
        </details>
      </div>
      <p className="auth-page__back">
        <Link className="text-link" to="/menu">
          Continue as a guest <span aria-hidden="true">→</span>
        </Link>
      </p>
    </section>
  );
}
