import { useEffect, useId, useState } from 'react';
import { Link } from 'react-router-dom';
import { BarChart, type BarDatum } from '../../components/common/BarChart';
import { Button, ErrorMessage, Loader } from '../../components/common';
import {
  cafeName,
  clock,
  countryName,
  intlLocale,
  productName,
  t as translate,
  useI18n,
} from '../../i18n';
import {
  loadAdminOrders,
  loadOverview,
  type AdminOrder,
  type Overview,
} from '../../services/adminPanelApi';
import { formatPrice, guestErrorMessage, type OrderStatus } from '../../services/api';
import { addDays, dateKeyToDate, formatDate, localDateKey } from '../../utils/dates';
import { StatusBadge } from '../Account/StatusBadge';
import { PanelHead } from './shared';

const PERIODS = [7, 30, 90] as const;
const STATUSES: OrderStatus[] = ['NEW', 'CONFIRMED', 'DONE', 'CANCELLED'];

/** The first page of the panel: the numbers of all countries for the last 7, 30 or 90 days. */
export function OverviewPage() {
  const { t, locale } = useI18n();
  const periodId = useId();
  const [days, setDays] = useState<(typeof PERIODS)[number]>(30);
  const [overview, setOverview] = useState<Overview>();
  const [latest, setLatest] = useState<AdminOrder[]>();
  const [error, setError] = useState<string>();
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let active = true;
    const today = new Date();
    setOverview(undefined);
    setError(undefined);
    loadOverview(localDateKey(addDays(today, -(days - 1))), localDateKey(today))
      .then((result) => {
        if (active) setOverview(result);
      })
      .catch((requestError: unknown) => {
        if (active) setError(guestErrorMessage(requestError, translate('panel.overview.error')));
      });
    return () => {
      active = false;
    };
  }, [days, attempt]);

  useEffect(() => {
    let active = true;
    loadAdminOrders({})
      .then((page) => {
        if (active) setLatest(page.items.slice(0, 5));
      })
      .catch(() => {
        if (active) setLatest([]);
      });
    return () => {
      active = false;
    };
  }, [attempt]);

  const number = new Intl.NumberFormat(intlLocale(locale));

  return (
    <section className="panel-page" aria-labelledby="panel-overview-title">
      <PanelHead
        title={t('panel.sections.overview')}
        titleId="panel-overview-title"
        description={t('panel.overview.description')}
      >
        <fieldset className="segmented">
          <legend>{t('panel.period.label')}</legend>
          {PERIODS.map((value) => (
            <label key={value} className="segmented__option">
              <input
                type="radio"
                name={periodId}
                value={value}
                checked={days === value}
                onChange={() => setDays(value)}
              />
              <span>{t('panel.period.days', { count: value })}</span>
            </label>
          ))}
        </fieldset>
      </PanelHead>

      {!overview && !error && <Loader label={t('panel.overview.loading')} />}
      {error && (
        <div className="page-error">
          <ErrorMessage title={t('panel.overview.error')}>{error}</ErrorMessage>
          <Button variant="outline" onClick={() => setAttempt((count) => count + 1)}>
            {t('common.retry')}
          </Button>
        </div>
      )}

      {overview && (
        <>
          <p className="panel-period" role="status">
            {formatDate(overview.from)} – {formatDate(overview.to)}
          </p>
          <dl className="kpi-grid panel-kpis">
            <div className="kpi">
              <dt>{t('panel.overview.orders')}</dt>
              <dd className="kpi__value">{number.format(overview.orders.total)}</dd>
              <dd className="kpi__note">
                {t('panel.overview.cancelled', {
                  count: number.format(overview.orders.byStatus.CANCELLED ?? 0),
                })}
              </dd>
            </div>
            <div className="kpi">
              <dt>{t('panel.overview.delivery')}</dt>
              <dd className="kpi__value">{number.format(overview.orders.delivery)}</dd>
              <dd className="kpi__note">
                {t('panel.overview.deliveryShare', {
                  percent:
                    overview.orders.total === 0
                      ? 0
                      : Math.round((overview.orders.delivery / overview.orders.total) * 100),
                })}
              </dd>
            </div>
            <div className="kpi">
              <dt>{t('panel.overview.bookings')}</dt>
              <dd className="kpi__value">{number.format(overview.bookings.total)}</dd>
              <dd className="kpi__note">
                {t('panel.overview.guests', { count: number.format(overview.bookings.guests) })}
              </dd>
            </div>
            <div className="kpi">
              <dt>{t('panel.overview.accounts')}</dt>
              <dd className="kpi__value">{number.format(overview.accounts.total)}</dd>
              <dd className="kpi__note">
                {t('panel.overview.newAccounts', {
                  count: number.format(overview.accounts.newInPeriod),
                })}
              </dd>
            </div>
          </dl>

          <div className="panel-grid">
            <section className="panel-card panel-grid__wide" aria-labelledby="panel-per-day">
              <h2 id="panel-per-day">{t('panel.overview.perDay')}</h2>
              <BarChart
                data={dayBars(overview, (count) =>
                  t('panel.overview.ordersCount', { count: number.format(count) }),
                )}
                description={t('panel.overview.perDayChart', {
                  from: formatDate(overview.from),
                  to: formatDate(overview.to),
                })}
                height={240}
              />
            </section>

            <section className="panel-card" aria-labelledby="panel-by-status">
              <h2 id="panel-by-status">{t('panel.overview.byStatus')}</h2>
              <ul className="panel-meters">
                {STATUSES.map((status) => {
                  const count = overview.orders.byStatus[status] ?? 0;
                  return (
                    <li key={status}>
                      <StatusBadge status={status} />
                      <span className="panel-meter" aria-hidden="true">
                        <span style={{ width: share(count, overview.orders.total) }} />
                      </span>
                      <strong>{number.format(count)}</strong>
                    </li>
                  );
                })}
              </ul>
            </section>

            <section className="panel-card panel-grid__wide" aria-labelledby="panel-revenue">
              <h2 id="panel-revenue">{t('panel.overview.revenue')}</h2>
              <p className="muted panel-card__note">{t('panel.overview.revenueNote')}</p>
              <div
                className="table-scroll"
                role="region"
                aria-labelledby="panel-revenue"
                tabIndex={0}
              >
                <table className="data-table">
                  <thead>
                    <tr>
                      <th scope="col">{t('panel.col.country')}</th>
                      <th scope="col">{t('panel.col.orders')}</th>
                      <th scope="col">{t('panel.col.revenue')}</th>
                      <th scope="col">{t('panel.col.averageCheck')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {overview.regions.map((region) => (
                      <tr key={region.country}>
                        <th scope="row">{countryName(region.country)}</th>
                        <td>{number.format(region.orders)}</td>
                        <td>{formatPrice(region.revenue, region.currency)}</td>
                        <td>{formatPrice(region.averageCheck, region.currency)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </section>

            <section className="panel-card" aria-labelledby="panel-top-dishes">
              <h2 id="panel-top-dishes">{t('panel.overview.topDishes')}</h2>
              <ol className="panel-meters panel-meters--dishes">
                {overview.topDishes.map((dish) => (
                  <li key={dish.productId}>
                    <span className="panel-meters__name">{productName(dish.name)}</span>
                    <span className="panel-meter" aria-hidden="true">
                      <span
                        style={{
                          width: share(dish.quantity, overview.topDishes[0]?.quantity ?? 0),
                        }}
                      />
                    </span>
                    <strong>
                      {t('panel.overview.ordered', { count: number.format(dish.quantity) })}
                    </strong>
                  </li>
                ))}
              </ol>
            </section>
          </div>
        </>
      )}

      <section className="panel-card panel-latest" aria-labelledby="panel-latest">
        <div className="panel-card__head">
          <h2 id="panel-latest">{t('panel.overview.latest')}</h2>
          <Link className="text-link" to="/admin/orders">
            {t('panel.overview.allOrders')} <span aria-hidden="true">→</span>
          </Link>
        </div>
        {!latest ? (
          <Loader label={t('panel.orders.loading')} />
        ) : latest.length === 0 ? (
          <p className="muted">{t('panel.empty')}</p>
        ) : (
          <ul className="panel-latest__list">
            {latest.map((order) => (
              <li key={order.orderNumber}>
                <Link className="panel-table__link" to={`/admin/orders/${order.orderNumber}`}>
                  {order.orderNumber}
                </Link>
                <span className="muted panel-latest__meta">
                  {formatDate(order.orderDate)}, {clock(order.slotStart)} ·{' '}
                  {cafeName(order.cafe.name)}
                </span>
                <strong>{formatPrice(order.total, order.currency)}</strong>
                <StatusBadge status={order.status} />
              </li>
            ))}
          </ul>
        )}
      </section>
    </section>
  );
}

function share(value: number, whole: number): string {
  return whole > 0 ? `${Math.max(1, Math.round((value / whole) * 100))}%` : '0%';
}

/** One bar a day with the day of the month under it (the chart thins the labels out). */
function dayBars(overview: Overview, valueText: (count: number) => string): BarDatum[] {
  return overview.days.map((day) => ({
    key: day.date,
    label: String(dateKeyToDate(day.date).getDate()),
    name: formatDate(day.date),
    value: day.orders,
    valueText: valueText(day.orders),
  }));
}
