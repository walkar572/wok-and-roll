// The language-dependent functions without React: message lookup, catalog texts and the locale
// for Intl. The provider in index.tsx sets the locale; services can import this file without a
// circular import through the contexts.
import { catalogTexts } from './catalog';
import { DEFAULT_LOCALE, INTL_LOCALE, type Locale } from './locales';
import { en, type Messages } from './messages/en';
import { ka } from './messages/ka';
import { kk } from './messages/kk';
import { uk } from './messages/uk';
import { uz } from './messages/uz';
import { zh } from './messages/zh';

export type { Locale } from './locales';

const MESSAGES: Record<Locale, Messages> = { uz, kk, ka, uk, zh };

type Path<T> = {
  [K in keyof T & string]: T[K] extends string ? K : `${K}.${Path<T[K]>}`;
}[keyof T & string];

/** Every message key, for example "cart.title". Checked by TypeScript. */
export type MessageKey = Path<Messages>;
export type Params = Record<string, string | number>;

// The locale of the current render. Plain functions (validation messages, API error texts) read
// it too; the provider sets it before its children render, so they always agree with the screen.
let currentLocale: Locale = DEFAULT_LOCALE;

export function setLocale(locale: Locale) {
  currentLocale = locale;
}

export function getLocale(): Locale {
  return currentLocale;
}

export function intlLocale(locale: Locale = currentLocale): string {
  return INTL_LOCALE[locale];
}

function lookup(messages: Messages, key: string): string | undefined {
  let node: unknown = messages;
  for (const part of key.split('.')) {
    if (typeof node !== 'object' || node === null) return undefined;
    node = (node as Record<string, unknown>)[part];
  }
  return typeof node === 'string' ? node : undefined;
}

/** The text of a key in the current language; {name} placeholders are filled from params. */
export function t(key: MessageKey, params?: Params, locale: Locale = currentLocale): string {
  const text = lookup(MESSAGES[locale], key) ?? lookup(en, key) ?? key;
  if (!params) return text;
  return text.replace(/\{(\w+)\}/g, (match, name: string) =>
    name in params ? String(params[name]) : match,
  );
}

/** Like t(), but for keys built at run time (an API error code); undefined if there is none. */
export function tOptional(key: string, params?: Params): string | undefined {
  const text = lookup(MESSAGES[currentLocale], key);
  return text === undefined ? undefined : t(key as MessageKey, params);
}

// ---- catalog data (English from the API) --------------------------------------------------------

export function productName(name: string): string {
  return catalogTexts(currentLocale).products[name]?.[0] ?? name;
}

/** The description of a dish; the English name is the key, the API text is the fallback. */
export function productDescription(name: string, description: string | null): string | null {
  return catalogTexts(currentLocale).products[name]?.[1] ?? description;
}

/** The numbers of a portion, as the API gives them. */
export interface Portion {
  pieces: number | null;
  weightGrams: number | null;
  volumeMl: number | null;
}

/** The portion of a dish on one line: "8 dona · 250 g", "400 ml"; null when nothing is known. */
export function portionText(portion: Portion): string | null {
  const parts: string[] = [];
  if (portion.pieces) parts.push(t('product.portion.pieces', { count: portion.pieces }));
  if (portion.weightGrams) parts.push(t('product.portion.grams', { count: portion.weightGrams }));
  if (portion.volumeMl) parts.push(t('product.portion.ml', { count: portion.volumeMl }));
  return parts.length > 0 ? parts.join(' · ') : null;
}

export function categoryName(name: string): string {
  return catalogTexts(currentLocale).categories[name] ?? name;
}

export function modifierName(name: string): string {
  return catalogTexts(currentLocale).modifiers[name] ?? name;
}

export function zoneName(name: string): string {
  return catalogTexts(currentLocale).zones[name] ?? name;
}

export function cafeName(name: string): string {
  return catalogTexts(currentLocale).cafes[name] ?? name;
}

export function cafeAddress(address: string): string {
  return catalogTexts(currentLocale).addresses[address] ?? address;
}

export function countryName(code: string, fallback?: string): string {
  return catalogTexts(currentLocale).countries[code] ?? fallback ?? code;
}
