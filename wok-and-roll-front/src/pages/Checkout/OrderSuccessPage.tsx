import { Link, useLocation } from 'react-router-dom';
import { TotalsBlock } from '../../components/common';
import type { OrderResponse } from '../../services/api';
import { PagePlaceholder } from '../../components/layout/PagePlaceholder';

interface SuccessLocationState {
  order?: OrderResponse;
}

export function OrderSuccessPage() {
  const location = useLocation();
  const state = location.state as SuccessLocationState | null;
  const order = state?.order;

  if (!order) {
    return (
      <PagePlaceholder
        eyebrow="Order complete"
        title="Thanks for your order"
        description="Your order details are no longer available on this page."
        symbol="✓"
        panelTitle="Ready for another favorite?"
        panelDescription="Browse the menu to start a new order."
        actionLabel="Explore the menu"
        actionTo="/menu"
      />
    );
  }

  return (
    <section className="checkout-success" aria-labelledby="order-success-title">
      <p className="eyebrow">Order confirmed</p>
      <h1 id="order-success-title">Thanks for your order</h1>
      <p className="checkout-success__number">
        Order number: <strong>{order.orderNumber}</strong>
      </p>
      <dl className="checkout-success__details">
        <div>
          <dt>Status</dt>
          <dd>{order.status}</dd>
        </div>
        <div>
          <dt>Order date</dt>
          <dd>{order.orderDate}</dd>
        </div>
        <div>
          <dt>Time slot</dt>
          <dd>
            {order.slotStart}–{order.slotEnd}
          </dd>
        </div>
        <div>
          <dt>{order.scenario === 'PICKUP' ? 'Pickup café' : 'Delivery address'}</dt>
          <dd>
            {order.scenario === 'PICKUP'
              ? `${order.cafe.name}, ${order.cafe.address}`
              : order.deliveryAddress}
          </dd>
        </div>
      </dl>
      <TotalsBlock quote={order} />
      <Link className="button button--primary" to="/menu">
        Back to menu
      </Link>
    </section>
  );
}
