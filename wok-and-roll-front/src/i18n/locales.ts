// The languages of the interface. The guest chooses the language and the city apart (contract
// v1.9): the city gives the country (prices, currency, cafés), the language only the texts. On the
// first visit the language is the one of the country of the city (only the brand name stays as
// it is).

export type Locale = 'uz' | 'kk' | 'ka' | 'uk' | 'zh' | 'en-GB' | 'en-US';

/** In the order of the language switcher. */
export const LOCALES: Locale[] = ['uz', 'kk', 'ka', 'uk', 'zh', 'en-GB', 'en-US'];

export const COUNTRY_LOCALE: Record<string, Locale> = {
  UZB: 'uz',
  KAZ: 'kk',
  GEO: 'ka',
  UKR: 'uk',
  CHN: 'zh',
  GBR: 'en-GB',
  USA: 'en-US',
};

export const DEFAULT_LOCALE: Locale = 'uz';

/** BCP 47 tags: for Intl (numbers, money, dates) and for <html lang>. */
export const INTL_LOCALE: Record<Locale, string> = {
  uz: 'uz-Latn-UZ',
  kk: 'kk-KZ',
  ka: 'ka-GE',
  uk: 'uk-UA',
  zh: 'zh-CN',
  'en-GB': 'en-GB',
  'en-US': 'en-US',
};

/** The name of each language in itself, for the language switcher. */
export const LANGUAGE_NAME: Record<Locale, string> = {
  uz: 'Oʻzbekcha',
  kk: 'Қазақша',
  ka: 'ქართული',
  uk: 'Українська',
  zh: '中文',
  'en-GB': 'English (UK)',
  'en-US': 'English (US)',
};

/** The short code on the button of the language switcher. */
export const LANGUAGE_CODE: Record<Locale, string> = {
  uz: 'UZ',
  kk: 'KK',
  ka: 'KA',
  uk: 'UK',
  zh: 'ZH',
  'en-GB': 'EN-GB',
  'en-US': 'EN-US',
};

export function isLocale(value: unknown): value is Locale {
  return typeof value === 'string' && (LOCALES as string[]).includes(value);
}

export function localeForCountry(country: string): Locale {
  return COUNTRY_LOCALE[country] ?? DEFAULT_LOCALE;
}
