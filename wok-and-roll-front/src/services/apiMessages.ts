// API error texts in the language of the interface. The server answers in English; the page shows
// a translated message chosen by the error code (API contract §4) and the field name, and falls
// back to the server text only for something it does not know.
import { t, tOptional, type MessageKey } from '../i18n/runtime';

const FIELD_MESSAGES: Record<string, MessageKey> = {
  contactName: 'validation.name',
  contactPhone: 'validation.phone',
  contactEmail: 'validation.email',
  deliveryAddress: 'validation.address',
  comment: 'validation.comment',
  orderDate: 'validation.orderDate',
  slotStart: 'validation.slot',
  cafeId: 'validation.cafe',
  deliveryZoneId: 'validation.zone',
  time: 'booking.validationTime',
  guests: 'booking.validationGuests',
  email: 'login.emailInvalid',
  password: 'login.passwordLength',
  fullName: 'login.nameRequired',
  phone: 'login.phoneInvalid',
  quantity: 'cart.quantityRule',
};

/** Codes that belong to one field: then the field gets the message of the code. */
const CODE_FIELDS: Record<string, string[]> = {
  SLOT_FULL: ['slotStart'],
  INVALID_SLOT: ['slotStart'],
  PAST_DATE: ['orderDate', 'date', 'time'],
  NO_TABLE_AVAILABLE: ['time'],
  UNKNOWN_CAFE: ['cafeId'],
  UNKNOWN_DELIVERY_ZONE: ['deliveryZoneId'],
  EMAIL_TAKEN: ['email'],
  PRICE_CHANGED: ['expectedTotal'],
};

const LINE_FIELD = /^items\[\d+\]\.?(\w+)?/;

/** The message for one field of an API error, in the current language. */
export function apiFieldMessage(field: string, serverMessage: string, code?: string): string {
  if (code && CODE_FIELDS[code]?.includes(field)) {
    return tOptional(`apiErrors.${code}`) ?? serverMessage;
  }
  const line = LINE_FIELD.exec(field);
  if (line) {
    if (line[1] === 'quantity') return t('cart.quantityRule');
    if (line[1] === 'modifierIds') return t('apiErrors.lineOptions');
    return t('apiErrors.lineNotAvailable');
  }
  if (field === 'date') return t('booking.validationDate', { days: 30 });
  const key = FIELD_MESSAGES[field];
  return key ? t(key) : serverMessage;
}

/** The message of an error code, or undefined when the code has no text of its own. */
export function apiCodeMessage(code: string): string | undefined {
  return tOptional(`apiErrors.${code}`);
}
