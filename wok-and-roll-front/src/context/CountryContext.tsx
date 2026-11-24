import { createContext, useContext, useEffect, useState, type PropsWithChildren } from 'react';
import { fallbackCountries, loadCountries, type Country, type CountryCode } from '../services/api';

const COUNTRY_KEY = 'selectedCountry';
const CITY_KEY = 'selectedCity';

/** A city the guest can choose, with the country it belongs to (contract v1.9). */
export interface City {
  /** English, as the API gives it: the key for translations and for the cafés of the city. */
  name: string;
  country: Country;
}

type CountryContextValue = {
  /** The country of the chosen city: prices, currency, cafés and delivery zones come from it. */
  country: CountryCode;
  currentCountry: Country;
  countries: Country[];
  /** Every city of every country, in the order of the countries. */
  cities: City[];
  /** The chosen city; undefined while its country has no café yet. */
  city?: string;
  isLoadingCountries: boolean;
  countriesError?: string;
  setCountry: (country: CountryCode) => void;
  /** Chooses a city and with it its country. The language does not change. */
  setCity: (city: City) => void;
};

const CountryContext = createContext<CountryContextValue | null>(null);

function readSaved(key: string): string | null {
  try {
    return window.localStorage.getItem(key);
  } catch {
    return null;
  }
}

function getInitialCountry(): CountryCode {
  const savedCountry = readSaved(COUNTRY_KEY);
  return savedCountry && /^[A-Z]{3}$/.test(savedCountry) ? savedCountry : fallbackCountries[0].code;
}

export function CountryProvider({ children }: PropsWithChildren) {
  const [country, setCountry] = useState<CountryCode>(getInitialCountry);
  const [chosenCity, setChosenCity] = useState<string | null>(() => readSaved(CITY_KEY));
  const [countries, setCountries] = useState<Country[]>(fallbackCountries);
  const [isLoadingCountries, setIsLoadingCountries] = useState(true);
  const [countriesError, setCountriesError] = useState<string>();

  useEffect(() => {
    try {
      window.localStorage.setItem(COUNTRY_KEY, country);
    } catch {
      // Private mode: the choice is kept for this visit only.
    }
  }, [country]);

  useEffect(() => {
    let active = true;

    loadCountries()
      .then((result) => {
        if (!active) return;

        setCountries(result);
        setCountriesError(undefined);
        setCountry((selectedCountry) =>
          result.some(({ code }) => code === selectedCountry) ? selectedCountry : result[0].code,
        );
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
  // A city saved for another country (or gone) gives way to the first city of the country.
  const city =
    chosenCity && currentCountry.cities.includes(chosenCity)
      ? chosenCity
      : currentCountry.cities[0];
  const cities = countries.flatMap((item) => item.cities.map((name) => ({ name, country: item })));

  function setCity(next: City) {
    setChosenCity(next.name);
    setCountry(next.country.code);
    try {
      window.localStorage.setItem(CITY_KEY, next.name);
    } catch {
      // Private mode: the choice is kept for this visit only.
    }
  }

  return (
    <CountryContext.Provider
      value={{
        country,
        currentCountry,
        countries,
        cities,
        city,
        isLoadingCountries,
        countriesError,
        setCountry,
        setCity,
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
