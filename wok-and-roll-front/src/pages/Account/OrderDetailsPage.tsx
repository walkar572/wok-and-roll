import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Button, ErrorMessage, Loader, TotalsBlock } from '../../components/common';
import { PagePlaceholder } from '../../components/layout/PagePlaceholder';
import { loadMyOrder } from '../../services/accountApi';
import {
  ApiRequestError,
  formatPrice,
  guestErrorMessage,
  type OrderResponse,
} from '../../services/api';
import { formatDate, formatDateTime } from '../../utils/dates';
import {
  cafeAddress,
  cafeName,
  modifierName,
  productName,
  t as translate,
  useI18n,
  zoneName,
} from '../../i18n';
import { StatusBadge } from './StatusBadge';

/** One of my orders with all lines and totals (#184). */
export function OrderDetailsPage() {
  const { orderNumber = '' } = useParams();
  const [order, setOrder] = useState<OrderResponse>();
  const [notFound, setNotFound] = useState(false);
  const [error, setError] = useState<string>();
  const [retry, setRetry] = useState(0);
  const { t } = useI18n();

  useEffect(() => {
    let active = true;
    setError(undefined);
    setNotFound(false);
    loadMyOrder(orderNumber)
      .then((result) => active && setOrder(result))
      .catch((requestError: unknown) => {
        if (!active) return;
        if (requestError instanceof ApiRequestError && requestError.status === 404)
          setNotFound(true);
        else setError(guestErrorMessage(requestError, translate('orderDetails.loadError')));
      });
    return () => {
      active = false;
    };
  }, [orderNumber, retry]);

  if (notFound) {
    return (
      <PagePlaceholder
        eyebrow={t('notFound.eyebrow')}
        title={t('orderDetails.notFoundTitle')}
        description={t('orderDetails.notFoundDescription', { number: orderNumber })}
        symbol="?"
        panelTitle={t('orderDetails.notFoundPanelTitle')}
        panelDescription={t('orderDetails.notFoundPanelText')}
        actionLabel={t('orderDetails.back')}
        actionTo="/account"
      />
    );
  }

  return (
    <section className="order-details" aria-labelledby="order-title">
      <Link className="text-link back-link" to="/account">
        <span aria-hidden="true">←</span> {t('orderDetails.back')}
      </Link>
      <h1 id="order-title">{t('orderDetails.title', { number: orderNumber })}</h1>
      {!order && !error && <Loader label={t('orderDetails.loading')} />}
      {error && (
        <div className="page-error">
          <ErrorMessage title={t('orderDetails.notLoaded')}>{error}</ErrorMessage>
          <Button variant="outline" onClick={() => setRetry((count) => count + 1)}>
            {t('common.retry')}
          </Button>
        </div>
      )}
      {order && (
        <div className="order-details__layout">
          <div>
            <dl className="checkout-success__details">
              <div>
                <dt>{t('orderDetails.status')}</dt>
                <dd>
                  <StatusBadge status={order.status} />
                </dd>
              </div>
              <div>
                <dt>{t('orderDetails.placed')}</dt>
                <dd>{formatDateTime(order.createdAt)}</dd>
              </div>
              <div>
                <dt>
                  {order.scenario === 'PICKUP' ? t('checkout.pickup') : t('checkout.delivery')}
                </dt>
                <dd>
                  {formatDate(order.orderDate)}, {order.slotStart}–{order.slotEnd}
                </dd>
              </div>
              <div>
                <dt>
                  {order.scenario === 'PICKUP'
                    ? t('success.pickupCafe')
                    : t('success.deliveryAddress')}
                </dt>
                <dd>
                  {order.scenario === 'PICKUP'
                    ? `${cafeName(order.cafe.name)}, ${cafeAddress(order.cafe.address)}`
                    : `${order.deliveryAddress ?? ''} (${zoneName(order.deliveryZone?.name ?? '')})`}
                </dd>
              </div>
            </dl>
            <h2 className="order-details__subtitle">{t('orderDetails.items')}</h2>
            <ul className="order-lines">
              {order.lines.map((line, index) => (
                <li key={`${line.productId}-${index}`}>
                  <div>
                    <strong>{productName(line.name)}</strong>
                    {line.modifiers.length > 0 && (
                      <span>
                        {line.modifiers.map((modifier) => modifierName(modifier.name)).join(', ')}
                      </span>
                    )}
                  </div>
                  <span>
                    {line.quantity} × {formatPrice(line.unitPrice, order.currency)}
                  </span>
                  <strong>{formatPrice(line.lineTotal, order.currency)}</strong>
                </li>
              ))}
            </ul>
          </div>
          <TotalsBlock quote={order} />
        </div>
      )}
    </section>
  );
}
