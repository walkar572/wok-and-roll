// The interface language follows the chosen country (only the brand name stays as it is).

export type Locale = 'uz' | 'kk' | 'ka' | 'uk' | 'zh' | 'en';

export const COUNTRY_LOCALE: Record<string, Locale> = {
  UZB: 'uz',
  KAZ: 'kk',
  GEO: 'ka',
  UKR: 'uk',
  CHN: 'zh',
  GBR: 'en',
};

export const DEFAULT_LOCALE: Locale = 'uz';

/** BCP 47 tags: for Intl (numbers, money, dates) and for <html lang>. */
export const INTL_LOCALE: Record<Locale, string> = {
  uz: 'uz-Latn-UZ',
  kk: 'kk-KZ',
  ka: 'ka-GE',
  uk: 'uk-UA',
  zh: 'zh-CN',
  en: 'en-GB',
};

export function localeForCountry(country: string): Locale {
  return COUNTRY_LOCALE[country] ?? DEFAULT_LOCALE;
}
