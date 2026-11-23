import { NavLink } from 'react-router-dom';
import type { Role } from '../../services/authToken';

export interface NavItem {
  to: string;
  label: string;
  end?: boolean;
  /** Shown only to these roles; undefined = everybody, including guests. */
  roles?: Role[];
  /** Shown to every logged-in user. */
  loggedIn?: boolean;
}

// Guests see Home, Menu and Book a table (approved design). The role links (#186) live in the
// account menu on wide screens and in the same list on small screens.
export const mainLinks: NavItem[] = [
  { to: '/', label: 'Home', end: true },
  { to: '/menu', label: 'Menu' },
  { to: '/booking', label: 'Book a table' },
];

export const accountLinks: NavItem[] = [
  { to: '/account', label: 'My orders', loggedIn: true },
  { to: '/staff', label: 'Café orders', roles: ['STAFF', 'ADMIN'] },
  { to: '/reports', label: 'Reports', roles: ['ADMIN', 'VIEWER'] },
  { to: '/admin', label: 'Menu admin', roles: ['ADMIN'] },
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
  label = 'Main navigation',
  onNavigate,
}: {
  links?: NavItem[];
  label?: string;
  onNavigate?: () => void;
}) {
  return (
    <nav className="main-nav" aria-label={label}>
      {links.map(({ to, label: text, end }) => (
        <NavLink
          key={to}
          to={to}
          end={end}
          className={({ isActive }) => `main-nav__link${isActive ? ' is-active' : ''}`}
          onClick={onNavigate}
        >
          {text}
        </NavLink>
      ))}
    </nav>
  );
}
