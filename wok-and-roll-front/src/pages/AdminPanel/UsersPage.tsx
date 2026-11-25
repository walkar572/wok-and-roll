import { useEffect, useId, useState, type FormEvent } from 'react';
import { Button } from '../../components/common';
import { useI18n } from '../../i18n';
import { loadAdminUsers } from '../../services/adminPanelApi';
import type { Role } from '../../services/authToken';
import { formatDate, formatDateTime, localDateKey } from '../../utils/dates';
import { ListState, PanelHead, useListFilters, usePagedList } from './shared';

const ROLES: Role[] = ['ADMIN', 'STAFF', 'VIEWER', 'CUSTOMER'];

/** Every account with its role and its orders (API contract §3.20); never a password. */
export function UsersPage() {
  const { t } = useI18n();
  const id = useId();
  const filters = useListFilters(['q', 'role']);
  const { role, q } = filters.values;
  const [search, setSearch] = useState(filters.values.q);
  useEffect(() => setSearch(filters.values.q), [filters.values.q]);
  const list = usePagedList(
    `${role}|${q}`,
    (pageToken) => loadAdminUsers({ role, q }, pageToken),
    t('panel.users.error'),
  );

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    filters.set({ q: search.trim() });
  }

  return (
    <section className="panel-page" aria-labelledby="panel-users-title">
      <PanelHead
        title={t('panel.sections.users')}
        titleId="panel-users-title"
        description={t('panel.users.description')}
      />
      <form
        className="panel-filters"
        role="search"
        aria-label={t('panel.filters.label')}
        onSubmit={submit}
      >
        <div className="input panel-filters__search">
          <label className="input__label" htmlFor={`${id}-q`}>
            {t('panel.filters.search')}
          </label>
          <input
            id={`${id}-q`}
            className="input__control"
            type="search"
            value={search}
            maxLength={100}
            placeholder={t('panel.users.search')}
            onChange={(event) => setSearch(event.target.value)}
          />
        </div>
        <Button type="submit">{t('panel.filters.find')}</Button>
        <div className="input">
          <label className="input__label" htmlFor={`${id}-role`}>
            {t('panel.filters.role')}
          </label>
          <select
            id={`${id}-role`}
            className="input__control"
            value={role}
            onChange={(event) => filters.set({ role: event.target.value })}
          >
            <option value="">{t('panel.filters.allRoles')}</option>
            {ROLES.map((item) => (
              <option key={item} value={item}>
                {t(`roles.${item}`)}
              </option>
            ))}
          </select>
        </div>
      </form>
      <ListState
        loading={list.loading}
        loadingLabel={t('panel.users.loading')}
        error={list.error}
        errorTitle={t('panel.users.error')}
        onRetry={list.retry}
        count={list.items.length}
        hasMore={Boolean(list.nextToken)}
        loadingMore={list.loadingMore}
        onShowMore={() => void list.showMore()}
      >
        <div
          className="table-scroll"
          role="region"
          aria-labelledby="panel-users-caption"
          tabIndex={0}
        >
          <table className="data-table panel-table">
            <caption id="panel-users-caption">{t('panel.users.caption')}</caption>
            <thead>
              <tr>
                <th scope="col">{t('panel.col.name')}</th>
                <th scope="col" className="is-text">
                  {t('panel.col.email')}
                </th>
                <th scope="col" className="is-text">
                  {t('panel.col.phone')}
                </th>
                <th scope="col" className="is-text">
                  {t('panel.col.role')}
                </th>
                <th scope="col" className="is-text">
                  {t('panel.col.created')}
                </th>
                <th scope="col">{t('panel.col.orders')}</th>
                <th scope="col" className="is-text">
                  {t('panel.col.lastOrder')}
                </th>
              </tr>
            </thead>
            <tbody>
              {list.items.map((user) => (
                <tr key={user.id}>
                  <th scope="row">{user.fullName}</th>
                  <td className="is-text">
                    <a href={`mailto:${user.email}`}>{user.email}</a>
                  </td>
                  <td className="is-text">{user.phone ?? '—'}</td>
                  <td className="is-text">
                    <span className={`role-badge role-badge--${user.role.toLowerCase()}`}>
                      {t(`roles.${user.role}`)}
                    </span>
                  </td>
                  <td className="is-text">{formatDate(localDateKey(new Date(user.createdAt)))}</td>
                  <td>{user.orders}</td>
                  <td className="is-text">
                    {user.lastOrderAt ? (
                      formatDateTime(user.lastOrderAt)
                    ) : (
                      <span className="muted">{t('panel.users.never')}</span>
                    )}
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
