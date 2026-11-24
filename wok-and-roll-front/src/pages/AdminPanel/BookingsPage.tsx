import { cafeName, clock, countryName, useI18n } from '../../i18n';
import { loadAdminBookings } from '../../services/adminPanelApi';
import { formatDate } from '../../utils/dates';
import {
  AccountCell,
  BookingBadge,
  ListFilters,
  ListState,
  PanelHead,
  useAdminCafes,
  useListFilters,
  usePagedList,
} from './shared';

/** Every table booking of every café (API contract §3.20), newest day first. */
export function BookingsPage() {
  const { t } = useI18n();
  const filters = useListFilters(['q', 'country', 'cafeId', 'status', 'from', 'to']);
  const cafes = useAdminCafes();
  const list = usePagedList(
    JSON.stringify(filters.values),
    (pageToken) => loadAdminBookings(filters.values, pageToken),
    t('panel.bookings.error'),
  );

  return (
    <section className="panel-page" aria-labelledby="panel-bookings-title">
      <PanelHead
        title={t('panel.sections.bookings')}
        titleId="panel-bookings-title"
        description={t('panel.bookings.description')}
      />
      <ListFilters
        values={filters.values}
        onChange={filters.set}
        onReset={filters.reset}
        searchHint={t('panel.bookings.search')}
        statuses={[
          { value: 'CONFIRMED', label: t('staff.confirmed') },
          { value: 'CANCELLED', label: t('status.CANCELLED') },
        ]}
        cafes={cafes}
      />
      <ListState
        loading={list.loading}
        loadingLabel={t('panel.bookings.loading')}
        error={list.error}
        errorTitle={t('panel.bookings.error')}
        onRetry={list.retry}
        count={list.items.length}
        hasMore={Boolean(list.nextToken)}
        loadingMore={list.loadingMore}
        onShowMore={() => void list.showMore()}
      >
        <div
          className="table-scroll"
          role="region"
          aria-labelledby="panel-bookings-caption"
          tabIndex={0}
        >
          <table className="data-table panel-table">
            <caption id="panel-bookings-caption">{t('panel.bookings.caption')}</caption>
            <thead>
              <tr>
                <th scope="col">{t('panel.col.number')}</th>
                <th scope="col" className="is-text">
                  {t('panel.col.date')}
                </th>
                <th scope="col" className="is-text">
                  {t('panel.col.cafe')}
                </th>
                <th scope="col">{t('panel.col.guests')}</th>
                <th scope="col" className="is-text">
                  {t('panel.col.customer')}
                </th>
                <th scope="col" className="is-text">
                  {t('panel.col.comment')}
                </th>
                <th scope="col" className="is-text">
                  {t('panel.col.status')}
                </th>
              </tr>
            </thead>
            <tbody>
              {list.items.map((booking) => (
                <tr key={booking.bookingNumber}>
                  <th scope="row">{booking.bookingNumber}</th>
                  <td className="is-text">
                    {formatDate(booking.date)}
                    <span className="panel-table__sub">
                      {clock(booking.time)}–{clock(booking.endTime)}
                    </span>
                  </td>
                  <td className="is-text">
                    {cafeName(booking.cafe.name)}
                    <span className="panel-table__sub">{countryName(booking.country)}</span>
                  </td>
                  <td>
                    {booking.guests}
                    <span className="panel-table__sub">
                      {t('panel.bookings.table', { seats: booking.tableSeats })}
                    </span>
                  </td>
                  <td className="is-text">
                    {booking.contactName}
                    <span className="panel-table__sub">
                      {booking.contactPhone}
                      {booking.contactEmail ? ` · ${booking.contactEmail}` : ''}
                    </span>
                    <span className="panel-table__sub">
                      <AccountCell account={booking.account} />
                    </span>
                  </td>
                  <td className="is-text panel-table__wrap">{booking.comment ?? '—'}</td>
                  <td className="is-text">
                    <BookingBadge status={booking.status} />
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
