import { useEffect, useMemo, useState } from 'react';
import { BarChart, type BarDatum } from '../../components/common/BarChart';
import { Button, ErrorMessage, Loader, Toast } from '../../components/common';
import { useCountry } from '../../context/CountryContext';
import { countryName, useI18n, type MessageKey } from '../../i18n';
import { formatPrice, guestErrorMessage } from '../../services/api';
import {
  downloadMonthCsv,
  downloadRegionCsv,
  downloadSalesPdf,
  loadSalesByMonth,
  loadSalesByRegion,
  saveFile,
  type DownloadedFile,
  type SalesByMonth,
  type SalesByRegion,
  type SalesNumbers,
} from '../../services/reportsApi';
import { addMonths, firstOfMonth, localDateKey, monthName } from '../../utils/dates';

type Grouping = 'region' | 'month';
const FIRST_YEAR = 2024;

const MONEY_COLUMNS: Array<{ key: keyof SalesNumbers; label: MessageKey }> = [
  { key: 'subtotal', label: 'reports.colSubtotal' },
  { key: 'vat', label: 'reports.colVat' },
  { key: 'discount', label: 'reports.colDiscount' },
  { key: 'deliveryFee', label: 'reports.colDeliveryFee' },
  { key: 'total', label: 'reports.colTotal' },
  { key: 'averageCheck', label: 'reports.colAverageCheck' },
];

function periodError(from: string, to: string): MessageKey | undefined {
  if (!from || !to) return 'reports.periodBoth';
  if (from > to) return 'reports.periodOrder';
  const days = (Date.parse(to) - Date.parse(from)) / 86_400_000 + 1;
  if (days > 366) return 'reports.periodLong';
  return undefined;
}

function formatGenerated(value: string): string {
  return `${value.slice(0, 10)} ${value.slice(11, 16)} UTC`;
}

/** The sales reports for ADMIN and VIEWER (#180, #181, #182, API contract §3.16–3.19). */
export function ReportsPage() {
  const { country: currentCountry, countries } = useCountry();
  const { t } = useI18n();
  const today = localDateKey(new Date());
  const thisYear = new Date().getFullYear();
  const [grouping, setGrouping] = useState<Grouping>('region');

  // Filters as typed, and the applied filters the data was loaded with.
  const [fromInput, setFromInput] = useState(() => firstOfMonth(addMonths(new Date(), -5)));
  const [toInput, setToInput] = useState(today);
  const [period, setPeriod] = useState({ from: fromInput, to: toInput });
  const [country, setCountry] = useState(currentCountry);
  const [year, setYear] = useState(thisYear);
  const filterError = periodError(fromInput, toInput);

  const [regions, setRegions] = useState<SalesByRegion>();
  const [months, setMonths] = useState<SalesByMonth>();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string>();
  const [retry, setRetry] = useState(0);

  const [pdf, setPdf] = useState<{ file: DownloadedFile; url: string }>();
  const [busy, setBusy] = useState<'generate' | 'download' | 'csv' | null>(null);
  const [toast, setToast] = useState<{ variant: 'success' | 'error'; text: string }>();

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(undefined);
    const request =
      grouping === 'region'
        ? loadSalesByRegion(period.from, period.to).then((result) => active && setRegions(result))
        : loadSalesByMonth(country, year).then((result) => active && setMonths(result));
    request
      .catch((requestError: unknown) => {
        if (active) {
          setError(guestErrorMessage(requestError, t('reports.loadError')));
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [grouping, period, country, year, retry, t]);

  // A new filter makes the old PDF preview out of date.
  useEffect(() => setPdf(undefined), [period, country, year]);

  // The blob URL of a replaced or removed preview is freed.
  useEffect(
    () => () => {
      if (pdf) URL.revokeObjectURL(pdf.url);
    },
    [pdf],
  );

  const filters = { from: period.from, to: period.to, country, year };

  async function generatePdf(): Promise<DownloadedFile | undefined> {
    setBusy('generate');
    try {
      const file = await downloadSalesPdf(filters);
      const url = URL.createObjectURL(file.blob);
      setPdf({ file, url });
      setToast({ variant: 'success', text: t('reports.pdfReady', { file: file.fileName }) });
      return file;
    } catch (requestError) {
      setToast({
        variant: 'error',
        text: guestErrorMessage(requestError, t('reports.pdfError')),
      });
      return undefined;
    } finally {
      setBusy(null);
    }
  }

  async function downloadPdf() {
    if (pdf) {
      saveFile(pdf.file);
      return;
    }
    setBusy('download');
    try {
      saveFile(await downloadSalesPdf(filters));
    } catch (requestError) {
      setToast({
        variant: 'error',
        text: guestErrorMessage(requestError, t('reports.downloadError')),
      });
    } finally {
      setBusy(null);
    }
  }

  async function exportCsv() {
    setBusy('csv');
    try {
      const file =
        grouping === 'region'
          ? await downloadRegionCsv(period.from, period.to)
          : await downloadMonthCsv(country, year);
      saveFile(file);
      setToast({ variant: 'success', text: t('reports.csvSaved', { file: file.fileName }) });
    } catch (requestError) {
      setToast({
        variant: 'error',
        text: guestErrorMessage(requestError, t('reports.csvError')),
      });
    } finally {
      setBusy(null);
    }
  }

  const regionBars: BarDatum[] = useMemo(
    () =>
      (regions?.regions ?? []).map((r) => ({
        key: r.country,
        label: r.country,
        name: countryName(r.country, r.countryName),
        value: r.orders,
        valueText: t('reports.ordersValue', { count: r.orders }),
      })),
    [regions, t],
  );

  // Twelve bars, so no memo: month names and money follow the current language on every render.
  const monthBars: BarDatum[] = (months?.months ?? []).map((m) => ({
    key: String(m.month),
    label: monthName(m.month, 'short'),
    name: `${monthName(m.month, 'long')} ${months?.year}`,
    value: m.total,
    valueText: formatPrice(m.total, months?.currency ?? 'USD'),
  }));

  const years = Array.from({ length: thisYear + 1 - FIRST_YEAR + 1 }, (_, i) => FIRST_YEAR + i);

  return (
    <section className="reports-page" aria-labelledby="reports-title">
      <p className="eyebrow">{t('reports.eyebrow')}</p>
      <h1 id="reports-title">{t('reports.title')}</h1>
      <p className="page-description">{t('reports.description')}</p>

      <div className="report-toolbar">
        <fieldset className="segmented">
          <legend>{t('reports.groupBy')}</legend>
          {(
            [
              ['region', t('reports.region')],
              ['month', t('reports.month')],
            ] as const
          ).map(([value, label]) => (
            <label key={value} className="segmented__option">
              <input
                type="radio"
                name="report-grouping"
                value={value}
                checked={grouping === value}
                onChange={() => setGrouping(value)}
              />
              <span>{label}</span>
            </label>
          ))}
        </fieldset>

        {grouping === 'region' ? (
          <form
            className="report-filters"
            aria-label={t('reports.period')}
            noValidate
            onSubmit={(event) => {
              event.preventDefault();
              if (!filterError) setPeriod({ from: fromInput, to: toInput });
            }}
          >
            <div className="input">
              <label className="input__label" htmlFor="report-from">
                {t('reports.from')}
              </label>
              <input
                id="report-from"
                className={`input__control${filterError ? ' input__control--error' : ''}`}
                type="date"
                value={fromInput}
                max={toInput || undefined}
                aria-invalid={Boolean(filterError) || undefined}
                aria-describedby={filterError ? 'report-period-error' : undefined}
                onChange={(event) => setFromInput(event.target.value)}
              />
            </div>
            <div className="input">
              <label className="input__label" htmlFor="report-to">
                {t('reports.to')}
              </label>
              <input
                id="report-to"
                className={`input__control${filterError ? ' input__control--error' : ''}`}
                type="date"
                value={toInput}
                min={fromInput || undefined}
                aria-invalid={Boolean(filterError) || undefined}
                aria-describedby={filterError ? 'report-period-error' : undefined}
                onChange={(event) => setToInput(event.target.value)}
              />
            </div>
            <Button type="submit" variant="outline" disabled={Boolean(filterError)}>
              {t('reports.apply')}
            </Button>
            {filterError && (
              <span
                id="report-period-error"
                className="input__message input__message--error report-filters__error"
                role="alert"
              >
                {filterError && t(filterError)}
              </span>
            )}
          </form>
        ) : (
          <div className="report-filters" role="group" aria-label={t('reports.countryYear')}>
            <div className="input">
              <label className="input__label" htmlFor="report-country">
                {t('reports.country')}
              </label>
              <select
                id="report-country"
                className="input__control"
                value={country}
                onChange={(event) => setCountry(event.target.value)}
              >
                {countries.map((item) => (
                  <option key={item.code} value={item.code}>
                    {countryName(item.code, item.name)} ({item.currency})
                  </option>
                ))}
              </select>
            </div>
            <div className="input">
              <label className="input__label" htmlFor="report-year">
                {t('reports.year')}
              </label>
              <select
                id="report-year"
                className="input__control"
                value={year}
                onChange={(event) => setYear(Number(event.target.value))}
              >
                {years.map((value) => (
                  <option key={value} value={value}>
                    {value}
                  </option>
                ))}
              </select>
            </div>
          </div>
        )}
      </div>

      <div className="report-actions" role="group" aria-label={t('reports.export')}>
        <Button
          variant="primary"
          loading={busy === 'generate'}
          disabled={busy !== null}
          onClick={() => void generatePdf()}
        >
          {t('reports.generatePdf')}
        </Button>
        <Button
          variant="outline"
          loading={busy === 'download'}
          disabled={busy !== null}
          onClick={() => void downloadPdf()}
        >
          {t('reports.downloadPdf')}
        </Button>
        <Button
          variant="outline"
          loading={busy === 'csv'}
          disabled={busy !== null}
          onClick={() => void exportCsv()}
        >
          {t('reports.exportCsv')}
        </Button>
        <p className="report-actions__note">
          {t(grouping === 'region' ? 'reports.noteRegion' : 'reports.noteMonth', {
            from: period.from,
            to: period.to,
            country: countryName(country),
            year,
          })}
        </p>
      </div>

      {toast && (
        <Toast
          className="report-toast"
          variant={toast.variant}
          onDismiss={() => setToast(undefined)}
        >
          {toast.text}
        </Toast>
      )}

      {pdf && (
        <section className="pdf-preview" aria-labelledby="pdf-preview-title">
          <div className="pdf-preview__header">
            <h2 id="pdf-preview-title">{t('reports.previewTitle')}</h2>
            <a className="text-link" href={pdf.url} target="_blank" rel="noopener noreferrer">
              {t('reports.openNewTab')}
            </a>
          </div>
          <object
            data={pdf.url}
            type="application/pdf"
            className="pdf-preview__frame"
            aria-label={pdf.file.fileName}
          >
            <p>
              {t('reports.noPdfPreview')}{' '}
              <a href={pdf.url} target="_blank" rel="noopener noreferrer">
                {t('reports.openNewTab')}
              </a>
              .
            </p>
          </object>
        </section>
      )}

      {loading && <Loader label={t('reports.loading')} />}
      {error && (
        <div className="page-error">
          <ErrorMessage title={t('reports.notLoaded')}>{error}</ErrorMessage>
          <Button variant="outline" onClick={() => setRetry((count) => count + 1)}>
            {t('common.retry')}
          </Button>
        </div>
      )}

      {!loading && !error && grouping === 'region' && regions && (
        <section className="report-section" aria-labelledby="region-title">
          <div className="report-section__heading">
            <h2 id="region-title">{t('reports.regionTitle')}</h2>
            <p>
              {t('reports.regionMeta', {
                from: regions.from,
                to: regions.to,
                count: regions.totalOrders,
                time: formatGenerated(regions.generatedAt),
              })}
            </p>
          </div>
          <BarChart
            data={regionBars}
            description={t('reports.regionChart', { from: regions.from, to: regions.to })}
          />
          <div
            className="table-scroll"
            role="region"
            aria-labelledby="region-table-caption"
            tabIndex={0}
          >
            <table className="data-table">
              <caption id="region-table-caption">{t('reports.regionTable')}</caption>
              <thead>
                <tr>
                  <th scope="col">{t('reports.colCountry')}</th>
                  <th scope="col">{t('reports.colOrders')}</th>
                  {MONEY_COLUMNS.map((column) => (
                    <th key={column.key} scope="col">
                      {t(column.label)}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {regions.regions.map((r) => (
                  <tr key={r.country}>
                    <th scope="row">{countryName(r.country, r.countryName)}</th>
                    <td>{r.orders}</td>
                    {MONEY_COLUMNS.map((column) => (
                      <td key={column.key}>{formatPrice(r[column.key], r.currency)}</td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}

      {!loading && !error && grouping === 'month' && months && (
        <section className="report-section" aria-labelledby="month-title">
          <div className="report-section__heading">
            <h2 id="month-title">
              {t('reports.monthTitle', {
                country: countryName(months.country, months.countryName),
                year: months.year,
              })}
            </h2>
            <p>
              {t('reports.monthMeta', {
                currency: months.currency,
                time: formatGenerated(months.generatedAt),
              })}
            </p>
          </div>
          <dl
            className="kpi-grid"
            aria-label={t('reports.yearKpis', { year: months.year, currency: months.currency })}
          >
            <div className="kpi">
              <dt>{t('reports.colOrders')}</dt>
              <dd>{months.totals.orders}</dd>
            </div>
            {MONEY_COLUMNS.map((column) => (
              <div className="kpi" key={column.key}>
                <dt>{t(column.label)}</dt>
                <dd>{formatPrice(months.totals[column.key], months.currency)}</dd>
              </div>
            ))}
          </dl>
          <BarChart
            data={monthBars}
            description={t('reports.monthChart', {
              country: countryName(months.country, months.countryName),
              year: months.year,
              currency: months.currency,
            })}
          />
          <div
            className="table-scroll"
            role="region"
            aria-labelledby="month-table-caption"
            tabIndex={0}
          >
            <table className="data-table">
              <caption id="month-table-caption">
                {t('reports.monthTable', {
                  country: countryName(months.country, months.countryName),
                  currency: months.currency,
                })}
              </caption>
              <thead>
                <tr>
                  <th scope="col">{t('reports.colMonth')}</th>
                  <th scope="col">{t('reports.colOrders')}</th>
                  {MONEY_COLUMNS.map((column) => (
                    <th key={column.key} scope="col">
                      {t(column.label)}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {months.months.map((m) => (
                  <tr key={m.month}>
                    <th scope="row">{monthName(m.month, 'long')}</th>
                    <td>{m.orders}</td>
                    {MONEY_COLUMNS.map((column) => (
                      <td key={column.key}>{formatPrice(m[column.key], months.currency)}</td>
                    ))}
                  </tr>
                ))}
              </tbody>
              <tfoot>
                <tr>
                  <th scope="row">{t('reports.colYear')}</th>
                  <td>{months.totals.orders}</td>
                  {MONEY_COLUMNS.map((column) => (
                    <td key={column.key}>
                      {formatPrice(months.totals[column.key], months.currency)}
                    </td>
                  ))}
                </tr>
              </tfoot>
            </table>
          </div>
        </section>
      )}
    </section>
  );
}
