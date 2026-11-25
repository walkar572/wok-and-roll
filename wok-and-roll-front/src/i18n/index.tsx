import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useState,
  type PropsWithChildren,
} from 'react';
import { useCountry } from '../context/CountryContext';
import { INTL_LOCALE, isLocale, localeForCountry, type Locale } from './locales';
import { setLocale, t } from './runtime';

export * from './runtime';

// ---- React ---------------------------------------------------------------------------------------

interface I18nValue {
  locale: Locale;
  t: typeof t;
  /** The guest chooses the language; the city (and its prices) stays as it is. */
  setLanguage: (locale: Locale) => void;
}

const I18nContext = createContext<I18nValue | null>(null);

const STORAGE_KEY = 'selectedLanguage';

function savedLanguage(): Locale | undefined {
  try {
    const saved = window.localStorage.getItem(STORAGE_KEY);
    return isLocale(saved) ? saved : undefined;
  } catch {
    return undefined;
  }
}

/**
 * The language of the interface, chosen apart from the city (contract v1.9). On the first visit it
 * is the language of the country of the city; after that the choice is kept, and a new city never
 * changes it. Re-renders the app when it changes.
 */
export function I18nProvider({ children }: PropsWithChildren) {
  const { country } = useCountry();
  const [locale, setLanguage] = useState<Locale>(
    () => savedLanguage() ?? localeForCountry(country),
  );
  setLocale(locale);

  useEffect(() => {
    document.documentElement.lang = INTL_LOCALE[locale];
    try {
      window.localStorage.setItem(STORAGE_KEY, locale);
    } catch {
      // Private mode: the language is kept for this visit only.
    }
  }, [locale]);

  const value = useMemo<I18nValue>(
    () => ({ locale, t: (key, params) => t(key, params, locale), setLanguage }),
    [locale],
  );

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}

export function useI18n(): I18nValue {
  const context = useContext(I18nContext);
  if (!context) throw new Error('useI18n must be used within an I18nProvider');
  return context;
}
