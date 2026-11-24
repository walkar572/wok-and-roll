import { createContext, useContext, useEffect, useMemo, type PropsWithChildren } from 'react';
import { useCountry } from '../context/CountryContext';
import { INTL_LOCALE, localeForCountry, type Locale } from './locales';
import { setLocale, t } from './runtime';

export * from './runtime';

// ---- React ---------------------------------------------------------------------------------------

interface I18nValue {
  locale: Locale;
  t: typeof t;
}

const I18nContext = createContext<I18nValue | null>(null);

/** Takes the language from the chosen country and re-renders the app when it changes. */
export function I18nProvider({ children }: PropsWithChildren) {
  const { country } = useCountry();
  const locale = localeForCountry(country);
  setLocale(locale);

  useEffect(() => {
    document.documentElement.lang = INTL_LOCALE[locale];
  }, [locale]);

  const value = useMemo<I18nValue>(
    () => ({ locale, t: (key, params) => t(key, params, locale) }),
    [locale],
  );

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}

export function useI18n(): I18nValue {
  const context = useContext(I18nContext);
  if (!context) throw new Error('useI18n must be used within an I18nProvider');
  return context;
}
