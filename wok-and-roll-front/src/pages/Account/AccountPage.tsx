import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Button, ErrorMessage, Loader } from '../../components/common';
import { useAuth } from '../../context/AuthContext';
import { t as translate, useI18n } from '../../i18n';
import { loadMyOrders, type OrderSummary } from '../../services/accountApi';
import { formatPrice, guestErrorMessage } from '../../services/api';
import { formatDate, formatDateTime } from '../../utils/dates';
import { StatusBadge } from './StatusBadge';

/** My orders (#184, API contract §3.13): newest first, 10 per page with "Show more". */
export function AccountPage() {
  const { me, auth } = useAuth();
  const { t } = useI18n();
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
        if (active) setError(guestErrorMessage(requestError, translate('account.loadError')));
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
      setError(guestErrorMessage(requestError, t('account.moreError')));
    } finally {
      setLoadingMore(false);
    }
  }

  return (
    <section className="account-page" aria-labelledby="account-title">
      <p className="eyebrow">{t('account.eyebrow')}</p>
      <h1 id="account-title">{t('account.title')}</h1>
      <p className="page-description">
        {me ? `${me.fullName} · ${me.email}` : auth?.email}
        {auth && <span className="role-pill">{t(`roles.${auth.role}`)}</span>}
      </p>

      {loading && <Loader label={t('account.loading')} />}
      {error && (
        <div className="page-error">
          <ErrorMessage title={t('account.notLoaded')}>{error}</ErrorMessage>
          <Button variant="outline" onClick={loadFirstPage}>
            {t('common.retry')}
          </Button>
        </div>
      )}

      {!loading && !error && orders.length === 0 && (
        <div className="empty-state">
          <span className="empty-state__symbol" aria-hidden="true">
            ♡
          </span>
          <h2>{t('account.emptyTitle')}</h2>
          <p>{t('account.emptyText')}</p>
          <Link className="button button--primary" to="/menu">
            {t('account.explore')}
          </Link>
        </div>
      )}

      {orders.length > 0 && (
        <>
          <ul className="order-list" aria-label={t('account.list')}>
            {orders.map((order) => (
              <li key={order.orderNumber} className="order-card">
                <div className="order-card__main">
                  <h2>
                    <Link to={`/account/orders/${encodeURIComponent(order.orderNumber)}`}>
                      <span className="sr-only">{t('account.order')} </span>
                      {order.orderNumber}
                    </Link>
                  </h2>
                  <p>
                    {order.scenario === 'PICKUP' ? t('checkout.pickup') : t('checkout.delivery')} ·{' '}
                    {t('account.when', {
                      date: formatDate(order.orderDate),
                      time: order.slotStart,
                    })}{' '}
                    · {t('account.items', { count: order.itemCount })}
                  </p>
                  <p className="order-card__meta">
                    {t('account.placed', { date: formatDateTime(order.createdAt) })}
                  </p>
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
              {t('account.showMore')}
            </Button>
          )}
        </>
      )}
    </section>
  );
}
