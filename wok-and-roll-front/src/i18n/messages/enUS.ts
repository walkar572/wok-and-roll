// American English (English (US)): the British text of en.ts with the American words and spelling
// (cart, pickup, favorite, customize, catalog, tax, average check). Only the texts that differ are
// here; every other key is the same as in British English.
import { en, type Messages } from './en';

type Overrides<T> = { [K in keyof T]?: T[K] extends string ? string : Overrides<T[K]> };

const american: Overrides<Messages> = {
  nav: {
    cart: 'Cart, dishes: {count}',
  },
  titles: {
    cart: 'Cart',
  },
  home: {
    cta: 'Find your favorite',
    favoritesTitle: 'A few favorites',
    loadingFavorites: 'Loading favorites',
    favoritesError: 'We could not load our favorites. Please try again.',
    favoritesUnavailable: 'Favorites unavailable',
    cafes: {
      description: 'Pick up your order or book a table at the café nearest you.',
    },
  },
  menu: {
    customize: 'Customize',
  },
  cart: {
    title: 'Your cart',
    emptyTitle: 'Your cart is waiting',
    emptyText: 'There is nothing in your cart yet. Find a favorite to get started.',
    lineMax: 'A cart line cannot have more than 99 items.',
    linesMax: 'A cart cannot have more than 50 lines.',
    fieldItems: 'Cart',
  },
  totals: {
    vat: 'Tax ({rate}%)',
    amountInclVat: 'Amount incl. tax',
  },
  fields: {
    phoneHint: 'International format, for example +16175550123.',
  },
  validation: {
    orderDate: 'Choose a date from today through the next 7 days.',
    cafe: 'Choose a pickup café.',
  },
  checkout: {
    emptyPanelText: 'Add a dish to your cart before you check out.',
    pickup: 'Pickup',
    cafe: 'Pickup café',
    loadingCafes: 'Loading pickup cafés',
    cafesError: 'Could not load pickup cafés',
    cafesFallback: 'We could not load the pickup cafés. Please try again.',
    noCafes: 'There are no pickup cafés in this country.',
    backToCart: 'Back to the cart',
  },
  success: {
    pickupCafe: 'Pickup café',
    gonePanelTitle: 'Ready for another favorite?',
  },
  booking: {
    validationDate: 'Choose a date from today through {days} days ahead.',
  },
  reports: {
    colVat: 'Tax',
    colAverageCheck: 'Average check',
  },
  admin: {
    description:
      'Prices are per country, in the local currency, before tax. Hidden dishes stay in old orders and reports.',
    loading: 'Loading the catalog',
    notLoaded: 'Catalog not loaded',
    loadError: 'We could not load the catalog.',
  },
  staff: {
    pickup: 'Pickup',
  },
};

function merge<T>(base: T, overrides: Overrides<T>): T {
  const result = { ...base } as Record<string, unknown>;
  for (const [key, value] of Object.entries(overrides as Record<string, unknown>)) {
    result[key] =
      typeof value === 'string'
        ? value
        : merge((base as Record<string, unknown>)[key], value as Overrides<unknown>);
  }
  return result as T;
}

export const enUS: Messages = merge(en, american);
