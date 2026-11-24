// The calendar (react-day-picker) in the language of the interface.
import { ka, kk, uk, uz, zhCN } from 'react-day-picker/locale';
import type { Locale } from './locales';

export const DAY_PICKER_LOCALE = { uz, kk, ka, uk, zh: zhCN } satisfies Record<Locale, unknown>;
