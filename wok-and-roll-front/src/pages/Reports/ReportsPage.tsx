import { useEffect, useMemo, useState } from 'react';
import { BarChart, type BarDatum } from '../../components/common/BarChart';
import { Button, ErrorMessage, Loader, Toast } from '../../components/common';
import { useCountry } from '../../context/CountryContext';
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

const MONEY_COLUMNS: Array<{ key: keyof SalesNumbers; label: string }> = [
  { key: 'subtotal', label: 'Subtotal' },
  { key: 'vat', label: 'VAT' },
  { key: 'discount', label: 'Discount' },
  { key: 'deliveryFee', label: 'Delivery fee' },
  { key: 'total', label: 'Total' },
  { key: 'averageCheck', label: 'Average check' },
];

function periodError(from: string, to: string): string | undefined {
  if (!from || !to) return 'Choose both dates.';
  if (from > to) return 'The start date must be on or before the end date.';
  const days = (Date.parse(to) - Date.parse(from)) / 86_400_000 + 1;
  if (days > 366) return 'The period can be at most 366 days.';
  return undefined;
}

function formatGenerated(value: string): string {
  return `${value.slice(0, 10)} ${value.slice(11, 16)} UTC`;
}

/** The sales reports for ADMIN and VIEWER (#180, #181, #182, API contract §3.16–3.19). */
export function ReportsPage() {
  const { country: currentCountry, countries } = useCountry();
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
          setError(
            guestErrorMessage(requestError, 'We could not load the report. Please try again.'),
          );
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [grouping, period, country, year, retry]);

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
      setToast({ variant: 'success', text: `PDF ready: ${file.fileName}` });
      return file;
    } catch (requestError) {
      setToast({
        variant: 'error',
        text: guestErrorMessage(requestError, 'We could not create the PDF.'),
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
        text: guestErrorMessage(requestError, 'We could not download the PDF.'),
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
      setToast({ variant: 'success', text: `CSV saved: ${file.fileName}` });
    } catch (requestError) {
      setToast({
        variant: 'error',
        text: guestErrorMessage(requestError, 'We could not export the CSV.'),
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
        name: r.countryName,
        value: r.orders,
        valueText: `${r.orders} ${r.orders === 1 ? 'order' : 'orders'}`,
      })),
    [regions],
  );

  const monthBars: BarDatum[] = useMemo(
    () =>
      (months?.months ?? []).map((m) => ({
        key: String(m.month),
        label: monthName(m.month, 'short'),
        name: `${monthName(m.month, 'long')} ${months?.year}`,
        value: m.total,
        valueText: formatPrice(m.total, months?.currency ?? 'USD'),
      })),
    [months],
  );

  const years = Array.from({ length: thisYear + 1 - FIRST_YEAR + 1 }, (_, i) => FIRST_YEAR + i);

  return (
    <section className="reports-page" aria-labelledby="reports-title">
      <p className="eyebrow">The bigger picture</p>
      <h1 id="reports-title">Sales reports</h1>
      <p className="page-description">
        Every country is shown in its own currency. The PDF and the CSV have the same numbers as
        this page.
      </p>

      <div className="report-toolbar">
        <fieldset className="segmented">
          <legend>Group by</legend>
          {(
            [
              ['region', 'Region'],
              ['month', 'Month'],
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
            aria-label="Period"
            noValidate
            onSubmit={(event) => {
              event.preventDefault();
              if (!filterError) setPeriod({ from: fromInput, to: toInput });
            }}
          >
            <div className="input">
              <label className="input__label" htmlFor="report-from">
                From
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
                To
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
              Apply
            </Button>
            {filterError && (
              <span
                id="report-period-error"
                className="input__message input__message--error report-filters__error"
                role="alert"
              >
                {filterError}
              </span>
            )}
          </form>
        ) : (
          <div className="report-filters" role="group" aria-label="Country and year">
            <div className="input">
              <label className="input__label" htmlFor="report-country">
                Country
              </label>
              <select
                id="report-country"
                className="input__control"
                value={country}
                onChange={(event) => setCountry(event.target.value)}
              >
                {countries.map((item) => (
                  <option key={item.code} value={item.code}>
                    {item.name} ({item.currency})
                  </option>
                ))}
              </select>
            </div>
            <div className="input">
              <label className="input__label" htmlFor="report-year">
                Year
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

      <div className="report-actions" role="group" aria-label="Export">
        <Button
          variant="primary"
          loading={busy === 'generate'}
          disabled={busy !== null}
          onClick={() => void generatePdf()}
        >
          Generate PDF
        </Button>
        <Button
          variant="outline"
          loading={busy === 'download'}
          disabled={busy !== null}
          onClick={() => void downloadPdf()}
        >
          Download PDF
        </Button>
        <Button
          variant="outline"
          loading={busy === 'csv'}
          disabled={busy !== null}
          onClick={() => void exportCsv()}
        >
          Export CSV
        </Button>
        <p className="report-actions__note">
          PDF: both reports — {period.from} to {period.to}, and {country} {year}. CSV: the{' '}
          {grouping === 'region' ? 'region' : 'month'} report shown below.
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
            <h2 id="pdf-preview-title">PDF preview</h2>
            <a className="text-link" href={pdf.url} target="_blank" rel="noopener noreferrer">
              Open in a new tab
            </a>
          </div>
          <object
            data={pdf.url}
            type="application/pdf"
            className="pdf-preview__frame"
            aria-label={pdf.file.fileName}
          >
            <p>
              Your browser cannot show the PDF here.{' '}
              <a href={pdf.url} target="_blank" rel="noopener noreferrer">
                Open it in a new tab
              </a>
              .
            </p>
          </object>
        </section>
      )}

      {loading && <Loader label="Loading the report" />}
      {error && (
        <div className="page-error">
          <ErrorMessage title="Report not loaded">{error}</ErrorMessage>
          <Button variant="outline" onClick={() => setRetry((count) => count + 1)}>
            Retry
          </Button>
        </div>
      )}

      {!loading && !error && grouping === 'region' && regions && (
        <section className="report-section" aria-labelledby="region-title">
          <div className="report-section__heading">
            <h2 id="region-title">Orders by country</h2>
            <p>
              {regions.from} to {regions.to} · {regions.totalOrders} orders in all countries ·
              generated {formatGenerated(regions.generatedAt)}
            </p>
          </div>
          <BarChart
            data={regionBars}
            description={`Orders per country from ${regions.from} to ${regions.to}`}
          />
          <div
            className="table-scroll"
            role="region"
            aria-labelledby="region-table-caption"
            tabIndex={0}
          >
            <table className="data-table">
              <caption id="region-table-caption">
                Sales by country, each in its own currency
              </caption>
              <thead>
                <tr>
                  <th scope="col">Country</th>
                  <th scope="col">Orders</th>
                  {MONEY_COLUMNS.map((column) => (
                    <th key={column.key} scope="col">
                      {column.label}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {regions.regions.map((r) => (
                  <tr key={r.country}>
                    <th scope="row">{r.countryName}</th>
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
              Total by month, {months.countryName} {months.year}
            </h2>
            <p>
              In {months.currency} · generated {formatGenerated(months.generatedAt)}
            </p>
          </div>
          <dl className="kpi-grid" aria-label={`Year ${months.year} in ${months.currency}`}>
            <div className="kpi">
              <dt>Orders</dt>
              <dd>{months.totals.orders}</dd>
            </div>
            {MONEY_COLUMNS.map((column) => (
              <div className="kpi" key={column.key}>
                <dt>{column.label}</dt>
                <dd>{formatPrice(months.totals[column.key], months.currency)}</dd>
              </div>
            ))}
          </dl>
          <BarChart
            data={monthBars}
            description={`Sales total per month in ${months.countryName}, ${months.year}, in ${months.currency}`}
          />
          <div
            className="table-scroll"
            role="region"
            aria-labelledby="month-table-caption"
            tabIndex={0}
          >
            <table className="data-table">
              <caption id="month-table-caption">
                Sales by month in {months.countryName}, {months.currency}
              </caption>
              <thead>
                <tr>
                  <th scope="col">Month</th>
                  <th scope="col">Orders</th>
                  {MONEY_COLUMNS.map((column) => (
                    <th key={column.key} scope="col">
                      {column.label}
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
                  <th scope="row">Year</th>
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
