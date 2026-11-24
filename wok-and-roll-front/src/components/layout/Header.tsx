import { useEffect, useRef, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Icon } from '../common/Icon';
import { useAuth } from '../../context/AuthContext';
import { useCart } from '../../context/CartContext';
import { useI18n } from '../../i18n';
import { AccountMenu } from './AccountMenu';
import { CitySwitcher } from './CitySwitcher';
import { LanguageSwitcher } from './LanguageSwitcher';
import { accountLinks, mainLinks, Navigation, visibleLinks } from './Navigation';

export function Header() {
  const [menuOpen, setMenuOpen] = useState(false);
  const [scrolled, setScrolled] = useState(false);
  const header = useRef<HTMLElement>(null);
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

  // The header stays at the top of the screen; once the page moves under it, a soft shadow
  // separates the two.
  useEffect(() => {
    const update = () => setScrolled(window.scrollY > 4);
    update();
    window.addEventListener('scroll', update, { passive: true });
    return () => window.removeEventListener('scroll', update);
  }, []);

  // Keyboard focus never hides under the sticky header (WCAG 2.4.11): Shift+Tab to a link that is
  // under it moves the page down. Only keyboard focus (:focus-visible), so a click never jumps.
  useEffect(() => {
    function keepFocusVisible(event: FocusEvent) {
      const target = event.target;
      const bar = header.current;
      if (!(target instanceof HTMLElement) || !bar || bar.contains(target)) return;
      if (!target.closest('.app-main, .site-footer') || !target.matches(':focus-visible')) return;
      const covered = bar.getBoundingClientRect().bottom + 16 - target.getBoundingClientRect().top;
      if (covered > 16) window.scrollBy({ top: -covered });
    }
    document.addEventListener('focusin', keepFocusVisible);
    return () => document.removeEventListener('focusin', keepFocusVisible);
  }, []);

  // Escape closes the mobile menu and gives the focus back to its button; a tap outside the
  // header closes it too. The city list of the menu opens outside the header (a portal): Escape
  // and taps in that list only close the list.
  useEffect(() => {
    if (!menuOpen) return undefined;
    function inCityList(target: EventTarget | null) {
      return target instanceof Element && target.closest('[data-radix-popper-content-wrapper]');
    }
    function closeOnEscape(event: KeyboardEvent) {
      if (event.key !== 'Escape' || inCityList(event.target)) return;
      setMenuOpen(false);
      menuToggle.current?.focus();
    }
    function closeOnOutsidePointer(event: PointerEvent) {
      if (inCityList(event.target)) return;
      if (!header.current?.contains(event.target as Node)) setMenuOpen(false);
    }
    document.addEventListener('keydown', closeOnEscape);
    document.addEventListener('pointerdown', closeOnOutsidePointer);
    return () => {
      document.removeEventListener('keydown', closeOnEscape);
      document.removeEventListener('pointerdown', closeOnOutsidePointer);
    };
  }, [menuOpen]);

  function closeMenuWhenOpen(open: boolean) {
    if (open) setMenuOpen(false);
  }

  return (
    <header ref={header} className={`site-header figma-header${scrolled ? ' is-scrolled' : ''}`}>
      <div className="site-header__inner">
        <div className="desktop-nav">
          <Navigation />
        </div>
        <Link className="brand" to="/" aria-label={t('nav.brandHome')}>
          Wok &amp; Roll
        </Link>
        <div className="header-tools">
          {/* One menu at a time: a list closes the mobile menu, and while a list is open a tap
              outside it (on the menu button too) only closes the list. The city and the
              language are chosen apart. */}
          <CitySwitcher onOpenChange={closeMenuWhenOpen} />
          <LanguageSwitcher onOpenChange={closeMenuWhenOpen} />
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
        {/* On a phone the header has no room for the city: it is chosen here. */}
        <CitySwitcher variant="menu" />
      </div>
    </header>
  );
}
