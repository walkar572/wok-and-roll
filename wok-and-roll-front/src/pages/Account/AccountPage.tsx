import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Button, ErrorMessage, Loader } from '../../components/common';
import { ROLE_LABELS, useAuth } from '../../context/AuthContext';
import { loadMyOrders, type OrderSummary } from '../../services/accountApi';
import { formatPrice, guestErrorMessage } from '../../services/api';
import { formatDate, formatDateTime } from '../../utils/dates';
import { StatusBadge } from './StatusBadge';

/** My orders (#184, API contract §3.13): newest first, 10 per page with "Show more". */
export function AccountPage() {
  const { me, auth } = useAuth();
  const [orders, setOrders] = useState<OrderSummary[]>([]);
  const [nextToken, setNextToken] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string>();

  const loadFirstPage = useCallback(() => {
    let active = true;
    setLoading(true);
    setError(undefined);
    loadMyOrders()
      .then((page) => {
        if (!active) return;
        setOrders(page.items);
        setNextToken(page.nextPageToken);
      })
      .catch((requestError: unknown) => {
        if (active) setError(guestErrorMessage(requestError, 'We could not load your orders.'));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => loadFirstPage(), [loadFirstPage]);

  async function loadMore() {
    if (!nextToken) return;
    setLoadingMore(true);
    try {
      const page = await loadMyOrders(nextToken);
      setOrders((current) => [...current, ...page.items]);
      setNextToken(page.nextPageToken);
    } catch (requestError) {
      setError(guestErrorMessage(requestError, 'We could not load more orders.'));
    } finally {
      setLoadingMore(false);
    }
  }

  return (
    <section className="account-page" aria-labelledby="account-title">
      <p className="eyebrow">Your account</p>
      <h1 id="account-title">My orders</h1>
      <p className="page-description">
        {me ? `${me.fullName} · ${me.email}` : auth?.email}
        {auth && <span className="role-pill">{ROLE_LABELS[auth.role]}</span>}
      </p>

      {loading && <Loader label="Loading your orders" />}
      {error && (
        <div className="page-error">
          <ErrorMessage title="Orders not loaded">{error}</ErrorMessage>
          <Button variant="outline" onClick={loadFirstPage}>
            Retry
          </Button>
        </div>
      )}

      {!loading && !error && orders.length === 0 && (
        <div className="empty-state">
          <span className="empty-state__symbol" aria-hidden="true">
            ♡
          </span>
          <h2>No orders yet</h2>
          <p>Orders you place while logged in appear here.</p>
          <Link className="button button--primary" to="/menu">
            Explore the menu
          </Link>
        </div>
      )}

      {orders.length > 0 && (
        <>
          <ul className="order-list" aria-label="Orders, newest first">
            {orders.map((order) => (
              <li key={order.orderNumber} className="order-card">
                <div className="order-card__main">
                  <h2>
                    <Link to={`/account/orders/${encodeURIComponent(order.orderNumber)}`}>
                      <span className="sr-only">Order </span>
                      {order.orderNumber}
                    </Link>
                  </h2>
                  <p>
                    {order.scenario === 'PICKUP' ? 'Pickup' : 'Delivery'} ·{' '}
                    {formatDate(order.orderDate)} at {order.slotStart} · {order.itemCount}{' '}
                    {order.itemCount === 1 ? 'item' : 'items'}
                  </p>
                  <p className="order-card__meta">Placed {formatDateTime(order.createdAt)}</p>
                </div>
                <div className="order-card__side">
                  <StatusBadge status={order.status} />
                  <strong>{formatPrice(order.total, order.currency)}</strong>
                </div>
              </li>
            ))}
          </ul>
          {nextToken && (
            <Button variant="outline" loading={loadingMore} onClick={() => void loadMore()}>
              Show more orders
            </Button>
          )}
        </>
      )}
    </section>
  );
}
