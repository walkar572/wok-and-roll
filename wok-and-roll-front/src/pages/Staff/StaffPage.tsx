import { useCallback, useEffect, useState } from 'react';
import { Button, ErrorMessage, Loader, Toast } from '../../components/common';
import { useCountry } from '../../context/CountryContext';
import { cafeName, clock, t as translate, useI18n, type MessageKey } from '../../i18n';
import {
  changeOrderStatus,
  loadStaffBookings,
  loadStaffOrders,
  type StaffBooking,
  type StaffOrder,
} from '../../services/adminApi';
import {
  formatPrice,
  guestErrorMessage,
  loadCafes,
  type Cafe,
  type OrderStatus,
} from '../../services/api';
import { dateKeyInTimeZone } from '../../utils/dates';
import { StatusBadge } from '../Account/StatusBadge';

const NEXT_ACTIONS: Record<
  OrderStatus,
  Array<{ status: OrderStatus; label: MessageKey; variant: 'primary' | 'outline' | 'danger' }>
> = {
  NEW: [
    { status: 'CONFIRMED', label: 'staff.confirm', variant: 'primary' },
    { status: 'CANCELLED', label: 'staff.cancel', variant: 'outline' },
  ],
  CONFIRMED: [
    { status: 'DONE', label: 'staff.markDone', variant: 'primary' },
    { status: 'CANCELLED', label: 'staff.cancel', variant: 'outline' },
  ],
  DONE: [],
  CANCELLED: [],
};

/** Café orders and bookings for STAFF and ADMIN (API contract §3.14, #65). */
export function StaffPage() {
  const { country, currentCountry } = useCountry();
  const { t } = useI18n();
  const [cafes, setCafes] = useState<Cafe[]>([]);
  const [cafeId, setCafeId] = useState<number>();
  const cafe = cafes.find(({ id }) => id === cafeId);
  const [date, setDate] = useState(() => dateKeyInTimeZone(currentCountry.timeZone));
  const [orders, setOrders] = useState<StaffOrder[]>([]);
  const [bookings, setBookings] = useState<StaffBooking[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string>();
  const [busy, setBusy] = useState<string | null>(null);
  const [toast, setToast] = useState<{ variant: 'success' | 'error'; text: string }>();

  useEffect(() => {
    let active = true;
    loadCafes(country)
      .then((result) => {
        if (!active) return;
        setCafes(result);
        setCafeId(result[0]?.id);
      })
      .catch((requestError: unknown) => {
        if (active) setError(guestErrorMessage(requestError, translate('staff.cafesError')));
      });
    return () => {
      active = false;
    };
  }, [country]);

  const load = useCallback(async () => {
    if (!cafeId || !date) return;
    setLoading(true);
    setError(undefined);
    try {
      const [orderList, bookingList] = await Promise.all([
        loadStaffOrders(cafeId, date),
        loadStaffBookings(cafeId, date),
      ]);
      setOrders(orderList);
      setBookings(bookingList);
    } catch (requestError) {
      setError(guestErrorMessage(requestError, translate('staff.loadError')));
    } finally {
      setLoading(false);
    }
  }, [cafeId, date]);

  useEffect(() => {
    void load();
  }, [load]);

  async function change(order: StaffOrder, status: OrderStatus) {
    setBusy(order.orderNumber);
    try {
      const updated = await changeOrderStatus(order.orderNumber, status);
      setOrders((current) =>
        current.map((item) =>
          item.orderNumber === order.orderNumber ? { ...item, status: updated.status } : item,
        ),
      );
      setToast({
        variant: 'success',
        text: t('staff.changed', {
          number: order.orderNumber,
          status: t(`status.${updated.status}`),
        }),
      });
    } catch (requestError) {
      setToast({
        variant: 'error',
        text: guestErrorMessage(requestError, t('staff.changeError')),
      });
      void load();
    } finally {
      setBusy(null);
    }
  }

  return (
    <section className="staff-page" aria-labelledby="staff-title">
      <p className="eyebrow">{t('staff.eyebrow')}</p>
      <h1 id="staff-title">{t('staff.title')}</h1>
      <p className="page-description">{t('staff.description')}</p>

      <div className="report-filters">
        <div className="input">
          <label className="input__label" htmlFor="staff-cafe">
            {t('staff.cafe')}
          </label>
          <select
            id="staff-cafe"
            className="input__control"
            value={cafeId ?? ''}
            onChange={(event) => setCafeId(Number(event.target.value))}
          >
            {cafes.map((item) => (
              <option key={item.id} value={item.id}>
                {cafeName(item.name)}
              </option>
            ))}
          </select>
        </div>
        <div className="input">
          <label className="input__label" htmlFor="staff-date">
            {t('staff.date')}
          </label>
          <input
            id="staff-date"
            className="input__control"
            type="date"
            value={date}
            onChange={(event) => setDate(event.target.value)}
          />
        </div>
        <Button variant="outline" onClick={() => void load()} disabled={loading}>
          {t('staff.refresh')}
        </Button>
      </div>

      {toast && (
        <Toast
          className="report-toast"
          variant={toast.variant}
          onDismiss={() => setToast(undefined)}
        >
          {toast.text}
        </Toast>
      )}
      {loading && <Loader label={t('staff.loading')} />}
      {error && (
        <div className="page-error">
          <ErrorMessage title={t('staff.notLoaded')}>{error}</ErrorMessage>
          <Button variant="outline" onClick={() => void load()}>
            {t('common.retry')}
          </Button>
        </div>
      )}

      {!loading && !error && (
        <div className="staff-columns">
          <section aria-labelledby="staff-orders-title">
            <h2 id="staff-orders-title">
              {t('staff.orders')} <span className="count">{orders.length}</span>
            </h2>
            {orders.length === 0 ? (
              <p className="muted">
                {t('staff.noOrders', { cafe: cafe ? cafeName(cafe.name) : '' })}
              </p>
            ) : (
              <ul className="order-list">
                {orders.map((order) => (
                  <li key={order.orderNumber} className="order-card">
                    <div className="order-card__main">
                      <h3>
                        {clock(order.slotStart)}–{clock(order.slotEnd)} · {order.orderNumber}
                      </h3>
                      <p>
                        {order.scenario === 'PICKUP'
                          ? t('staff.pickup')
                          : t('staff.delivery', { address: order.deliveryAddress ?? '' })}{' '}
                        · {t('staff.items', { count: order.itemCount })}
                      </p>
                      <p className="order-card__meta">
                        {order.contactName},{' '}
                        <a href={`tel:${order.contactPhone}`}>{order.contactPhone}</a>
                      </p>
                    </div>
                    <div className="order-card__side">
                      <StatusBadge status={order.status} />
                      <strong>{formatPrice(order.total, order.currency)}</strong>
                      <div className="order-card__actions">
                        {NEXT_ACTIONS[order.status].map((action) => (
                          <Button
                            key={action.status}
                            size="small"
                            variant={action.variant}
                            disabled={busy !== null}
                            loading={busy === order.orderNumber && action.variant === 'primary'}
                            onClick={() => void change(order, action.status)}
                          >
                            {t(action.label)}
                            <span className="sr-only"> {order.orderNumber}</span>
                          </Button>
                        ))}
                      </div>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </section>
          <section aria-labelledby="staff-bookings-title">
            <h2 id="staff-bookings-title">
              {t('staff.bookings')} <span className="count">{bookings.length}</span>
            </h2>
            {bookings.length === 0 ? (
              <p className="muted">{t('staff.noBookings')}</p>
            ) : (
              <ul className="order-list">
                {bookings.map((booking) => (
                  <li key={booking.bookingNumber} className="order-card">
                    <div className="order-card__main">
                      <h3>
                        {clock(booking.time)}–{clock(booking.endTime)} · {booking.bookingNumber}
                      </h3>
                      <p>
                        {t('staff.guests', { count: booking.guests })} ·{' '}
                        {t('staff.tableFor', { seats: booking.tableSeats })}
                      </p>
                      <p className="order-card__meta">
                        {booking.contactName},{' '}
                        <a href={`tel:${booking.contactPhone}`}>{booking.contactPhone}</a>
                      </p>
                    </div>
                    <div className="order-card__side">
                      <span className="status-badge">
                        {booking.status === 'CONFIRMED' ? t('staff.confirmed') : booking.status}
                      </span>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </div>
      )}
    </section>
  );
}
