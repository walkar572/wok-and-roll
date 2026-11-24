import { Link } from 'react-router-dom';
import { cafeName, clock, countryName, useI18n } from '../../i18n';
import { loadAdminOrders } from '../../services/adminPanelApi';
import { formatPrice } from '../../services/api';
import { formatDate } from '../../utils/dates';
import { StatusBadge } from '../Account/StatusBadge';
import {
  AccountCell,
  ListFilters,
  ListState,
  PanelHead,
  useAdminCafes,
  useListFilters,
  usePagedList,
} from './shared';

const STATUSES = ['NEW', 'CONFIRMED', 'DONE', 'CANCELLED'] as const;

/** Every order of every country and café (API contract §3.20), newest day first. */
export function OrdersPage() {
  const { t } = useI18n();
  const filters = useListFilters(['q', 'country', 'cafeId', 'status', 'from', 'to']);
  const cafes = useAdminCafes();
  const list = usePagedList(
    JSON.stringify(filters.values),
    (pageToken) => loadAdminOrders(filters.values, pageToken),
    t('panel.orders.error'),
  );

  return (
    <section className="panel-page" aria-labelledby="panel-orders-title">
      <PanelHead
        title={t('panel.sections.orders')}
        titleId="panel-orders-title"
        description={t('panel.orders.description')}
      />
      <ListFilters
        values={filters.values}
        onChange={filters.set}
        onReset={filters.reset}
        searchHint={t('panel.orders.search')}
        statuses={STATUSES.map((status) => ({ value: status, label: t(`status.${status}`) }))}
        cafes={cafes}
      />
      <ListState
        loading={list.loading}
        loadingLabel={t('panel.orders.loading')}
        error={list.error}
        errorTitle={t('panel.orders.error')}
        onRetry={list.retry}
        count={list.items.length}
        hasMore={Boolean(list.nextToken)}
        loadingMore={list.loadingMore}
        onShowMore={() => void list.showMore()}
      >
        <div
          className="table-scroll"
          role="region"
          aria-labelledby="panel-orders-caption"
          tabIndex={0}
        >
          <table className="data-table panel-table">
            <caption id="panel-orders-caption">{t('panel.orders.caption')}</caption>
            <thead>
              <tr>
                <th scope="col">{t('panel.col.number')}</th>
                <th scope="col" className="is-text">
                  {t('panel.col.date')}
                </th>
                <th scope="col" className="is-text">
                  {t('panel.col.cafe')}
                </th>
                <th scope="col" className="is-text">
                  {t('panel.col.customer')}
                </th>
                <th scope="col">{t('panel.col.total')}</th>
                <th scope="col" className="is-text">
                  {t('panel.col.status')}
                </th>
              </tr>
            </thead>
            <tbody>
              {list.items.map((order) => (
                <tr key={order.orderNumber}>
                  <th scope="row">
                    <Link className="panel-table__link" to={`/admin/orders/${order.orderNumber}`}>
                      {order.orderNumber}
                    </Link>
                  </th>
                  <td className="is-text">
                    {formatDate(order.orderDate)}
                    <span className="panel-table__sub">
                      {clock(order.slotStart)}–{clock(order.slotEnd)} ·{' '}
                      {order.scenario === 'PICKUP' ? t('checkout.pickup') : t('checkout.delivery')}
                    </span>
                  </td>
                  <td className="is-text">
                    {cafeName(order.cafe.name)}
                    <span className="panel-table__sub">{countryName(order.country)}</span>
                  </td>
                  <td className="is-text">
                    {order.contactName}
                    <span className="panel-table__sub">{order.contactPhone}</span>
                    <span className="panel-table__sub">
                      <AccountCell account={order.account} />
                    </span>
                  </td>
                  <td>
                    {formatPrice(order.total, order.currency)}
                    <span className="panel-table__sub">
                      {t('staff.items', { count: order.itemCount })}
                    </span>
                  </td>
                  <td className="is-text">
                    <StatusBadge status={order.status} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </ListState>
    </section>
  );
}
