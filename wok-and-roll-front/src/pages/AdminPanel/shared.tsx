import { useEffect, useId, useRef, useState, type FormEvent, type ReactNode } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Button, ErrorMessage, Loader } from '../../components/common';
import { useCountry } from '../../context/CountryContext';
import { cafeName, countryName, useI18n } from '../../i18n';
import { loadAdminCafes, type AdminCafe, type BookingStatus } from '../../services/adminPanelApi';
import { guestErrorMessage } from '../../services/api';
import type { Page } from '../../services/parse';

/** The heading of a panel page: the title, one line about it, and its tools on the right. */
export function PanelHead({
  title,
  titleId,
  description,
  children,
}: {
  title: ReactNode;
  /** For the aria-labelledby of the page section. */
  titleId: string;
  description?: string;
  children?: ReactNode;
}) {
  return (
    <div className="panel-head">
      <div className="panel-head__text">
        <h1 id={titleId}>{title}</h1>
        {description && <p>{description}</p>}
      </div>
      {children && <div className="panel-head__tools">{children}</div>}
    </div>
  );
}

/**
 * One list of the panel API page by page (API contract §1.1): the first page loads again when
 * `key` (the filters) changes; "Show more" adds the next page. There is no total on purpose.
 */
export function usePagedList<T>(
  key: string,
  load: (pageToken?: string | null) => Promise<Page<T>>,
  fallback: string,
) {
  const loadRef = useRef(load);
  loadRef.current = load;
  const fallbackRef = useRef(fallback);
  fallbackRef.current = fallback;
  const [items, setItems] = useState<T[]>([]);
  const [nextToken, setNextToken] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string>();
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(undefined);
    loadRef
      .current(null)
      .then((result) => {
        if (!active) return;
        setItems(result.items);
        setNextToken(result.nextPageToken);
      })
      .catch((requestError: unknown) => {
        if (active) setError(guestErrorMessage(requestError, fallbackRef.current));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [key, attempt]);

  async function showMore() {
    if (!nextToken) return;
    setLoadingMore(true);
    try {
      const result = await loadRef.current(nextToken);
      setItems((current) => [...current, ...result.items]);
      setNextToken(result.nextPageToken);
    } catch (requestError) {
      setError(guestErrorMessage(requestError, fallbackRef.current));
    } finally {
      setLoadingMore(false);
    }
  }

  return {
    items,
    nextToken,
    loading,
    loadingMore,
    error,
    showMore,
    retry: () => setAttempt((count) => count + 1),
  };
}

/** Loading, error with "Try again", the table, and "Show more" under it. */
export function ListState({
  loading,
  loadingLabel,
  error,
  errorTitle,
  onRetry,
  count,
  hasMore,
  loadingMore,
  onShowMore,
  children,
}: {
  loading: boolean;
  loadingLabel: string;
  error?: string;
  errorTitle: string;
  onRetry: () => void;
  count: number;
  hasMore: boolean;
  loadingMore: boolean;
  onShowMore: () => void;
  children: ReactNode;
}) {
  const { t } = useI18n();
  if (loading) return <Loader label={loadingLabel} />;
  if (error && count === 0) {
    return (
      <div className="page-error">
        <ErrorMessage title={errorTitle}>{error}</ErrorMessage>
        <Button variant="outline" onClick={onRetry}>
          {t('common.retry')}
        </Button>
      </div>
    );
  }
  if (count === 0) return <p className="panel-empty">{t('panel.empty')}</p>;
  return (
    <>
      {children}
      <div className="panel-more">
        <span className="muted" role="status">
          {t('panel.shown', { count })}
        </span>
        {error && <ErrorMessage title={errorTitle}>{error}</ErrorMessage>}
        {hasMore && (
          <Button variant="outline" loading={loadingMore} onClick={onShowMore}>
            {t('panel.showMore')}
          </Button>
        )}
      </div>
    </>
  );
}

/** A booking status as text with a symbol, never by colour alone (like the order StatusBadge). */
export function BookingBadge({ status }: { status: BookingStatus | string }) {
  const { t } = useI18n();
  if (status === 'CONFIRMED') {
    return (
      <span className="status-badge status-badge--done">
        <span aria-hidden="true">✓</span>
        {t('staff.confirmed')}
      </span>
    );
  }
  if (status === 'CANCELLED') {
    return (
      <span className="status-badge status-badge--cancelled">
        <span aria-hidden="true">✕</span>
        {t('status.CANCELLED')}
      </span>
    );
  }
  return <span className="status-badge">{status}</span>;
}

/** The account of an order or a booking, or "Guest". */
export function AccountCell({ account }: { account: { email: string } | null }) {
  const { t } = useI18n();
  return account ? <span>{account.email}</span> : <span className="muted">{t('panel.guest')}</span>;
}

/** Every café of the panel (ADMIN), for the café filter; loaded once per page. */
export function useAdminCafes() {
  const [cafes, setCafes] = useState<AdminCafe[]>([]);
  useEffect(() => {
    let active = true;
    loadAdminCafes()
      .then((result) => {
        if (active) setCafes(result);
      })
      .catch(() => {
        // The filter then offers "All cafés" only; the list itself still works.
      });
    return () => {
      active = false;
    };
  }, []);
  return cafes;
}

const FILTER_KEYS = ['q', 'country', 'cafeId', 'status', 'from', 'to', 'role'] as const;
export type FilterKey = (typeof FILTER_KEYS)[number];

/**
 * The filters of a list, kept in the address, so a filtered list can be shared and reloaded.
 * Keys the list does not use are always empty.
 */
export function useListFilters(keys: FilterKey[]) {
  const [params, setParams] = useSearchParams();
  const values = Object.fromEntries(
    FILTER_KEYS.map((key) => [key, keys.includes(key) ? (params.get(key) ?? '') : '']),
  ) as Record<FilterKey, string>;
  function set(changes: Partial<Record<FilterKey, string>>) {
    setParams(
      (current) => {
        const next = new URLSearchParams(current);
        for (const [key, value] of Object.entries(changes)) {
          if (value) next.set(key, value);
          else next.delete(key);
        }
        return next;
      },
      { replace: true },
    );
  }
  return { values, set, reset: () => setParams(new URLSearchParams(), { replace: true }) };
}

/**
 * The filter bar of the order and booking lists: search (applied with Find), country, café of the
 * country, status and dates (applied at once).
 */
export function ListFilters({
  values,
  onChange,
  onReset,
  searchHint,
  statuses,
  cafes,
}: {
  values: Record<FilterKey, string>;
  onChange: (changes: Partial<Record<FilterKey, string>>) => void;
  onReset: () => void;
  searchHint: string;
  statuses: Array<{ value: string; label: string }>;
  cafes: AdminCafe[];
}) {
  const { t } = useI18n();
  const { countries } = useCountry();
  const id = useId();
  const [search, setSearch] = useState(values.q);
  useEffect(() => setSearch(values.q), [values.q]);
  const countryCafes = cafes.filter((cafe) => !values.country || cafe.country === values.country);
  const anyFilter = Object.values(values).some(Boolean);

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    onChange({ q: search.trim() });
  }

  return (
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
          placeholder={searchHint}
          onChange={(event) => setSearch(event.target.value)}
        />
      </div>
      <Button type="submit" variant="primary">
        {t('panel.filters.find')}
      </Button>
      <div className="input">
        <label className="input__label" htmlFor={`${id}-country`}>
          {t('panel.filters.country')}
        </label>
        <select
          id={`${id}-country`}
          className="input__control"
          value={values.country}
          // another country: the café of the old one no longer fits
          onChange={(event) => onChange({ country: event.target.value, cafeId: '' })}
        >
          <option value="">{t('panel.filters.allCountries')}</option>
          {countries.map((country) => (
            <option key={country.code} value={country.code}>
              {countryName(country.code, country.name)}
            </option>
          ))}
        </select>
      </div>
      <div className="input">
        <label className="input__label" htmlFor={`${id}-cafe`}>
          {t('panel.filters.cafe')}
        </label>
        <select
          id={`${id}-cafe`}
          className="input__control"
          value={values.cafeId}
          onChange={(event) => onChange({ cafeId: event.target.value })}
        >
          <option value="">{t('panel.filters.allCafes')}</option>
          {countryCafes.map((cafe) => (
            <option key={cafe.id} value={String(cafe.id)}>
              {cafeName(cafe.name)}
            </option>
          ))}
        </select>
      </div>
      <div className="input">
        <label className="input__label" htmlFor={`${id}-status`}>
          {t('panel.filters.status')}
        </label>
        <select
          id={`${id}-status`}
          className="input__control"
          value={values.status}
          onChange={(event) => onChange({ status: event.target.value })}
        >
          <option value="">{t('panel.filters.allStatuses')}</option>
          {statuses.map((status) => (
            <option key={status.value} value={status.value}>
              {status.label}
            </option>
          ))}
        </select>
      </div>
      <div className="input">
        <label className="input__label" htmlFor={`${id}-from`}>
          {t('panel.filters.from')}
        </label>
        <input
          id={`${id}-from`}
          className="input__control"
          type="date"
          value={values.from}
          max={values.to || undefined}
          onChange={(event) => onChange({ from: event.target.value })}
        />
      </div>
      <div className="input">
        <label className="input__label" htmlFor={`${id}-to`}>
          {t('panel.filters.to')}
        </label>
        <input
          id={`${id}-to`}
          className="input__control"
          type="date"
          value={values.to}
          min={values.from || undefined}
          onChange={(event) => onChange({ to: event.target.value })}
        />
      </div>
      {anyFilter && (
        <Button type="button" variant="ghost" onClick={onReset}>
          {t('panel.filters.reset')}
        </Button>
      )}
    </form>
  );
}
