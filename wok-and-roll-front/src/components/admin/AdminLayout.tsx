import { useEffect, useRef, useState, type ReactNode } from 'react';
import { Link, Navigate, NavLink, Outlet, ScrollRestoration, useLocation } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { useI18n } from '../../i18n';
import type { Role } from '../../services/authToken';
import { Icon } from '../common/Icon';
import { LanguageSwitcher } from '../layout/LanguageSwitcher';
import { isPanelRole, panelHome, sectionOf, sectionsFor } from './panelSections';

/**
 * The admin panel (API contract v1.11): its own screen with a dark side menu, without the header
 * and footer of the shop. Only ADMIN, STAFF and VIEWER get in, each to the sections of the role;
 * the server checks the role again on every request.
 */
export function AdminLayout() {
  const { auth, me, logout } = useAuth();
  const { t, locale } = useI18n();
  const location = useLocation();
  const [menuOpen, setMenuOpen] = useState(false);
  const menuButton = useRef<HTMLButtonElement>(null);
  // Log out goes to the plain login; an expired session comes back to the same page after it.
  const loggedOut = useRef(false);
  const section = sectionOf(location.pathname);

  useEffect(() => setMenuOpen(false), [location.pathname]);

  useEffect(() => {
    const page = section ? `${t(section.label)} — ` : '';
    document.title = `${page}${t('panel.title')} — Wok & Roll`;
  }, [section, t, locale]);

  // On a narrow screen the menu is a drawer: Escape closes it and gives focus back to its button.
  useEffect(() => {
    if (!menuOpen) return undefined;
    function closeOnEscape(event: KeyboardEvent) {
      if (event.key !== 'Escape') return;
      setMenuOpen(false);
      menuButton.current?.focus();
    }
    document.addEventListener('keydown', closeOnEscape);
    return () => document.removeEventListener('keydown', closeOnEscape);
  }, [menuOpen]);

  if (!auth) {
    const next = encodeURIComponent(location.pathname + location.search);
    return (
      <Navigate to={loggedOut.current ? '/admin/login' : `/admin/login?next=${next}`} replace />
    );
  }
  if (!isPanelRole(auth.role)) return <Navigate to="/admin/login" replace />;

  const name = me?.fullName ?? auth.email;

  return (
    <div className="panel">
      <a className="skip-link" href="#panel-main">
        {t('nav.skipToContent')}
      </a>
      <aside id="panel-sidebar" className={`panel-sidebar${menuOpen ? ' is-open' : ''}`}>
        <div className="panel-sidebar__brand">
          <Link className="panel-brand" to={panelHome(auth.role)}>
            Wok <span>&amp;</span> Roll
          </Link>
          <span className="panel-brand__tag">{t('panel.tag')}</span>
        </div>
        <nav className="panel-nav" aria-label={t('panel.nav')}>
          <ul>
            {sectionsFor(auth.role).map((item) => (
              <li key={item.path}>
                <NavLink
                  className="panel-nav__link"
                  to={item.path}
                  // the overview only on its own path; "/admin/orders/WR-…" keeps Orders marked
                  end={item.path === '/admin'}
                >
                  <Icon name={item.icon} />
                  <span>{t(item.label)}</span>
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>
        <div className="panel-sidebar__foot">
          <Link className="panel-nav__link" to="/">
            <Icon name="external" />
            <span>{t('panel.toSite')}</span>
          </Link>
        </div>
      </aside>
      {menuOpen && (
        <button
          type="button"
          className="panel-backdrop"
          aria-hidden="true"
          tabIndex={-1}
          onClick={() => setMenuOpen(false)}
        />
      )}

      <div className="panel-body">
        <header className="panel-topbar">
          <button
            ref={menuButton}
            type="button"
            className="panel-topbar__menu"
            aria-expanded={menuOpen}
            aria-controls="panel-sidebar"
            onClick={() => setMenuOpen((open) => !open)}
          >
            <Icon name={menuOpen ? 'close' : 'menu'} />
            <span className="sr-only">{menuOpen ? t('panel.closeMenu') : t('panel.openMenu')}</span>
          </button>
          <span className="panel-topbar__brand" aria-hidden="true">
            Wok <span>&amp;</span> Roll
          </span>
          <div className="panel-topbar__tools">
            <LanguageSwitcher />
            <div className="panel-user">
              <span className="panel-user__avatar" aria-hidden="true">
                {name.trim().charAt(0).toUpperCase()}
              </span>
              <span className="panel-user__text">
                <strong>{name}</strong>
                <span>{t(`roles.${auth.role}`)}</span>
              </span>
            </div>
            <button
              type="button"
              className="panel-logout"
              onClick={() => {
                loggedOut.current = true;
                logout();
              }}
            >
              <Icon name="logout" />
              <span className="panel-logout__text">{t('panel.logOut')}</span>
            </button>
          </div>
        </header>
        <main id="panel-main" className="panel-main" tabIndex={-1}>
          <Outlet />
        </main>
      </div>
      <ScrollRestoration />
    </div>
  );
}

/** A section of the panel for some roles only; another role goes to its own first section. */
export function PanelSectionGuard({ roles, children }: { roles: Role[]; children: ReactNode }) {
  const { auth } = useAuth();
  if (auth && !roles.includes(auth.role)) return <Navigate to={panelHome(auth.role)} replace />;
  return <>{children}</>;
}
