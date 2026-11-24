import { useEffect, useRef, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Icon } from '../common/Icon';
import { useAuth } from '../../context/AuthContext';
import { useCountry } from '../../context/CountryContext';
import { useCart } from '../../context/CartContext';
import { countryName, useI18n } from '../../i18n';
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
  const { t } = useI18n();
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
        <Link className="brand" to="/" aria-label={t('nav.brandHome')}>
          Wok &amp; Roll
        </Link>
        <div className="header-tools">
          <label className="country-control">
            <span className="sr-only">{t('nav.country')}</span>
            <select
              aria-label={t('nav.countryCurrent', {
                name: countryName(country, currentCountry.name),
              })}
              value={country}
              onChange={(event) => {
                const selectedCountry = countries.find(({ code }) => code === event.target.value);
                if (selectedCountry) setCountry(selectedCountry.code);
              }}
            >
              {countries.map((item) => (
                <option key={item.code} value={item.code} title={countryName(item.code, item.name)}>
                  {item.code}
                </option>
              ))}
            </select>
            <Icon name="chevron" />
            {isLoadingCountries ? (
              <span className="country-control__status" role="status">
                {t('nav.loadingCountries')}
              </span>
            ) : countriesError ? (
              <span className="country-control__status" role="status" title={countriesError}>
                {import.meta.env.DEV ? t('nav.sampleData') : t('nav.countryUnavailable')}
              </span>
            ) : null}
          </label>
          <div className="header-account">
            <AccountMenu />
          </div>
          <Link className="header-cart" to="/cart" aria-label={t('nav.cart', { count: itemCount })}>
            <Icon name="cart" />
            <span>{itemCount}</span>
          </Link>
          <button
            ref={menuToggle}
            className="menu-toggle"
            type="button"
            aria-label={menuOpen ? t('nav.closeMenu') : t('nav.openMenu')}
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
              {t('nav.logOutAs', { email: auth.email })}
            </button>
          ) : (
            <Link
              className="main-nav__link"
              to={`/login?next=${encodeURIComponent(location.pathname)}`}
              onClick={() => setMenuOpen(false)}
            >
              {t('nav.logIn')}
            </Link>
          )}
        </div>
        <p>{countryName(country, currentCountry.name)}</p>
      </div>
    </header>
  );
}
