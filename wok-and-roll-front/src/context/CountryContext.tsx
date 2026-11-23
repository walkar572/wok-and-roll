import { createContext, useContext, useEffect, useState, type PropsWithChildren } from 'react';
import {
  fallbackCountries,
  loadCountries,
  type Country,
  type CountryCode,
} from '../services/api';

const STORAGE_KEY = 'selectedCountry';

type CountryContextValue = {
  country: CountryCode;
  currentCountry: Country;
  countries: Country[];
  isLoadingCountries: boolean;
  countriesError?: string;
  setCountry: (country: CountryCode) => void;
};

const CountryContext = createContext<CountryContextValue | null>(null);

function getInitialCountry(): CountryCode {
  const savedCountry = window.localStorage.getItem(STORAGE_KEY);
  return savedCountry && /^[A-Z]{3}$/.test(savedCountry)
    ? savedCountry
    : fallbackCountries[0].code;
}

export function CountryProvider({ children }: PropsWithChildren) {
  const [country, setCountry] = useState<CountryCode>(getInitialCountry);
  const [countries, setCountries] = useState<Country[]>(fallbackCountries);
  const [isLoadingCountries, setIsLoadingCountries] = useState(true);
  const [countriesError, setCountriesError] = useState<string>();

  useEffect(() => {
    window.localStorage.setItem(STORAGE_KEY, country);
  }, [country]);

  useEffect(() => {
    let active = true;

    loadCountries()
      .then((result) => {
        if (!active) return;

        setCountries(result);
        setCountriesError(undefined);
        setCountry((selectedCountry) => (
          result.some(({ code }) => code === selectedCountry)
            ? selectedCountry
            : result[0].code
        ));
      })
      .catch((error: unknown) => {
        if (!active) return;
        setCountriesError(error instanceof Error ? error.message : 'Countries API request failed.');
      })
      .finally(() => {
        if (active) setIsLoadingCountries(false);
      });

    return () => {
      active = false;
    };
  }, []);

  const currentCountry = countries.find(({ code }) => code === country) ?? countries[0];

  return (
    <CountryContext.Provider
      value={{
        country,
        currentCountry,
        countries,
        isLoadingCountries,
        countriesError,
        setCountry,
      }}
    >
      {children}
    </CountryContext.Provider>
  );
}

export function useCountry() {
  const context = useContext(CountryContext);

  if (!context) {
    throw new Error('useCountry must be used within a CountryProvider');
  }

  return context;
}
