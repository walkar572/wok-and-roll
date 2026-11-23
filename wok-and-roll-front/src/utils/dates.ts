// Dates as YYYY-MM-DD keys in local time, the format of the API (contract §1).

export function localDateKey(value: Date): string {
  const year = value.getFullYear();
  const month = String(value.getMonth() + 1).padStart(2, '0');
  const day = String(value.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

/** Noon avoids daylight-saving edge cases when a key is turned back into a Date. */
export function dateKeyToDate(value: string): Date {
  const [year, month, day] = value.split('-').map(Number);
  return new Date(year, month - 1, day, 12);
}

export function addDays(value: Date, count: number): Date {
  const result = new Date(value);
  result.setDate(result.getDate() + count);
  return result;
}

export function addMonths(value: Date, count: number): Date {
  const result = new Date(value.getFullYear(), value.getMonth() + count, 1, 12);
  return result;
}

export function firstOfMonth(value: Date): string {
  return localDateKey(new Date(value.getFullYear(), value.getMonth(), 1, 12));
}

/** Today in a country's time zone ("today" is the café's date, contract §1). */
export function dateKeyInTimeZone(timeZone: string, now = new Date()): string {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(now);
  const part = (type: string) => parts.find((item) => item.type === type)?.value ?? '';
  return `${part('year')}-${part('month')}-${part('day')}`;
}

/** Minutes since midnight in a time zone, for "at least N minutes from now" checks. */
export function minutesNowInTimeZone(timeZone: string, now = new Date()): number {
  const parts = new Intl.DateTimeFormat('en-GB', {
    timeZone,
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(now);
  const hour = Number(parts.find((item) => item.type === 'hour')?.value ?? 0);
  const minute = Number(parts.find((item) => item.type === 'minute')?.value ?? 0);
  return hour * 60 + minute;
}

export function timeToMinutes(value: string): number {
  const [hour, minute] = value.split(':').map(Number);
  return hour * 60 + minute;
}

export function minutesToTime(value: number): string {
  return `${String(Math.floor(value / 60)).padStart(2, '0')}:${String(value % 60).padStart(2, '0')}`;
}

export function monthName(month: number, style: 'long' | 'short'): string {
  return new Intl.DateTimeFormat('en-GB', { month: style }).format(
    new Date(2026, month - 1, 1, 12),
  );
}

export function formatDate(value: string): string {
  return new Intl.DateTimeFormat('en-GB', {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  }).format(dateKeyToDate(value));
}

export function formatDateTime(value: string): string {
  return new Intl.DateTimeFormat('en-GB', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value));
}
