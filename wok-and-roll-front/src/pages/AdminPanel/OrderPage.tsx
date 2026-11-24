import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Button, ErrorMessage, Loader, Receipt, Toast } from '../../components/common';
import {
  cafeAddress,
  cafeName,
  clock,
  countryName,
  t as translate,
  useI18n,
  zoneName,
  type MessageKey,
} from '../../i18n';
import { changeOrderStatus } from '../../services/adminApi';
import { loadAdminOrder, type AdminOrderDetails } from '../../services/adminPanelApi';
import { ApiRequestError, guestErrorMessage, type OrderStatus } from '../../services/api';
import { formatDate, formatDateTime } from '../../utils/dates';
import { StatusBadge } from '../Account/StatusBadge';
import { AccountCell, PanelHead } from './shared';

// The same steps as the café work list (API contract §3.14).
const NEXT: Record<
  OrderStatus,
  Array<{ status: OrderStatus; label: MessageKey; primary: boolean }>
> = {
  NEW: [
    { status: 'CONFIRMED', label: 'staff.confirm', primary: true },
    { status: 'CANCELLED', label: 'staff.cancel', primary: false },
  ],
  CONFIRMED: [
    { status: 'DONE', label: 'staff.markDone', primary: true },
    { status: 'CANCELLED', label: 'staff.cancel', primary: false },
  ],
  DONE: [],
  CANCELLED: [],
};

/** One order with everything the shop knows about it, and its status buttons. */
export function OrderPage() {
  const { orderNumber = '' } = useParams();
  const { t } = useI18n();
  const [details, setDetails] = useState<AdminOrderDetails>();
  const [error, setError] = useState<string>();
  const [notFound, setNotFound] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const [busy, setBusy] = useState(false);
  const [toast, setToast] = useState<{ variant: 'success' | 'error'; text: string }>();

  useEffect(() => {
    let active = true;
    setDetails(undefined);
    setError(undefined);
    setNotFound(false);
    loadAdminOrder(orderNumber)
      .then((result) => {
        if (active) setDetails(result);
      })
      .catch((requestError: unknown) => {
        if (!active) return;
        if (requestError instanceof ApiRequestError && requestError.status === 404) {
          setNotFound(true);
        } else {
          setError(guestErrorMessage(requestError, translate('panel.order.error')));
        }
      });
    return () => {
      active = false;
    };
  }, [orderNumber, attempt]);

  async function change(status: OrderStatus) {
    setBusy(true);
    setToast(undefined);
    try {
      const order = await changeOrderStatus(orderNumber, status);
      setDetails(
        (current) => current && { ...current, order, updatedAt: new Date().toISOString() },
      );
      setToast({
        variant: 'success',
        text: t('panel.order.changed', { status: t(`status.${order.status}`) }),
      });
    } catch (requestError) {
      setToast({
        variant: 'error',
        text: guestErrorMessage(requestError, t('panel.order.changeFailed')),
      });
    } finally {
      setBusy(false);
    }
  }

  const back = (
    <Link className="text-link back-link" to="/admin/orders">
      <span aria-hidden="true">←</span> {t('panel.order.back')}
    </Link>
  );

  if (notFound) {
    return (
      <section className="panel-page" aria-labelledby="panel-order-title">
        {back}
        <PanelHead
          title={t('orderDetails.title', { number: orderNumber })}
          titleId="panel-order-title"
          description={t('panel.order.notFound')}
        />
      </section>
    );
  }

  const order = details?.order;
  return (
    <section className="panel-page" aria-labelledby="panel-order-title">
      {back}
      <PanelHead
        title={t('orderDetails.title', { number: orderNumber })}
        titleId="panel-order-title"
      >
        {order && <StatusBadge status={order.status} />}
      </PanelHead>

      {!details && !error && <Loader label={t('panel.order.loading')} />}
      {error && (
        <div className="page-error">
          <ErrorMessage title={t('panel.order.error')}>{error}</ErrorMessage>
          <Button variant="outline" onClick={() => setAttempt((count) => count + 1)}>
            {t('common.retry')}
          </Button>
        </div>
      )}

      {details && order && (
        <div className="receipt-layout">
          <div className="panel-order">
            <div
              className="panel-card panel-status"
              aria-labelledby="panel-status-title"
              role="group"
            >
              <h2 id="panel-status-title">{t('panel.order.changeStatus')}</h2>
              {NEXT[order.status].length === 0 ? (
                <p className="muted">{t('panel.order.noChange')}</p>
              ) : (
                <div className="panel-status__actions">
                  {NEXT[order.status].map((action) => (
                    <Button
                      key={action.status}
                      variant={action.primary ? 'primary' : 'outline'}
                      disabled={busy}
                      loading={busy && action.primary}
                      onClick={() => void change(action.status)}
                    >
                      {t(action.label)}
                    </Button>
                  ))}
                </div>
              )}
              {toast && (
                <Toast variant={toast.variant} onDismiss={() => setToast(undefined)}>
                  {toast.text}
                </Toast>
              )}
            </div>

            <div className="panel-card">
              <h2>{t('panel.order.details')}</h2>
              <dl className="panel-details">
                <div>
                  <dt>{t('panel.order.placed')}</dt>
                  <dd>{formatDateTime(order.createdAt)}</dd>
                </div>
                <div>
                  <dt>{t('panel.order.updated')}</dt>
                  <dd>{formatDateTime(details.updatedAt)}</dd>
                </div>
                <div>
                  <dt>{t('panel.order.when')}</dt>
                  <dd>
                    {formatDate(order.orderDate)}, {clock(order.slotStart)}–{clock(order.slotEnd)} ·{' '}
                    {order.scenario === 'PICKUP' ? t('checkout.pickup') : t('checkout.delivery')}
                  </dd>
                </div>
                <div>
                  <dt>{t('panel.order.place')}</dt>
                  <dd>
                    {cafeName(order.cafe.name)}, {cafeAddress(order.cafe.address)} (
                    {countryName(order.country)})
                  </dd>
                </div>
                {order.scenario === 'DELIVERY' && (
                  <>
                    <div>
                      <dt>{t('panel.order.zone')}</dt>
                      <dd>{zoneName(order.deliveryZone?.name ?? '')}</dd>
                    </div>
                    <div>
                      <dt>{t('panel.order.address')}</dt>
                      <dd>{order.deliveryAddress}</dd>
                    </div>
                  </>
                )}
                <div>
                  <dt>{t('panel.order.contact')}</dt>
                  <dd>
                    {order.contactName},{' '}
                    <a href={`tel:${order.contactPhone}`}>{order.contactPhone}</a>
                    {details.contactEmail && (
                      <>
                        , <a href={`mailto:${details.contactEmail}`}>{details.contactEmail}</a>
                      </>
                    )}
                  </dd>
                </div>
                <div>
                  <dt>{t('panel.order.account')}</dt>
                  <dd>
                    <AccountCell account={details.account} />
                  </dd>
                </div>
                {details.comment && (
                  <div>
                    <dt>{t('panel.order.comment')}</dt>
                    <dd>{details.comment}</dd>
                  </div>
                )}
              </dl>
            </div>
          </div>
          <Receipt quote={order} />
        </div>
      )}
    </section>
  );
}
