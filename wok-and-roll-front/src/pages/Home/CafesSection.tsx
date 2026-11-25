import {
  Component,
  lazy,
  Suspense,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import { Link } from 'react-router-dom';
import { Button, ErrorMessage, Loader } from '../../components/common';
import { Icon } from '../../components/common/Icon';
import { useCountry, type City } from '../../context/CountryContext';
import { CitySwitcher } from '../../components/layout/CitySwitcher';
import { cafeAddress, cafeName, cityName, clock, countryName, useI18n } from '../../i18n';
import { guestErrorMessage, loadCafes, type Cafe } from '../../services/api';
import type { CafeMapLabels, MapCafe } from './CafeMap';

// MapLibre is large: it is loaded only when the map is about to be seen.
const CafeMap = lazy(() => import('./CafeMap'));

/** The map chunk could not be loaded (offline, a new deploy): the list stays. */
class MapBoundary extends Component<{ fallback: ReactNode; children: ReactNode }> {
  state = { failed: false };

  static getDerivedStateFromError() {
    return { failed: true };
  }

  render() {
    return this.state.failed ? this.props.fallback : this.props.children;
  }
}

function minutes(time: string): number {
  return Number(time.slice(0, 2)) * 60 + Number(time.slice(3, 5));
}

/** Minutes since midnight in the time zone of the café, or undefined if the zone is unknown. */
function minutesIn(timeZone: string, now: Date): number | undefined {
  try {
    const parts = new Intl.DateTimeFormat('en-GB', {
      timeZone,
      hour: '2-digit',
      minute: '2-digit',
      hourCycle: 'h23',
    }).formatToParts(now);
    const hour = Number(parts.find(({ type }) => type === 'hour')?.value);
    const minute = Number(parts.find(({ type }) => type === 'minute')?.value);
    return Number.isFinite(hour) && Number.isFinite(minute) ? hour * 60 + minute : undefined;
  } catch {
    return undefined;
  }
}

function prefersReducedMotion(): boolean {
  return (
    typeof window.matchMedia === 'function' &&
    window.matchMedia('(prefers-reduced-motion: reduce)').matches
  );
}

/**
 * "Our cafés" on the home page: the cafés of the chosen city on a map and in a list. The city
 * switcher above them changes the city of the whole site (its prices too), not the language. Every
 * card has the address, the opening hours with "open now" in the café's own time zone, the phone
 * and a link to book a table there. The list is complete without the map.
 *
 * "All cities" shows every café of every country on one map, and the list by city, each city with
 * a button to choose it; booking a table there chooses its city first.
 */
export function CafesSection() {
  const { country, city, countries, cities, allCities, setCity } = useCountry();
  const countryCodes = countries.map(({ code }) => code).join(',');
  const { t, locale } = useI18n();
  const [countryCafes, setCafes] = useState<Cafe[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [retryCount, setRetryCount] = useState(0);
  const [activeId, setActiveId] = useState<number>();
  const [mapFailed, setMapFailed] = useState(false);
  const [mapNear, setMapNear] = useState(false);
  const [now, setNow] = useState(() => new Date());
  const mapBoxRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    let active = true;
    setIsLoading(true);
    setError(undefined);
    setActiveId(undefined);

    const request = allCities
      ? Promise.all(countryCodes.split(',').map((code) => loadCafes(code))).then((lists) =>
          lists.flat(),
        )
      : loadCafes(country);
    request
      .then((result) => {
        if (active) setCafes(result);
      })
      .catch((requestError: unknown) => {
        if (!active) return;
        setCafes([]);
        setError(guestErrorMessage(requestError, t('home.cafes.error')));
      })
      .finally(() => {
        if (active) setIsLoading(false);
      });

    return () => {
      active = false;
    };
  }, [country, retryCount, t, allCities, countryCodes]);

  // "Open now" changes with the clock.
  useEffect(() => {
    const timer = window.setInterval(() => setNow(new Date()), 60_000);
    return () => window.clearInterval(timer);
  }, []);

  // An older server sends no city: then every café of the country is in its (only) city.
  const cafes = useMemo(
    () =>
      allCities
        ? countryCafes
        : countryCafes.filter((cafe) => !city || !cafe.city || cafe.city === city),
    [countryCafes, city, allCities],
  );
  // "All cities": the cafés by city, in the order of the city list.
  const groups = useMemo(
    () =>
      cities
        .map((item) => ({
          city: item,
          cafes: cafes.filter(
            (cafe) => cafe.country === item.country.code && cafe.city === item.name,
          ),
        }))
        .filter((group) => group.cafes.length > 0),
    [cities, cafes],
  );

  const mapCafes = useMemo<MapCafe[]>(
    () =>
      cafes.flatMap(({ id, name, location }) =>
        location ? [{ id, name: cafeName(name), ...location }] : [],
      ),
    // The café names are translated: a new language gives new names.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [cafes, locale],
  );
  const showMap = mapCafes.length > 0;

  const mapLabels = useMemo<CafeMapLabels>(
    () => ({
      map: t('home.cafes.map'),
      zoomIn: t('home.cafes.zoomIn'),
      zoomOut: t('home.cafes.zoomOut'),
      attribution: t('home.cafes.attribution'),
      ctrlScroll: t('home.cafes.ctrlScroll'),
      cmdScroll: t('home.cafes.cmdScroll'),
      twoFingers: t('home.cafes.twoFingers'),
    }),
    [t],
  );

  // Start loading the map a little before it scrolls into view.
  useEffect(() => {
    const box = mapBoxRef.current;
    if (!showMap || mapNear || !box) return undefined;
    if (typeof IntersectionObserver === 'undefined') {
      setMapNear(true);
      return undefined;
    }
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some(({ isIntersecting }) => isIntersecting)) setMapNear(true);
      },
      { rootMargin: '400px 0px' },
    );
    observer.observe(box);
    return () => observer.disconnect();
  }, [showMap, mapNear]);

  function showOnMap(id: number) {
    setActiveId(id);
    // On a phone the map is above the list.
    const box = mapBoxRef.current;
    if (box && typeof box.scrollIntoView === 'function') {
      box.scrollIntoView({
        behavior: prefersReducedMotion() ? 'auto' : 'smooth',
        block: 'nearest',
      });
    }
  }

  /** One café card; in "All cities" booking a table there chooses its city first. */
  function renderCafe(cafe: Cafe, Heading: 'h3' | 'h4', cafeCity?: City) {
    const name = cafeName(cafe.name);
    const isActive = cafe.id === activeId;
    const current = minutesIn(cafe.timeZone, now);
    const isOpen =
      current !== undefined && current >= minutes(cafe.opensAt) && current < minutes(cafe.closesAt);
    return (
      <li key={cafe.id}>
        <article
          className={`cafe-card${isActive ? ' is-active' : ''}`}
          aria-labelledby={`cafe-${cafe.id}-name`}
        >
          <div className="cafe-card__head">
            <Heading id={`cafe-${cafe.id}-name`}>{name}</Heading>
            {current !== undefined && (
              <p className={`cafe-status${isOpen ? ' is-open' : ''}`}>
                <span className="cafe-status__dot" aria-hidden="true" />
                {isOpen
                  ? t('home.cafes.openNow', { time: clock(cafe.closesAt) })
                  : t('home.cafes.closedNow', { time: clock(cafe.opensAt) })}
              </p>
            )}
          </div>
          <ul className="cafe-card__facts">
            <li>
              <Icon name="pin" />
              {cafeAddress(cafe.address)}
            </li>
            <li>
              <Icon name="clock" />
              {t('home.cafes.hours', {
                opens: clock(cafe.opensAt),
                closes: clock(cafe.closesAt),
              })}
            </li>
            {cafe.phone && (
              <li>
                <Icon name="phone" />
                <a href={`tel:${cafe.phone}`}>{cafe.phone}</a>
              </li>
            )}
          </ul>
          <div className="cafe-card__actions">
            {cafe.location && showMap && !mapFailed && (
              <button
                className="cafe-card__locate"
                type="button"
                aria-label={t('home.cafes.showOnMap', { name })}
                aria-pressed={isActive}
                title={t('home.cafes.showOnMap', { name })}
                onClick={() => showOnMap(cafe.id)}
              >
                <Icon name="pin" />
              </button>
            )}
            <Link
              className="button button--primary cafe-card__book"
              to={`/booking?cafe=${cafe.id}`}
              onClick={cafeCity ? () => setCity(cafeCity) : undefined}
              aria-label={t('home.cafes.bookAt', { name })}
            >
              {t('home.cafes.book')}
            </Link>
          </div>
        </article>
      </li>
    );
  }

  const mapFallback = (
    <p className="cafe-map__fallback">
      <Icon name="pin" />
      {t('home.cafes.mapUnavailable')}
    </p>
  );

  return (
    <section className="home-cafes" aria-labelledby="cafes-title">
      <div className="section-heading">
        <div>
          <p className="eyebrow">{t('home.cafes.eyebrow')}</p>
          <h2 id="cafes-title">{t('home.cafes.title')}</h2>
          <p className="home-cafes__description">
            {allCities ? t('home.cafes.allDescription') : t('home.cafes.description')}
          </p>
        </div>
        <CitySwitcher variant="section" allowAll />
      </div>

      {isLoading && <Loader label={t('home.cafes.loading')} />}
      {error && (
        <div className="home-shortcuts__error">
          <ErrorMessage title={t('home.cafes.unavailable')}>{error}</ErrorMessage>
          <Button variant="outline" onClick={() => setRetryCount((count) => count + 1)}>
            {t('common.tryAgain')}
          </Button>
        </div>
      )}
      {!isLoading && !error && cafes.length === 0 && !allCities && (
        <p>{t('home.cafes.empty', { country: city ? cityName(city) : countryName(country) })}</p>
      )}

      {!isLoading && cafes.length > 0 && (
        <div
          className={`cafes-layout${showMap ? '' : ' cafes-layout--list'}${
            allCities ? ' cafes-layout--all' : ''
          }`}
        >
          {showMap && (
            <div className="cafe-map" ref={mapBoxRef}>
              {mapFailed
                ? mapFallback
                : mapNear && (
                    <MapBoundary
                      key={allCities ? 'all' : `${country}-${city}`}
                      fallback={mapFallback}
                    >
                      <Suspense fallback={null}>
                        <CafeMap
                          cafes={mapCafes}
                          activeId={activeId}
                          locale={locale}
                          labels={mapLabels}
                          onSelect={setActiveId}
                          onFail={() => setMapFailed(true)}
                        />
                      </Suspense>
                    </MapBoundary>
                  )}
            </div>
          )}

          {allCities ? (
            <div className="cafes-all">
              {groups.map((group) => {
                const titleId = `cafes-${group.city.country.code}-${group.city.name.replace(/\W/g, '')}`;
                return (
                  <section key={titleId} className="cafes-city" aria-labelledby={titleId}>
                    <div className="cafes-city__head">
                      <div>
                        <h3 id={titleId}>{cityName(group.city.name)}</h3>
                        <p>
                          {countryName(group.city.country.code, group.city.country.name)} ·{' '}
                          {group.city.country.currency}
                        </p>
                      </div>
                      <Button variant="outline" size="small" onClick={() => setCity(group.city)}>
                        {t('home.cafes.chooseCity', { city: cityName(group.city.name) })}
                      </Button>
                    </div>
                    <ul className="cafe-list">
                      {group.cafes.map((cafe) => renderCafe(cafe, 'h4', group.city))}
                    </ul>
                  </section>
                );
              })}
            </div>
          ) : (
            <ul className="cafe-list">{cafes.map((cafe) => renderCafe(cafe, 'h3'))}</ul>
          )}
        </div>
      )}
    </section>
  );
}
