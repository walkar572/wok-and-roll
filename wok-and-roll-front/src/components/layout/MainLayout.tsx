import { useEffect } from 'react';
import { Outlet, ScrollRestoration, useLocation } from 'react-router-dom';
import { useI18n, type MessageKey } from '../../i18n';
import { Mascot } from '../mascot/Mascot';
import { Footer } from './Footer';
import { Header } from './Header';

const pageTitles: Record<string, MessageKey> = {
  '/': 'titles.home',
  '/menu': 'titles.menu',
  '/cart': 'titles.cart',
  '/checkout': 'titles.checkout',
  '/checkout/success': 'titles.success',
  '/booking': 'titles.booking',
  '/login': 'titles.login',
  '/account': 'titles.account',
};

export function MainLayout() {
  const { pathname } = useLocation();
  const { t, locale } = useI18n();

  useEffect(() => {
    const key =
      pageTitles[pathname] ??
      (pathname.startsWith('/account/orders/') ? 'titles.orderDetails' : 'titles.notFound');
    document.title = `${t(key)} — Wok & Roll`;
  }, [pathname, t, locale]);

  return (
    <div className="app-shell">
      <a className="skip-link" href="#main-content">
        {t('nav.skipToContent')}
      </a>
      <Header />
      <main className="app-main" id="main-content">
        <Outlet />
      </main>
      <Footer />
      <Mascot />
      <ScrollRestoration />
    </div>
  );
}
