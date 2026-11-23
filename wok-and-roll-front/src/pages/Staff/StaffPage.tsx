import { useCallback, useEffect, useState } from 'react';
import { Button, ErrorMessage, Loader, Toast } from '../../components/common';
import { useCountry } from '../../context/CountryContext';
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
  Array<{ status: OrderStatus; label: string; variant: 'primary' | 'outline' | 'danger' }>
> = {
  NEW: [
    { status: 'CONFIRMED', label: 'Confirm', variant: 'primary' },
    { status: 'CANCELLED', label: 'Cancel', variant: 'outline' },
  ],
  CONFIRMED: [
    { status: 'DONE', label: 'Mark done', variant: 'primary' },
    { status: 'CANCELLED', label: 'Cancel', variant: 'outline' },
  ],
  DONE: [],
  CANCELLED: [],
};

/** Café orders and bookings for STAFF and ADMIN (API contract §3.14, #65). */
export function StaffPage() {
  const { country, currentCountry } = useCountry();
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
        if (active) setError(guestErrorMessage(requestError, 'We could not load the cafés.'));
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
      setError(guestErrorMessage(requestError, 'We could not load the work list.'));
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
        text: `Order ${order.orderNumber} is now ${updated.status.toLowerCase()}.`,
      });
    } catch (requestError) {
      setToast({
        variant: 'error',
        text: guestErrorMessage(requestError, 'We could not change the status.'),
      });
      void load();
    } finally {
      setBusy(null);
    }
  }

  return (
    <section className="staff-page" aria-labelledby="staff-title">
      <p className="eyebrow">Staff</p>
      <h1 id="staff-title">Café orders</h1>
      <p className="page-description">
        Orders and table bookings of one café for one day, in its local time.
      </p>

      <div className="report-filters">
        <div className="input">
          <label className="input__label" htmlFor="staff-cafe">
            Café
          </label>
          <select
            id="staff-cafe"
            className="input__control"
            value={cafeId ?? ''}
            onChange={(event) => setCafeId(Number(event.target.value))}
          >
            {cafes.map((item) => (
              <option key={item.id} value={item.id}>
                {item.name}
              </option>
            ))}
          </select>
        </div>
        <div className="input">
          <label className="input__label" htmlFor="staff-date">
            Date
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
          Refresh
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
      {loading && <Loader label="Loading the work list" />}
      {error && (
        <div className="page-error">
          <ErrorMessage title="Work list not loaded">{error}</ErrorMessage>
          <Button variant="outline" onClick={() => void load()}>
            Retry
          </Button>
        </div>
      )}

      {!loading && !error && (
        <div className="staff-columns">
          <section aria-labelledby="staff-orders-title">
            <h2 id="staff-orders-title">
              Orders <span className="count">{orders.length}</span>
            </h2>
            {orders.length === 0 ? (
              <p className="muted">No orders for {cafe?.name ?? 'this café'} on this day.</p>
            ) : (
              <ul className="order-list">
                {orders.map((order) => (
                  <li key={order.orderNumber} className="order-card">
                    <div className="order-card__main">
                      <h3>
                        {order.slotStart}–{order.slotEnd} · {order.orderNumber}
                      </h3>
                      <p>
                        {order.scenario === 'PICKUP'
                          ? 'Pickup'
                          : `Delivery: ${order.deliveryAddress ?? ''}`}{' '}
                        · {order.itemCount} {order.itemCount === 1 ? 'item' : 'items'}
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
                            {action.label}
                            <span className="sr-only"> order {order.orderNumber}</span>
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
              Table bookings <span className="count">{bookings.length}</span>
            </h2>
            {bookings.length === 0 ? (
              <p className="muted">No bookings on this day.</p>
            ) : (
              <ul className="order-list">
                {bookings.map((booking) => (
                  <li key={booking.bookingNumber} className="order-card">
                    <div className="order-card__main">
                      <h3>
                        {booking.time}–{booking.endTime} · {booking.bookingNumber}
                      </h3>
                      <p>
                        {booking.guests} {booking.guests === 1 ? 'guest' : 'guests'} · table for{' '}
                        {booking.tableSeats}
                      </p>
                      <p className="order-card__meta">
                        {booking.contactName},{' '}
                        <a href={`tel:${booking.contactPhone}`}>{booking.contactPhone}</a>
                      </p>
                    </div>
                    <div className="order-card__side">
                      <span className="status-badge">
                        {booking.status === 'CONFIRMED' ? 'Confirmed' : booking.status}
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
