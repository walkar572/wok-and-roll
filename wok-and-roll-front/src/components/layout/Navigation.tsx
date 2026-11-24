import { NavLink } from 'react-router-dom';
import { useI18n, type MessageKey } from '../../i18n';
import type { Role } from '../../services/authToken';

export interface NavItem {
  to: string;
  label: MessageKey;
  end?: boolean;
  /** Shown only to these roles; undefined = everybody, including guests. */
  roles?: Role[];
  /** Shown to every logged-in user. */
  loggedIn?: boolean;
}

// Guests see Home, Menu and Book a table (approved design). The role links (#186) live in the
// account menu on wide screens and in the same list on small screens.
export const mainLinks: NavItem[] = [
  { to: '/', label: 'nav.home', end: true },
  { to: '/menu', label: 'nav.menu' },
  { to: '/booking', label: 'nav.booking' },
];

export const accountLinks: NavItem[] = [
  { to: '/account', label: 'nav.myOrders', loggedIn: true },
  { to: '/staff', label: 'nav.staff', roles: ['STAFF', 'ADMIN'] },
  { to: '/reports', label: 'nav.reports', roles: ['ADMIN', 'VIEWER'] },
  { to: '/admin', label: 'nav.admin', roles: ['ADMIN'] },
];

export function visibleLinks(links: NavItem[], role: Role | undefined): NavItem[] {
  return links.filter((link) => {
    if (link.roles) return role !== undefined && link.roles.includes(role);
    if (link.loggedIn) return role !== undefined;
    return true;
  });
}

export function Navigation({
  links = mainLinks,
  onNavigate,
}: {
  links?: NavItem[];
  onNavigate?: () => void;
}) {
  const { t } = useI18n();
  return (
    <nav className="main-nav" aria-label={t('nav.main')}>
      {links.map(({ to, label, end }) => (
        <NavLink
          key={to}
          to={to}
          end={end}
          className={({ isActive }) => `main-nav__link${isActive ? ' is-active' : ''}`}
          onClick={onNavigate}
        >
          {t(label)}
        </NavLink>
      ))}
    </nav>
  );
}
