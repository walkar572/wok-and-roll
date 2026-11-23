import { useEffect } from 'react';
import { Outlet, ScrollRestoration, useLocation } from 'react-router-dom';
import { Footer } from './Footer';
import { Header } from './Header';

const pageTitles: Record<string, string> = {
  '/': 'Home',
  '/menu': 'Menu',
  '/cart': 'Cart',
  '/checkout': 'Checkout',
  '/checkout/success': 'Order confirmed',
  '/reports': 'Reports',
};

export function MainLayout() {
  const { pathname } = useLocation();

  useEffect(() => {
    const pageTitle = pageTitles[pathname] ?? 'Page not found';
    document.title = `${pageTitle} - Wok & Roll`;
  }, [pathname]);

  return (
    <div className="app-shell">
      <a className="skip-link" href="#main-content">Skip to content</a>
      <Header />
      <main className="app-main" id="main-content">
        <Outlet />
      </main>
      <Footer />
      <ScrollRestoration />
    </div>
  );
}
