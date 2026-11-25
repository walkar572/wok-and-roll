import { useEffect, useState, type FormEvent } from 'react';
import { Link, Navigate, useSearchParams } from 'react-router-dom';
import { isPanelRole, panelHome } from '../../components/admin/panelSections';
import { Button, ErrorMessage, Input } from '../../components/common';
import { LanguageSwitcher } from '../../components/layout/LanguageSwitcher';
import { useAuth } from '../../context/AuthContext';
import { useI18n } from '../../i18n';
import { ApiRequestError } from '../../services/api';
import type { Role } from '../../services/authToken';

const EMAIL = /^[^\s@]+@[^\s@.]+(\.[^\s@.]+)+$/;

// The demo accounts of the panel roles (wok-and-roll-back README, "Demo users"): local runs only.
const DEMO_ACCOUNTS: Array<{ role: Role; email: string; password: string }> = [
  { role: 'ADMIN', email: 'admin@wokandroll.test', password: 'AdminDemo2026' },
  { role: 'STAFF', email: 'staff@wokandroll.test', password: 'StaffDemo2026' },
  { role: 'VIEWER', email: 'viewer@wokandroll.test', password: 'ViewerDemo2026' },
];

/** Only a path of the panel, never another origin or the login itself. */
function safeNext(value: string | null): string | null {
  return value && value.startsWith('/admin') && !value.startsWith('/admin/login') ? value : null;
}

/**
 * The entry of the admin panel: a login of its own, on the panel's dark screen. A logged-in panel
 * user goes straight in; a customer account gets a clear "no access" and can log in with another.
 */
export function AdminLoginPage() {
  const { auth, login, notice, dismissNotice } = useAuth();
  const { t, locale } = useI18n();
  const [params] = useSearchParams();
  const next = safeNext(params.get('next'));
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [errors, setErrors] = useState<{ email?: string; password?: string }>({});
  const [formError, setFormError] = useState<string>();
  const [sending, setSending] = useState(false);

  useEffect(() => {
    document.title = `${t('login.titleLogin')} - ${t('panel.title')} - Wok & Roll`;
  }, [t, locale]);

  if (auth && isPanelRole(auth.role)) {
    return <Navigate to={next ?? panelHome(auth.role)} replace />;
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const found: typeof errors = {};
    const text = email.trim();
    if (!text) found.email = t('login.emailRequired');
    else if (!EMAIL.test(text)) found.email = t('login.emailInvalid');
    if (!password) found.password = t('login.passwordRequired');
    setErrors(found);
    setFormError(undefined);
    if (found.email || found.password) {
      document.getElementById(found.email ? 'panel-login-email' : 'panel-login-password')?.focus();
      return;
    }
    setSending(true);
    try {
      // A panel role is sent in by the redirect above on the next render.
      await login(text, password);
    } catch (error) {
      if (error instanceof ApiRequestError && error.code === 'INVALID_CREDENTIALS') {
        setFormError(t('login.wrongCredentials'));
      } else if (error instanceof ApiRequestError && error.code === 'TOO_MANY_ATTEMPTS') {
        const minutes = Math.max(1, Math.ceil((error.retryAfterSeconds ?? 900) / 60));
        setFormError(t('login.tooManyAttempts', { minutes }));
      } else if (error instanceof ApiRequestError) {
        setFormError(t('login.serverError'));
      } else {
        setFormError(t('common.networkError'));
      }
    } finally {
      setSending(false);
    }
  }

  return (
    <div className="panel-login">
      <div className="panel-login__language">
        <LanguageSwitcher />
      </div>
      <main className="panel-login__card" aria-labelledby="panel-login-title">
        <p className="panel-login__brand">
          <span className="panel-brand panel-brand--dark">
            Wok <span>&amp;</span> Roll
          </span>
          <span className="panel-brand__tag">{t('panel.tag')}</span>
        </p>
        <h1 id="panel-login-title">{t('panel.login.title')}</h1>
        <p className="panel-login__description">{t('panel.login.description')}</p>

        {auth && !isPanelRole(auth.role) && (
          <ErrorMessage variant="info" title={t('noAccess.title')}>
            {t('panel.login.noAccess')}
          </ErrorMessage>
        )}
        {notice && (
          <ErrorMessage variant="info" title={t('login.sessionEnded')}>
            {t('login.sessionNotice')}{' '}
            <button type="button" className="link-button" onClick={dismissNotice}>
              {t('login.dismiss')}
            </button>
          </ErrorMessage>
        )}
        {formError && <ErrorMessage title={t('login.loginFailed')}>{formError}</ErrorMessage>}

        <form className="auth-form" onSubmit={submit} noValidate>
          <Input
            id="panel-login-email"
            label={t('login.email')}
            type="email"
            autoComplete="username"
            inputMode="email"
            value={email}
            maxLength={255}
            error={errors.email}
            onChange={(event) => {
              setEmail(event.target.value);
              setErrors((current) => ({ ...current, email: undefined }));
            }}
          />
          <Input
            id="panel-login-password"
            label={t('login.password')}
            type="password"
            autoComplete="current-password"
            value={password}
            maxLength={72}
            error={errors.password}
            onChange={(event) => {
              setPassword(event.target.value);
              setErrors((current) => ({ ...current, password: undefined }));
            }}
          />
          <Button type="submit" size="large" loading={sending}>
            {t('login.submitLogin')}
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
                    setEmail(account.email);
                    setPassword(account.password);
                    setErrors({});
                  }}
                >
                  {t(`roles.${account.role}`)}: {account.email}
                </button>
              </li>
            ))}
          </ul>
        </details>

        <Link className="panel-login__back" to="/">
          <span aria-hidden="true">←</span> {t('panel.login.back')}
        </Link>
      </main>
    </div>
  );
}
