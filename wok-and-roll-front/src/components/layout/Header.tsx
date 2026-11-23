import { useEffect, useRef, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { Icon } from '../common/Icon';
import { useCountry } from '../../context/CountryContext';
import { useCart } from '../../context/CartContext';
import { Navigation } from './Navigation';

export function Header() {
  const [menuOpen, setMenuOpen] = useState(false);
  const {
    country,
    countries,
    currentCountry,
    isLoadingCountries,
    countriesError,
    setCountry,
  } = useCountry();
  const menuToggle = useRef<HTMLButtonElement>(null);
  const location = useLocation();
  const { itemCount } = useCart();

  useEffect(() => {
    setMenuOpen(false);
  }, [location.pathname]);

  function closeMenu() {
    setMenuOpen(false);
    menuToggle.current?.focus();
  }

  return (
    <header
      className="site-header figma-header"
      onKeyDown={(event) => {
        if (event.key === 'Escape' && menuOpen) closeMenu();
      }}
    >
      <div className="site-header__inner">
        <div className="desktop-nav">
          <Navigation />
        </div>
        <Link className="brand" to="/" aria-label="Wok & Roll home">Wok &amp; Roll</Link>
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
                <option key={item.code} value={item.code}>{item.code}</option>
              ))}
            </select>
            <Icon name="chevron" />
            {isLoadingCountries ? (
              <span className="country-control__status" role="status">Loading countries</span>
            ) : countriesError ? (
              <span className="country-control__status" role="status" title={countriesError}>
                {import.meta.env.DEV ? 'Sample data' : 'Country data unavailable'}
              </span>
            ) : null}
          </label>
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
        <Navigation onNavigate={() => setMenuOpen(false)} />
        <p>{currentCountry.name}</p>
      </div>
    </header>
  );
}
