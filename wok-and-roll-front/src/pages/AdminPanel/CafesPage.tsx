import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Button, ErrorMessage, Loader } from '../../components/common';
import {
  cafeAddress,
  cafeName,
  cityName,
  clock,
  countryName,
  t as translate,
  useI18n,
} from '../../i18n';
import { loadAdminCafes, type AdminCafe } from '../../services/adminPanelApi';
import { guestErrorMessage } from '../../services/api';
import { formatDate } from '../../utils/dates';
import { PanelHead } from './shared';

/** Every café by country, with its hours, tables and today in its own time zone (§3.20). */
export function CafesPage() {
  const { t } = useI18n();
  const [cafes, setCafes] = useState<AdminCafe[]>();
  const [error, setError] = useState<string>();
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let active = true;
    setError(undefined);
    loadAdminCafes()
      .then((result) => {
        if (active) setCafes(result);
      })
      .catch((requestError: unknown) => {
        if (active) setError(guestErrorMessage(requestError, translate('panel.cafes.error')));
      });
    return () => {
      active = false;
    };
  }, [attempt]);

  // The API gives the cafés in the order of the countries.
  const countries = [...new Set((cafes ?? []).map((cafe) => cafe.country))];

  return (
    <section className="panel-page" aria-labelledby="panel-cafes-title">
      <PanelHead
        title={t('panel.sections.cafes')}
        titleId="panel-cafes-title"
        description={t('panel.cafes.description')}
      />
      {!cafes && !error && <Loader label={t('panel.cafes.loading')} />}
      {error && (
        <div className="page-error">
          <ErrorMessage title={t('panel.cafes.error')}>{error}</ErrorMessage>
          <Button variant="outline" onClick={() => setAttempt((count) => count + 1)}>
            {t('common.retry')}
          </Button>
        </div>
      )}
      {countries.map((country) => (
        <section key={country} className="panel-cafes" aria-labelledby={`panel-cafes-${country}`}>
          <h2 id={`panel-cafes-${country}`}>{countryName(country)}</h2>
          <ul className="panel-cafes__list">
            {(cafes ?? [])
              .filter((cafe) => cafe.country === country)
              .map((cafe) => {
                const query = `?country=${cafe.country}&cafeId=${cafe.id}`;
                return (
                  <li key={cafe.id} className="panel-card panel-cafe">
                    <div className="panel-cafe__head">
                      <h3>{cafeName(cafe.name)}</h3>
                      <span
                        className={`status-badge status-badge--${cafe.active ? 'done' : 'cancelled'}`}
                      >
                        <span aria-hidden="true">{cafe.active ? '✓' : '✕'}</span>
                        {cafe.active ? t('panel.cafes.open') : t('panel.cafes.closed')}
                      </span>
                    </div>
                    <p>
                      {cityName(cafe.city)} · {cafeAddress(cafe.address)}
                    </p>
                    <ul className="panel-cafe__facts">
                      {cafe.phone && (
                        <li>
                          <a href={`tel:${cafe.phone}`}>{cafe.phone}</a>
                        </li>
                      )}
                      <li>
                        {t('panel.cafes.hours', {
                          opens: clock(cafe.opensAt),
                          closes: clock(cafe.closesAt),
                        })}
                      </li>
                      <li>{t('panel.cafes.tables', { tables: cafe.tables, seats: cafe.seats })}</li>
                      <li>{t('panel.cafes.capacity', { count: cafe.slotCapacity })}</li>
                      <li>{t('panel.cafes.timeZone', { zone: cafe.timeZone })}</li>
                    </ul>
                    <div className="panel-cafe__today">
                      <strong>{t('panel.cafes.today', { date: formatDate(cafe.today) })}</strong>
                      <span>{t('panel.cafes.todayOrders', { count: cafe.todayOrders })}</span>
                      <span>{t('panel.cafes.todayBookings', { count: cafe.todayBookings })}</span>
                    </div>
                    <div className="panel-cafe__links">
                      <Link
                        className="button button--outline button--small"
                        to={`/admin/orders${query}`}
                      >
                        {t('panel.cafes.showOrders')}
                        <span className="sr-only">: {cafeName(cafe.name)}</span>
                      </Link>
                      <Link
                        className="button button--outline button--small"
                        to={`/admin/bookings${query}`}
                      >
                        {t('panel.cafes.showBookings')}
                        <span className="sr-only">: {cafeName(cafe.name)}</span>
                      </Link>
                    </div>
                  </li>
                );
              })}
          </ul>
        </section>
      ))}
    </section>
  );
}
