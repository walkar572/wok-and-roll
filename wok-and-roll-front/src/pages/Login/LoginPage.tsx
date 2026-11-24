import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { Button, ErrorMessage, Input } from '../../components/common';
import { useAuth } from '../../context/AuthContext';
import { t as translate, useI18n } from '../../i18n';
import { ApiRequestError } from '../../services/api';
import { apiFieldMessage } from '../../services/apiMessages';
import type { Role } from '../../services/authToken';

type Mode = 'login' | 'register';
type Field = 'email' | 'password' | 'fullName' | 'phone';

const EMAIL = /^[^\s@]+@[^\s@.]+(\.[^\s@.]+)+$/;

const DEMO_ACCOUNTS: Array<{ role: Role; email: string; password: string }> = [
  { role: 'ADMIN', email: 'admin@wokandroll.test', password: 'AdminDemo2026' },
  { role: 'STAFF', email: 'staff@wokandroll.test', password: 'StaffDemo2026' },
  { role: 'VIEWER', email: 'viewer@wokandroll.test', password: 'ViewerDemo2026' },
  { role: 'CUSTOMER', email: 'customer@wokandroll.test', password: 'CustomerDemo2026' },
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
  if (!email) errors.email = translate('login.emailRequired');
  else if (email.length > 255 || !EMAIL.test(email)) errors.email = translate('login.emailInvalid');
  if (!values.password) errors.password = translate('login.passwordRequired');
  else if (mode === 'register' && (values.password.length < 8 || values.password.length > 72)) {
    errors.password = translate('login.passwordLength');
  }
  if (mode === 'register') {
    const name = values.fullName.trim();
    if (!name) errors.fullName = translate('login.nameRequired');
    else if (name.length > 150) errors.fullName = translate('login.nameLong');
    const phone = values.phone.replace(/\s/g, '');
    if (phone && !/^\+\d{8,15}$/.test(phone)) errors.phone = translate('login.phoneInvalid');
  }
  return errors;
}

/** Log in and register (#179, API contract §3.11). */
export function LoginPage() {
  const { login, register, notice, dismissNotice } = useAuth();
  const { t } = useI18n();
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
          setFormError(t('login.wrongCredentials'));
        } else if (error.code === 'TOO_MANY_ATTEMPTS') {
          const minutes = Math.max(1, Math.ceil((error.retryAfterSeconds ?? 900) / 60));
          setFormError(t('login.tooManyAttempts', { minutes }));
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
                  ? t('login.emailTaken')
                  : apiFieldMessage(field, message, error.code);
            }
          }
          setErrors(fieldErrors);
          setFocusError(true);
          if (Object.keys(fieldErrors).length === 0) setFormError(t('login.serverError'));
        } else {
          setFormError(t('login.serverError'));
        }
      } else {
        setFormError(t('common.networkError'));
      }
    } finally {
      setSending(false);
    }
  }

  const title = mode === 'login' ? t('login.titleLogin') : t('login.titleRegister');

  return (
    <section className="auth-page" aria-labelledby="auth-title">
      <p className="eyebrow">{t('login.eyebrow')}</p>
      <h1 id="auth-title">{title}</h1>
      <p className="page-description">
        {mode === 'login' ? t('login.descriptionLogin') : t('login.descriptionRegister')}
      </p>

      <div className="auth-card">
        <div className="segmented segmented--full" role="group" aria-label={t('login.modeLabel')}>
          <button
            type="button"
            aria-pressed={mode === 'login'}
            className="segmented__tab"
            onClick={() => switchMode('login')}
          >
            {t('login.tabLogin')}
          </button>
          <button
            type="button"
            aria-pressed={mode === 'register'}
            className="segmented__tab"
            onClick={() => switchMode('register')}
          >
            {t('login.tabRegister')}
          </button>
        </div>

        {notice && (
          <ErrorMessage variant="info" title={t('login.sessionEnded')}>
            {t('login.sessionNotice')}{' '}
            <button type="button" className="link-button" onClick={dismissNotice}>
              {t('login.dismiss')}
            </button>
          </ErrorMessage>
        )}
        {formError && (
          <ErrorMessage
            title={mode === 'login' ? t('login.loginFailed') : t('login.registerFailed')}
          >
            {formError}
          </ErrorMessage>
        )}

        <form ref={formRef} className="auth-form" onSubmit={submit} noValidate>
          {mode === 'register' && (
            <Input
              label={t('login.fullName')}
              autoComplete="name"
              value={values.fullName}
              maxLength={150}
              error={errors.fullName}
              onChange={(event) => update('fullName', event.target.value)}
            />
          )}
          <Input
            label={t('login.email')}
            type="email"
            autoComplete="email"
            inputMode="email"
            value={values.email}
            maxLength={255}
            error={errors.email}
            onChange={(event) => update('email', event.target.value)}
          />
          <Input
            label={t('login.password')}
            type="password"
            autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
            value={values.password}
            maxLength={72}
            hint={mode === 'register' ? t('login.passwordHint') : undefined}
            error={errors.password}
            onChange={(event) => update('password', event.target.value)}
          />
          {mode === 'register' && (
            <Input
              label={t('login.phoneOptional')}
              type="tel"
              autoComplete="tel"
              value={values.phone}
              hint={t('fields.phoneHint')}
              error={errors.phone}
              onChange={(event) => update('phone', event.target.value)}
            />
          )}
          <Button type="submit" size="large" loading={sending}>
            {mode === 'login' ? t('login.submitLogin') : t('login.submitRegister')}
          </Button>
        </form>

        <details className="demo-accounts">
          <summary>{t('login.demoTitle')}</summary>
          <p>{t('login.demoText')}</p>
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
                  {t(`roles.${account.role}`)}: {account.email}
                </button>
              </li>
            ))}
          </ul>
        </details>
      </div>
      <p className="auth-page__back">
        <Link className="text-link" to="/menu">
          {t('login.guest')} <span aria-hidden="true">→</span>
        </Link>
      </p>
    </section>
  );
}
