import { useEffect, useId, useRef, useState } from 'react';
import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom';
import { ROLE_LABELS, useAuth } from '../../context/AuthContext';
import { Icon } from '../common/Icon';
import { accountLinks, visibleLinks } from './Navigation';

/**
 * The session in the header (#179): "Log in" for guests; for a logged-in user a disclosure button
 * with the name and role that opens the role links (#186) and "Log out".
 */
export function AccountMenu() {
  const { auth, me, logout } = useAuth();
  const [open, setOpen] = useState(false);
  const panelId = useId();
  const toggle = useRef<HTMLButtonElement>(null);
  const container = useRef<HTMLDivElement>(null);
  const location = useLocation();
  const navigate = useNavigate();

  useEffect(() => setOpen(false), [location.pathname]);

  useEffect(() => {
    if (!open) return undefined;
    function closeOnOutsideClick(event: MouseEvent) {
      if (!container.current?.contains(event.target as Node)) setOpen(false);
    }
    function closeOnEscape(event: KeyboardEvent) {
      if (event.key !== 'Escape') return;
      setOpen(false);
      toggle.current?.focus();
    }
    // Focus leaving the menu (Tab past the last link) closes it too.
    function closeOnFocusOut(event: FocusEvent) {
      if (!container.current?.contains(event.target as Node)) setOpen(false);
    }
    document.addEventListener('mousedown', closeOnOutsideClick);
    document.addEventListener('keydown', closeOnEscape);
    document.addEventListener('focusin', closeOnFocusOut);
    return () => {
      document.removeEventListener('mousedown', closeOnOutsideClick);
      document.removeEventListener('keydown', closeOnEscape);
      document.removeEventListener('focusin', closeOnFocusOut);
    };
  }, [open]);

  if (!auth) {
    return (
      <Link
        className="account-login"
        to={`/login?next=${encodeURIComponent(location.pathname + location.search)}`}
      >
        Log in
      </Link>
    );
  }

  const name = me?.fullName || auth.email || 'Account';
  const links = visibleLinks(accountLinks, auth.role);

  return (
    <div className="account-menu" ref={container}>
      <button
        ref={toggle}
        type="button"
        className="account-menu__toggle"
        aria-expanded={open}
        aria-controls={panelId}
        onClick={() => setOpen((value) => !value)}
      >
        <span className="account-menu__avatar" aria-hidden="true">
          {name.trim().charAt(0).toUpperCase()}
        </span>
        <span className="account-menu__name">
          <span className="sr-only">Account: </span>
          {name}
        </span>
        <Icon name="chevron" />
      </button>
      <div className="account-menu__panel" id={panelId} hidden={!open}>
        <p className="account-menu__who">
          <strong>{name}</strong>
          <span>{ROLE_LABELS[auth.role]}</span>
        </p>
        <nav aria-label="Account">
          <ul>
            {links.map((link) => (
              <li key={link.to}>
                <NavLink
                  to={link.to}
                  className={({ isActive }) => `account-menu__link${isActive ? ' is-active' : ''}`}
                >
                  {link.label}
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>
        <button
          type="button"
          className="account-menu__link account-menu__logout"
          onClick={() => {
            logout();
            navigate('/');
          }}
        >
          Log out
        </button>
      </div>
    </div>
  );
}
