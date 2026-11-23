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
import { StatusBadge } from './StatusBadge';

/** One of my orders with all lines and totals (#184). */
export function OrderDetailsPage() {
  const { orderNumber = '' } = useParams();
  const [order, setOrder] = useState<OrderResponse>();
  const [notFound, setNotFound] = useState(false);
  const [error, setError] = useState<string>();
  const [retry, setRetry] = useState(0);

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
        else setError(guestErrorMessage(requestError, 'We could not load this order.'));
      });
    return () => {
      active = false;
    };
  }, [orderNumber, retry]);

  if (notFound) {
    return (
      <PagePlaceholder
        eyebrow="404"
        title="Order not found"
        description={`We could not find order ${orderNumber} in your account.`}
        symbol="?"
        panelTitle="Check the order number"
        panelDescription="Only orders placed while you were logged in are in your account."
        actionLabel="Back to my orders"
        actionTo="/account"
      />
    );
  }

  return (
    <section className="order-details" aria-labelledby="order-title">
      <Link className="text-link back-link" to="/account">
        <span aria-hidden="true">←</span> My orders
      </Link>
      <h1 id="order-title">Order {orderNumber}</h1>
      {!order && !error && <Loader label="Loading the order" />}
      {error && (
        <div className="page-error">
          <ErrorMessage title="Order not loaded">{error}</ErrorMessage>
          <Button variant="outline" onClick={() => setRetry((count) => count + 1)}>
            Retry
          </Button>
        </div>
      )}
      {order && (
        <div className="order-details__layout">
          <div>
            <dl className="checkout-success__details">
              <div>
                <dt>Status</dt>
                <dd>
                  <StatusBadge status={order.status} />
                </dd>
              </div>
              <div>
                <dt>Placed</dt>
                <dd>{formatDateTime(order.createdAt)}</dd>
              </div>
              <div>
                <dt>{order.scenario === 'PICKUP' ? 'Pickup' : 'Delivery'}</dt>
                <dd>
                  {formatDate(order.orderDate)}, {order.slotStart}–{order.slotEnd}
                </dd>
              </div>
              <div>
                <dt>{order.scenario === 'PICKUP' ? 'Pickup café' : 'Delivery address'}</dt>
                <dd>
                  {order.scenario === 'PICKUP'
                    ? `${order.cafe.name}, ${order.cafe.address}`
                    : `${order.deliveryAddress ?? ''} (${order.deliveryZone?.name ?? ''})`}
                </dd>
              </div>
            </dl>
            <h2 className="order-details__subtitle">Items</h2>
            <ul className="order-lines">
              {order.lines.map((line, index) => (
                <li key={`${line.productId}-${index}`}>
                  <div>
                    <strong>{line.name}</strong>
                    {line.modifiers.length > 0 && (
                      <span>{line.modifiers.map((modifier) => modifier.name).join(', ')}</span>
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
