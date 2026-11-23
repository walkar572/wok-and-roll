// Sales reports (API contract §3.16–3.19, #180–#182). PDF and CSV are downloaded with fetch and
// the Authorization header, then opened or saved from a Blob: the token is never put in a URL.
import { apiFetch, requestJson } from './api';
import { list, num, record, str, type Json } from './parse';

export interface SalesNumbers {
  orders: number;
  subtotal: number;
  vat: number;
  discount: number;
  deliveryFee: number;
  total: number;
  averageCheck: number;
}

export interface RegionSales extends SalesNumbers {
  country: string;
  countryName: string;
  currency: string;
}

export interface SalesByRegion {
  from: string;
  to: string;
  generatedAt: string;
  totalOrders: number;
  regions: RegionSales[];
}

export interface MonthSales extends SalesNumbers {
  month: number;
}

export interface SalesByMonth {
  country: string;
  countryName: string;
  currency: string;
  year: number;
  generatedAt: string;
  months: MonthSales[];
  totals: SalesNumbers;
}

function numbers(obj: Json, source: string): SalesNumbers {
  return {
    orders: num(obj, 'orders', source),
    subtotal: num(obj, 'subtotal', source),
    vat: num(obj, 'vat', source),
    discount: num(obj, 'discount', source),
    deliveryFee: num(obj, 'deliveryFee', source),
    total: num(obj, 'total', source),
    averageCheck: num(obj, 'averageCheck', source),
  };
}

export async function loadSalesByRegion(from: string, to: string): Promise<SalesByRegion> {
  const source = 'Sales by region API';
  const body = record(
    await requestJson(`/api/v1/reports/sales-by-region?${new URLSearchParams({ from, to })}`),
    source,
  );
  return {
    from: str(body, 'from', source),
    to: str(body, 'to', source),
    generatedAt: str(body, 'generatedAt', source),
    totalOrders: num(body, 'totalOrders', source),
    regions: list(body.regions, source, 'regions').map((value) => {
      const region = record(value, source, 'region');
      return {
        country: str(region, 'country', source),
        countryName: str(region, 'countryName', source),
        currency: str(region, 'currency', source),
        ...numbers(region, source),
      };
    }),
  };
}

export async function loadSalesByMonth(country: string, year: number): Promise<SalesByMonth> {
  const source = 'Sales by month API';
  const body = record(
    await requestJson(
      `/api/v1/reports/sales-by-month?${new URLSearchParams({ country, year: String(year) })}`,
    ),
    source,
  );
  return {
    country: str(body, 'country', source),
    countryName: str(body, 'countryName', source),
    currency: str(body, 'currency', source),
    year: num(body, 'year', source),
    generatedAt: str(body, 'generatedAt', source),
    months: list(body.months, source, 'months').map((value) => {
      const month = record(value, source, 'month');
      return { month: num(month, 'month', source), ...numbers(month, source) };
    }),
    totals: numbers(record(body.totals, source, 'totals'), source),
  };
}

export interface DownloadedFile {
  blob: Blob;
  fileName: string;
}

/** The file name from Content-Disposition (RFC 6266), or the fallback. */
export function fileNameFrom(header: string | null, fallback: string): string {
  if (!header) return fallback;
  const star = /filename\*=UTF-8''([^;]+)/i.exec(header);
  if (star) return decodeURIComponent(star[1]);
  const plain = /filename="?([^";]+)"?/i.exec(header);
  return plain ? plain[1] : fallback;
}

async function download(path: string, accept: string, fallback: string): Promise<DownloadedFile> {
  const response = await apiFetch(path, { headers: { Accept: accept } });
  return {
    blob: await response.blob(),
    fileName: fileNameFrom(response.headers.get('Content-Disposition'), fallback),
  };
}

export interface ReportFilters {
  from: string;
  to: string;
  country: string;
  year: number;
}

export function downloadSalesPdf({ from, to, country, year }: ReportFilters) {
  const query = new URLSearchParams({ from, to, country, year: String(year) });
  return download(
    `/api/v1/reports/sales.pdf?${query}`,
    'application/pdf',
    `wok-and-roll-sales_${from}_${to}_${country}-${year}.pdf`,
  );
}

export function downloadRegionCsv(from: string, to: string) {
  return download(
    `/api/v1/reports/sales-by-region.csv?${new URLSearchParams({ from, to })}`,
    'text/csv',
    `wok-and-roll-sales-by-region_${from}_${to}.csv`,
  );
}

export function downloadMonthCsv(country: string, year: number) {
  return download(
    `/api/v1/reports/sales-by-month.csv?${new URLSearchParams({ country, year: String(year) })}`,
    'text/csv',
    `wok-and-roll-sales-by-month_${country}_${year}.csv`,
  );
}

/** Saves a downloaded file with <a download>. */
export function saveFile({ blob, fileName }: DownloadedFile) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  document.body.append(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 10_000);
}
