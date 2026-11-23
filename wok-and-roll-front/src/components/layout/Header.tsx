import { useEffect, useRef, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Icon } from '../common/Icon';
import { useAuth } from '../../context/AuthContext';
import { useCountry } from '../../context/CountryContext';
import { useCart } from '../../context/CartContext';
import { AccountMenu } from './AccountMenu';
import { accountLinks, mainLinks, Navigation, visibleLinks } from './Navigation';

export function Header() {
  const [menuOpen, setMenuOpen] = useState(false);
  const { country, countries, currentCountry, isLoadingCountries, countriesError, setCountry } =
    useCountry();
  const menuToggle = useRef<HTMLButtonElement>(null);
  const location = useLocation();
  const { itemCount } = useCart();
  const { auth, logout } = useAuth();
  const navigate = useNavigate();
  const mobileLinks = [...mainLinks, ...visibleLinks(accountLinks, auth?.role)];

  useEffect(() => {
    setMenuOpen(false);
  }, [location.pathname]);

  // Escape closes the mobile menu and gives the focus back to its button.
  useEffect(() => {
    if (!menuOpen) return undefined;
    function closeOnEscape(event: KeyboardEvent) {
      if (event.key !== 'Escape') return;
      setMenuOpen(false);
      menuToggle.current?.focus();
    }
    document.addEventListener('keydown', closeOnEscape);
    return () => document.removeEventListener('keydown', closeOnEscape);
  }, [menuOpen]);

  return (
    <header className="site-header figma-header">
      <div className="site-header__inner">
        <div className="desktop-nav">
          <Navigation />
        </div>
        <Link className="brand" to="/" aria-label="Wok & Roll home">
          Wok &amp; Roll
        </Link>
        <div className="header-tools">
          <label className="country-control">
            <span className="sr-only">Country</span>
            <select
              aria-label={`Country, current selection: ${currentCountry.name}`}
              value={country}
              onChange={(event) => {
                const selectedCountry = countries.find(({ code }) => code === event.target.value);
                if (selectedCountry) setCountry(selectedCountry.code);
              }}
            >
              {countries.map((item) => (
                <option key={item.code} value={item.code}>
                  {item.code}
                </option>
              ))}
            </select>
            <Icon name="chevron" />
            {isLoadingCountries ? (
              <span className="country-control__status" role="status">
                Loading countries
              </span>
            ) : countriesError ? (
              <span className="country-control__status" role="status" title={countriesError}>
                {import.meta.env.DEV ? 'Sample data' : 'Country data unavailable'}
              </span>
            ) : null}
          </label>
          <div className="header-account">
            <AccountMenu />
          </div>
          <Link
            className="header-cart"
            to="/cart"
            aria-label={`Cart, ${itemCount} ${itemCount === 1 ? 'item' : 'items'}`}
          >
            <Icon name="cart" />
            <span>{itemCount}</span>
          </Link>
          <button
            ref={menuToggle}
            className="menu-toggle"
            type="button"
            aria-label={menuOpen ? 'Close menu' : 'Open menu'}
            aria-expanded={menuOpen}
            aria-controls="mobile-navigation"
            onClick={() => setMenuOpen((open) => !open)}
          >
            <Icon name={menuOpen ? 'close' : 'menu'} />
          </button>
        </div>
      </div>
      <div className="mobile-navigation" id="mobile-navigation" hidden={!menuOpen}>
        <Navigation links={mobileLinks} onNavigate={() => setMenuOpen(false)} />
        <div className="mobile-navigation__session">
          {auth ? (
            <button
              type="button"
              className="main-nav__link"
              onClick={() => {
                logout();
                setMenuOpen(false);
                navigate('/');
              }}
            >
              Log out ({auth.email})
            </button>
          ) : (
            <Link
              className="main-nav__link"
              to={`/login?next=${encodeURIComponent(location.pathname)}`}
              onClick={() => setMenuOpen(false)}
            >
              Log in
            </Link>
          )}
        </div>
        <p>{currentCountry.name}</p>
      </div>
    </header>
  );
}
