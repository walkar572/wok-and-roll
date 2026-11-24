import { Link, useLocation } from 'react-router-dom';
import { Receipt } from '../../components/common';
import { PagePlaceholder } from '../../components/layout/PagePlaceholder';
import { useAuth } from '../../context/AuthContext';
import { cafeAddress, cafeName, clock, useI18n, zoneName } from '../../i18n';
import type { OrderResponse } from '../../services/api';
import { formatDate } from '../../utils/dates';

interface SuccessLocationState {
  order?: OrderResponse;
}

export function OrderSuccessPage() {
  const location = useLocation();
  const { t } = useI18n();
  const { auth } = useAuth();
  const state = location.state as SuccessLocationState | null;
  const order = state?.order;

  if (!order) {
    return (
      <PagePlaceholder
        eyebrow={t('success.goneEyebrow')}
        title={t('success.title')}
        description={t('success.goneDescription')}
        symbol="✓"
        panelTitle={t('success.gonePanelTitle')}
        panelDescription={t('success.gonePanelText')}
        actionLabel={t('checkout.explore')}
        actionTo="/menu"
      />
    );
  }

  return (
    <section className="checkout-success" aria-labelledby="order-success-title">
      <p className="eyebrow">{t('success.eyebrow')}</p>
      <h1 id="order-success-title">{t('success.title')}</h1>
      <p className="checkout-success__number">
        {t('success.number')} <strong>{order.orderNumber}</strong>
      </p>
      <div className="receipt-layout">
        <dl className="checkout-success__details">
          <div>
            <dt>{t('success.status')}</dt>
            <dd>{t(`status.${order.status}`)}</dd>
          </div>
          <div>
            <dt>{t('success.orderDate')}</dt>
            <dd>{formatDate(order.orderDate)}</dd>
          </div>
          <div>
            <dt>{t('success.timeSlot')}</dt>
            <dd>
              {clock(order.slotStart)}–{clock(order.slotEnd)}
            </dd>
          </div>
          <div>
            <dt>
              {order.scenario === 'PICKUP' ? t('success.pickupCafe') : t('success.deliveryAddress')}
            </dt>
            <dd>
              {order.scenario === 'PICKUP'
                ? `${cafeName(order.cafe.name)}, ${cafeAddress(order.cafe.address)}`
                : `${order.deliveryAddress ?? ''}${
                    order.deliveryZone ? ` (${zoneName(order.deliveryZone.name)})` : ''
                  }`}
            </dd>
          </div>
        </dl>
        <div className="receipt-layout__side">
          <Receipt quote={order} />
          <div className="receipt-layout__actions">
            <Link className="button button--primary button--large" to="/menu">
              {t('success.backToMenu')}
            </Link>
            {auth && (
              <Link className="button button--outline" to="/account">
                {t('success.myOrders')}
              </Link>
            )}
          </div>
        </div>
      </div>
    </section>
  );
}
