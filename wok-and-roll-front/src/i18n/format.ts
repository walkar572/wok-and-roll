// Money, dates and month names in the language of the interface.
//
// Browsers do not ship Intl data for every language: Chromium formats Uzbek like English
// ("UZS 55,000.00", month "M06") and falls back to the browser language for Kazakh and Georgian
// (Russian month names). So these three languages are formatted here with fixed rules; Ukrainian
// and Chinese use Intl, which every browser supports.
import { INTL_LOCALE, type Locale } from './locales';

interface OwnFormat {
  monthsLong: string[];
  monthsShort: string[];
  /** Sunday first, as Date.getDay(). */
  weekdaysShort: string[];
  /** A date as text: day, month index (0-11), year, weekday index. */
  date: (day: number, month: number, year: number, weekday: number) => string;
  /** The word for "thousand" and "million" on the chart axis. */
  thousand: string;
  million: string;
  /** Sum (UZS) has no symbol; the other currencies have one. */
  sum: string;
}

const OWN: Partial<Record<Locale, OwnFormat>> = {
  uz: {
    monthsLong: [
      'Yanvar',
      'Fevral',
      'Mart',
      'Aprel',
      'May',
      'Iyun',
      'Iyul',
      'Avgust',
      'Sentabr',
      'Oktabr',
      'Noyabr',
      'Dekabr',
    ],
    monthsShort: [
      'Yan',
      'Fev',
      'Mar',
      'Apr',
      'May',
      'Iyn',
      'Iyl',
      'Avg',
      'Sen',
      'Okt',
      'Noy',
      'Dek',
    ],
    weekdaysShort: ['Yak', 'Dush', 'Sesh', 'Chor', 'Pay', 'Jum', 'Shan'],
    date: (d, m, y, w) =>
      `${OWN.uz!.weekdaysShort[w]}, ${d}-${OWN.uz!.monthsLong[m].toLowerCase()}, ${y}`,
    thousand: 'ming',
    million: 'mln',
    sum: 'soʻm',
  },
  kk: {
    monthsLong: [
      'Қаңтар',
      'Ақпан',
      'Наурыз',
      'Сәуір',
      'Мамыр',
      'Маусым',
      'Шілде',
      'Тамыз',
      'Қыркүйек',
      'Қазан',
      'Қараша',
      'Желтоқсан',
    ],
    monthsShort: [
      'Қаң',
      'Ақп',
      'Нау',
      'Сәу',
      'Мам',
      'Мау',
      'Шіл',
      'Там',
      'Қыр',
      'Қаз',
      'Қар',
      'Жел',
    ],
    weekdaysShort: ['Жс', 'Дс', 'Сс', 'Ср', 'Бс', 'Жм', 'Сб'],
    date: (d, m, y, w) =>
      `${OWN.kk!.weekdaysShort[w]}, ${d} ${OWN.kk!.monthsLong[m].toLowerCase()} ${y} ж.`,
    thousand: 'мың',
    million: 'млн',
    sum: 'сум',
  },
  ka: {
    monthsLong: [
      'იანვარი',
      'თებერვალი',
      'მარტი',
      'აპრილი',
      'მაისი',
      'ივნისი',
      'ივლისი',
      'აგვისტო',
      'სექტემბერი',
      'ოქტომბერი',
      'ნოემბერი',
      'დეკემბერი',
    ],
    monthsShort: [
      'იან',
      'თებ',
      'მარ',
      'აპრ',
      'მაი',
      'ივნ',
      'ივლ',
      'აგვ',
      'სექ',
      'ოქტ',
      'ნოე',
      'დეკ',
    ],
    weekdaysShort: ['კვი', 'ორშ', 'სამ', 'ოთხ', 'ხუთ', 'პარ', 'შაბ'],
    date: (d, m, y, w) => `${OWN.ka!.weekdaysShort[w]}, ${d} ${OWN.ka!.monthsLong[m]}, ${y}`,
    thousand: 'ათ.',
    million: 'მლნ',
    sum: 'სუმი',
  },
};

const SYMBOLS: Record<string, string> = { KZT: '₸', GEL: '₾', UAH: '₴', CNY: '¥' };
const NBSP = ' ';

/** 55000 -> "55 000,00" (no-break space between thousands, decimal comma). */
function groupedNumber(value: number, decimals: number): string {
  const [whole, fraction] = Math.abs(value).toFixed(decimals).split('.');
  const grouped = whole.replace(/\B(?=(\d{3})+(?!\d))/g, NBSP);
  return `${value < 0 ? '−' : ''}${grouped}${fraction ? `,${fraction}` : ''}`;
}

/** Money with exactly 2 decimals: "55 000,00 soʻm", "2 190,00 ₸", "179,00 ₴", "¥31.00". */
export function formatMoney(amount: number, currency: string, locale: Locale): string {
  const own = OWN[locale];
  if (own) {
    const symbol = currency === 'UZS' ? own.sum : (SYMBOLS[currency] ?? currency);
    return `${groupedNumber(amount, 2)}${NBSP}${symbol}`;
  }
  return new Intl.NumberFormat(INTL_LOCALE[locale], {
    style: 'currency',
    currency,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(amount);
}

/** Short numbers for a chart axis: "250 ming", "1,5 mln". */
export function formatCompact(value: number, locale: Locale): string {
  const own = OWN[locale];
  if (!own) {
    return new Intl.NumberFormat(INTL_LOCALE[locale], {
      notation: 'compact',
      maximumFractionDigits: 1,
    }).format(value);
  }
  const short = (n: number) => groupedNumber(n, Number.isInteger(n) ? 0 : 1);
  if (Math.abs(value) >= 1_000_000)
    return `${short(Math.round(value / 100_000) / 10)} ${own.million}`;
  if (Math.abs(value) >= 1_000) return `${short(Math.round(value / 100) / 10)} ${own.thousand}`;
  return short(Math.round(value * 10) / 10);
}

/** The month name on its own (a table row, a chart label), capitalised. */
export function formatMonth(month: number, style: 'long' | 'short', locale: Locale): string {
  const own = OWN[locale];
  if (own) return (style === 'long' ? own.monthsLong : own.monthsShort)[month - 1];
  const text = new Intl.DateTimeFormat(INTL_LOCALE[locale], { month: style }).format(
    new Date(2026, month - 1, 1, 12),
  );
  return text.charAt(0).toLocaleUpperCase(INTL_LOCALE[locale]) + text.slice(1);
}

/** A calendar date with the weekday: "Sesh, 24-noyabr, 2026", "вт, 24 лист. 2026 р.". */
export function formatCalendarDate(date: Date, locale: Locale): string {
  const own = OWN[locale];
  if (own) return own.date(date.getDate(), date.getMonth(), date.getFullYear(), date.getDay());
  return new Intl.DateTimeFormat(INTL_LOCALE[locale], {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  }).format(date);
}

/** A moment in local time: the date and HH:mm. */
export function formatMoment(date: Date, locale: Locale): string {
  const time = `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
  return `${formatCalendarDate(date, locale)}, ${time}`;
}
