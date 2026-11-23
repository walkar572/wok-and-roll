import { NavLink } from 'react-router-dom';

// The guest header shows Home and Menu only (approved design).
// Reports comes back for admins with the role-based menu (#186).
const links = [
  { to: '/', label: 'Home', end: true },
  { to: '/menu', label: 'Menu' },
];

export function Navigation({ onNavigate }: { onNavigate?: () => void }) {
  return (
    <nav className="main-nav" aria-label="Main navigation">
      {links.map(({ to, label, end }) => (
        <NavLink
          key={to}
          to={to}
          end={end}
          className={({ isActive }) => `main-nav__link${isActive ? ' is-active' : ''}`}
          onClick={onNavigate}
        >
          {label}
        </NavLink>
      ))}
    </nav>
  );
}
