import { useEffect, useRef, useState, type FormEvent } from 'react';
import * as RadioGroup from '@radix-ui/react-radio-group';
import { DayPicker } from 'react-day-picker';
import 'react-day-picker/style.css';
import { Link, useNavigate } from 'react-router-dom';
import { Button, ErrorMessage, Input, Loader, TotalsBlock } from '../../components/common';
import { useCart, type CartItem } from '../../context/CartContext';
import { useCountry } from '../../context/CountryContext';
import {
  cafeAddress,
  cafeName,
  modifierName,
  productName,
  t as translate,
  useI18n,
  zoneName,
} from '../../i18n';
import { DAY_PICKER_LOCALE } from '../../i18n/dayPicker';
import { apiFieldMessage } from '../../services/apiMessages';
import { createIdempotencyKey } from '../../services/parse';
import {
  addDays,
  dateKeyInTimeZone,
  dateKeyToDate as dateKeyToLocalDate,
  formatDate as formatOrderDate,
  localDateKey as localDateToKey,
} from '../../utils/dates';
import {
  ApiRequestError,
  formatPrice,
  guestErrorMessage,
  loadCafes,
  loadDeliveryZones,
  loadTimeSlots,
  placeOrder,
  quoteCart,
  type Cafe,
  type CartQuote,
  type DeliveryZone,
  type TimeSlots,
} from '../../services/api';
import { PagePlaceholder } from '../../components/layout/PagePlaceholder';

type Scenario = 'PICKUP' | 'DELIVERY';
type ContactField = 'contactName' | 'contactPhone' | 'contactEmail' | 'comment' | 'deliveryAddress';
type CheckoutErrorField = ContactField | 'orderDate' | 'slotStart' | 'cafeId' | 'deliveryZoneId';

interface ContactDetails {
  contactName: string;
  contactPhone: string;
  contactEmail: string;
  comment: string;
  deliveryAddress: string;
}

function validateContact(
  details: ContactDetails,
  scenario: Scenario,
  orderDate: string,
  today: string,
) {
  const errors: Partial<Record<CheckoutErrorField, string>> = {};
  const name = details.contactName.trim();
  const phone = details.contactPhone.replace(/\s/g, '');
  const email = details.contactEmail.trim();
  const address = details.deliveryAddress.trim();

  if (name.length < 2 || name.length > 100) {
    errors.contactName = translate('validation.name');
  }
  if (!/^\+\d{8,15}$/.test(phone)) {
    errors.contactPhone = translate('validation.phone');
  }
  if (email && (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) || email.length > 254)) {
    errors.contactEmail = translate('validation.email');
  }
  if (scenario === 'DELIVERY' && (address.length < 5 || address.length > 200)) {
    errors.deliveryAddress = translate('validation.address');
  }
  if (details.comment.length > 300) errors.comment = translate('validation.comment');
  if (orderDate < today || orderDate > localDateToKey(addDays(dateKeyToLocalDate(today), 7))) {
    errors.orderDate = translate('validation.orderDate');
  }
  return errors;
}

const FIELD_LABELS: Record<string, () => string> = {
  expectedTotal: () => translate('checkout.orderTotal'),
  country: () => translate('cart.fieldCountry'),
  scenario: () => translate('checkout.orderType'),
  items: () => translate('checkout.items'),
  'Idempotency-Key': () => translate('checkout.orderRequest'),
};

function fieldLabel(fieldName: string): string {
  const known = FIELD_LABELS[fieldName];
  if (known) return known();

  const words = fieldName
    .replace(/^items\[(\d+)\]/, (_, index: string) => `item ${Number(index) + 1}`)
    .replace(/[._]/g, ' ')
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .trim()
    .toLowerCase();
  return words.charAt(0).toUpperCase() + words.slice(1);
}

function mapApiFieldErrors(fields: Record<string, string>, cartItems: CartItem[], code?: string) {
  const fieldErrors: Partial<Record<CheckoutErrorField, string>> = {};
  const itemErrors: Record<number, string[]> = {};
  const unhandled: string[] = [];
  const checkoutFields: CheckoutErrorField[] = [
    'contactName',
    'contactPhone',
    'contactEmail',
    'comment',
    'deliveryAddress',
    'orderDate',
    'slotStart',
    'cafeId',
    'deliveryZoneId',
  ];

  for (const [fieldName, serverMessage] of Object.entries(fields)) {
    const message = apiFieldMessage(fieldName, serverMessage, code);
    const itemMatch = /^items\[(\d+)\](?:\.|$)/.exec(fieldName);
    if (itemMatch) {
      const itemIndex = Number(itemMatch[1]);
      if (cartItems[itemIndex]) {
        itemErrors[itemIndex] = [...(itemErrors[itemIndex] ?? []), message];
      } else {
        unhandled.push(`${fieldLabel(fieldName)}: ${message}`);
      }
      continue;
    }

    const checkoutField = checkoutFields.find((field) => field === fieldName);
    if (checkoutField) {
      fieldErrors[checkoutField] = message;
    } else {
      unhandled.push(`${fieldLabel(fieldName)}: ${message}`);
    }
  }

  return { fieldErrors, itemErrors, unhandled };
}

export function CheckoutPage() {
  const navigate = useNavigate();
  const { country, currentCountry } = useCountry();
  const { items, clearCart } = useCart();
  const { t, locale } = useI18n();
  const today = dateKeyInTimeZone(currentCountry.timeZone);
  const maxDate = localDateToKey(addDays(dateKeyToLocalDate(today), 7));
  const [orderDate, setOrderDate] = useState(today);
  const [scenario, setScenario] = useState<Scenario>('PICKUP');
  const [details, setDetails] = useState<ContactDetails>({
    contactName: '',
    contactPhone: '',
    contactEmail: '',
    comment: '',
    deliveryAddress: '',
  });
  const [cafes, setCafes] = useState<Cafe[]>([]);
  const [selectedCafeId, setSelectedCafeId] = useState<number>();
  const [isLoadingCafes, setIsLoadingCafes] = useState(false);
  const [cafeError, setCafeError] = useState<string>();
  const [cafeRetryCount, setCafeRetryCount] = useState(0);
  const [deliveryZones, setDeliveryZones] = useState<DeliveryZone[]>([]);
  const [selectedDeliveryZoneId, setSelectedDeliveryZoneId] = useState<number>();
  const [isLoadingZones, setIsLoadingZones] = useState(false);
  const [deliveryZoneError, setDeliveryZoneError] = useState<string>();
  const [zoneRetryCount, setZoneRetryCount] = useState(0);
  const [timeSlots, setTimeSlots] = useState<TimeSlots>();
  const [isLoadingSlots, setIsLoadingSlots] = useState(false);
  const [slotsError, setSlotsError] = useState<string>();
  const [slotsRetryCount, setSlotsRetryCount] = useState(0);
  const [slotStart, setSlotStart] = useState('');
  const [isDatePickerOpen, setIsDatePickerOpen] = useState(false);
  const [quote, setQuote] = useState<CartQuote>();
  const [isLoadingQuote, setIsLoadingQuote] = useState(false);
  const [isSending, setIsSending] = useState(false);
  const [quoteError, setQuoteError] = useState<string>();
  const [orderError, setOrderError] = useState<string>();
  const [fieldErrors, setFieldErrors] = useState<Partial<Record<CheckoutErrorField, string>>>({});
  const [itemErrors, setItemErrors] = useState<Record<number, string[]>>({});
  const [focusFirstError, setFocusFirstError] = useState(false);
  const [isSlotFull, setIsSlotFull] = useState(false);
  const [focusSlot, setFocusSlot] = useState(false);
  const [retryCount, setRetryCount] = useState(0);
  const idempotencyKey = useRef<string>();
  const slotReloadPending = useRef(false);
  const slotSelectRef = useRef<HTMLSelectElement>(null);
  const selectedCafe = cafes.find(({ id }) => id === selectedCafeId);
  const selectedDeliveryZone = deliveryZones.find(({ id }) => id === selectedDeliveryZoneId);
  const formRef = useRef<HTMLFormElement>(null);
  const currentQuote =
    quote &&
    quote.country === country &&
    quote.scenario === scenario &&
    quote.lines.length === items.length &&
    quote.lines.every(
      (line, index) =>
        line.productId === items[index].productId && line.quantity === items[index].quantity,
    )
      ? quote
      : undefined;
  const slotsCafeId = scenario === 'PICKUP' ? selectedCafeId : selectedDeliveryZone?.cafeId;
  const slotSelectionKey =
    scenario === 'PICKUP'
      ? `PICKUP:${selectedCafeId ?? ''}`
      : `DELIVERY:${selectedDeliveryZoneId ?? ''}:${slotsCafeId ?? ''}`;

  useEffect(() => {
    setScenario('PICKUP');
    setSelectedCafeId(undefined);
    setSelectedDeliveryZoneId(undefined);
    setOrderDate(dateKeyInTimeZone(currentCountry.timeZone));
    setSlotStart('');
    setIsSlotFull(false);
    setFieldErrors({});
    setItemErrors({});
    setOrderError(undefined);
    setDetails((current) => ({ ...current, deliveryAddress: '' }));
    idempotencyKey.current = undefined;
  }, [country, currentCountry.timeZone]);

  useEffect(() => {
    if (!focusFirstError) return;
    formRef.current
      ?.querySelector<HTMLElement>('[aria-invalid="true"], [data-invalid="true"]')
      ?.focus();
    setFocusFirstError(false);
  }, [fieldErrors, focusFirstError, itemErrors]);

  // After "slot full", focus returns to the slot select once the reloaded slots are shown.
  useEffect(() => {
    if (!focusSlot || isLoadingSlots) return;
    slotSelectRef.current?.focus();
    setFocusSlot(false);
  }, [focusSlot, isLoadingSlots, timeSlots]);

  useEffect(() => {
    let active = true;
    setDeliveryZones([]);
    setSelectedDeliveryZoneId(undefined);
    setDeliveryZoneError(undefined);
    setIsLoadingZones(true);

    loadDeliveryZones(country)
      .then((result) => {
        if (!active) return;
        if (result.length === 0) {
          setDeliveryZoneError(t('checkout.noZones'));
          return;
        }
        setDeliveryZones(result);
        setSelectedDeliveryZoneId(result[0].id);
      })
      .catch((requestError: unknown) => {
        if (active) {
          setDeliveryZoneError(guestErrorMessage(requestError, t('checkout.zonesFallback')));
        }
      })
      .finally(() => {
        if (active) setIsLoadingZones(false);
      });

    return () => {
      active = false;
    };
  }, [country, zoneRetryCount, t]);

  useEffect(() => {
    let active = true;
    setCafes([]);
    setSelectedCafeId(undefined);
    setCafeError(undefined);
    setIsLoadingCafes(true);

    loadCafes(country)
      .then((result) => {
        if (!active) return;
        if (result.length === 0) {
          setCafeError(t('checkout.noCafes'));
          return;
        }
        setCafes(result);
        setSelectedCafeId(result[0].id);
      })
      .catch((requestError: unknown) => {
        if (active) {
          setCafeError(guestErrorMessage(requestError, t('checkout.cafesFallback')));
        }
      })
      .finally(() => {
        if (active) setIsLoadingCafes(false);
      });

    return () => {
      active = false;
    };
  }, [country, cafeRetryCount, t]);

  useEffect(() => {
    let active = true;
    setTimeSlots(undefined);
    setSlotsError(undefined);

    if (slotsCafeId === undefined) {
      setIsLoadingSlots(false);
      return () => {
        active = false;
      };
    }

    setIsLoadingSlots(true);
    loadTimeSlots(slotsCafeId, orderDate)
      .then((result) => {
        if (result.cafeId !== slotsCafeId || result.date !== orderDate) {
          throw new Error('Time slots API returned slots for a different café or date.');
        }
        if (active) setTimeSlots(result);
      })
      .catch((requestError: unknown) => {
        if (active) {
          setSlotsError(guestErrorMessage(requestError, t('checkout.slotsFallback')));
        }
      })
      .finally(() => {
        if (!active) return;
        setIsLoadingSlots(false);
        if (slotReloadPending.current) {
          slotReloadPending.current = false;
          setFocusSlot(true);
        }
      });

    return () => {
      active = false;
    };
  }, [orderDate, slotSelectionKey, slotsCafeId, slotsRetryCount, t]);

  useEffect(() => {
    let active = true;
    setQuote(undefined);
    setQuoteError(undefined);
    setItemErrors({});

    if (scenario === 'DELIVERY' && !selectedDeliveryZoneId) {
      setIsLoadingQuote(false);
      return () => {
        active = false;
      };
    }

    if (items.length === 0) {
      setIsLoadingQuote(false);
      return () => {
        active = false;
      };
    }

    setIsLoadingQuote(true);
    quoteCart({
      country,
      scenario,
      ...(scenario === 'DELIVERY' ? { deliveryZoneId: selectedDeliveryZoneId } : {}),
      items: items.map(({ productId, quantity, modifierIds }) => ({
        productId,
        quantity,
        modifierIds,
      })),
    })
      .then((result) => {
        if (active) setQuote(result);
      })
      .catch((requestError: unknown) => {
        if (active) {
          if (
            requestError instanceof ApiRequestError &&
            Object.keys(requestError.fields).length > 0
          ) {
            const mappedErrors = mapApiFieldErrors(requestError.fields, items, requestError.code);
            setItemErrors(mappedErrors.itemErrors);
            if (Object.keys(mappedErrors.itemErrors).length > 0) setFocusFirstError(true);
          }
          setQuoteError(guestErrorMessage(requestError, t('cart.pricesFallback')));
        }
      })
      .finally(() => {
        if (active) setIsLoadingQuote(false);
      });

    return () => {
      active = false;
    };
  }, [country, items, retryCount, scenario, selectedDeliveryZoneId, t]);

  function updateField(field: ContactField, value: string) {
    setDetails((current) => ({ ...current, [field]: value }));
    setFieldErrors((current) => ({ ...current, [field]: undefined }));
    setOrderError(undefined);
    idempotencyKey.current = undefined;
  }

  async function submitOrder(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const errors = validateContact(details, scenario, orderDate, today);
    if (!slotStart) errors.slotStart = t('validation.slot');
    if (scenario === 'PICKUP' && !selectedCafeId) {
      errors.cafeId = t('validation.cafe');
    }
    if (scenario === 'DELIVERY' && !selectedDeliveryZoneId) {
      errors.deliveryZoneId = t('validation.zone');
    }
    setFieldErrors(errors);
    setItemErrors({});
    setOrderError(undefined);
    if (Object.keys(errors).length > 0) {
      setFocusFirstError(true);
      return;
    }
    if (!currentQuote || isLoadingQuote) {
      setOrderError(t('checkout.waitTotal'));
      return;
    }

    setIsSending(true);
    try {
      idempotencyKey.current ??= createIdempotencyKey();
      const order = await placeOrder(
        {
          country,
          orderDate,
          scenario,
          cafeId: scenario === 'PICKUP' ? (selectedCafeId ?? null) : null,
          deliveryZoneId: scenario === 'DELIVERY' ? (selectedDeliveryZoneId ?? null) : null,
          deliveryAddress: scenario === 'DELIVERY' ? details.deliveryAddress.trim() : null,
          slotStart,
          contactName: details.contactName.trim(),
          contactPhone: details.contactPhone.replace(/\s/g, ''),
          contactEmail: details.contactEmail.trim() || null,
          comment: details.comment.trim() || null,
          expectedTotal: currentQuote.total,
          items: items.map(({ productId, quantity, modifierIds }) => ({
            productId,
            quantity,
            modifierIds,
          })),
        },
        idempotencyKey.current,
      );
      clearCart();
      navigate('/checkout/success', { state: { order } });
    } catch (requestError: unknown) {
      if (requestError instanceof ApiRequestError && requestError.code === 'PRICE_CHANGED') {
        idempotencyKey.current = undefined;
        setQuote(undefined);
        setOrderError(t('checkout.priceChanged'));
        setRetryCount((count) => count + 1);
      } else if (requestError instanceof ApiRequestError && requestError.code === 'SLOT_FULL') {
        const message = t('checkout.slotJustFull');
        setSlotStart('');
        setIsSlotFull(true);
        setFieldErrors((current) => ({ ...current, slotStart: message }));
        setOrderError(message);
        slotReloadPending.current = true;
        setSlotsRetryCount((count) => count + 1);
      } else if (
        requestError instanceof ApiRequestError &&
        Object.keys(requestError.fields).length > 0
      ) {
        const mappedErrors = mapApiFieldErrors(requestError.fields, items, requestError.code);
        setFieldErrors(mappedErrors.fieldErrors);
        setItemErrors(mappedErrors.itemErrors);
        setOrderError(
          mappedErrors.unhandled.length > 0
            ? t('checkout.reviewDetails', { list: mappedErrors.unhandled.join('; ') })
            : undefined,
        );
        if (
          Object.keys(mappedErrors.fieldErrors).length > 0 ||
          Object.keys(mappedErrors.itemErrors).length > 0
        ) {
          setFocusFirstError(true);
        }
      } else {
        setOrderError(guestErrorMessage(requestError, t('checkout.placeFallback')));
      }
    } finally {
      setIsSending(false);
    }
  }

  if (items.length === 0) {
    return (
      <PagePlaceholder
        eyebrow={t('checkout.eyebrow')}
        title={t('checkout.title')}
        description={t('checkout.emptyDescription')}
        symbol="♡"
        panelTitle={t('checkout.emptyPanelTitle')}
        panelDescription={t('checkout.emptyPanelText')}
        actionLabel={t('checkout.explore')}
        actionTo="/menu"
      />
    );
  }

  const fieldErrorMessages = Object.entries(fieldErrors).filter(
    (entry): entry is [string, string] => Boolean(entry[1]),
  );

  return (
    <section className="checkout-page" aria-labelledby="checkout-title">
      <p className="eyebrow">{t('checkout.eyebrow')}</p>
      <h1 id="checkout-title">{t('checkout.title')}</h1>
      <p className="checkout-page__description">{t('checkout.description')}</p>

      {orderError && <ErrorMessage title={t('checkout.orderNotPlaced')}>{orderError}</ErrorMessage>}
      {quoteError && (
        <div className="checkout-page__error">
          <ErrorMessage title={t('checkout.pricesError')}>{quoteError}</ErrorMessage>
          <Button variant="outline" onClick={() => setRetryCount((count) => count + 1)}>
            {t('common.retry')}
          </Button>
        </div>
      )}
      {isLoadingQuote && <Loader label={t('checkout.loadingQuote')} />}

      <form ref={formRef} className="checkout-form" onSubmit={submitOrder} noValidate>
        <div className="checkout-form__fields">
          <section className="checkout-section" aria-labelledby="fulfillment-title">
            <h2 id="fulfillment-title">{t('checkout.howTitle')}</h2>
            <RadioGroup.Root
              className="checkout-scenarios"
              aria-label={t('checkout.orderType')}
              value={scenario}
              onValueChange={(value: Scenario) => {
                setScenario(value);
                setQuote(undefined);
                setSlotStart('');
                setOrderError(undefined);
                setFieldErrors({});
                setItemErrors({});
                idempotencyKey.current = undefined;
              }}
            >
              <label className="checkout-scenario">
                <RadioGroup.Item className="checkout-scenario__control" value="PICKUP" id="pickup">
                  <RadioGroup.Indicator className="checkout-scenario__indicator" />
                </RadioGroup.Item>
                <span>{t('checkout.pickup')}</span>
              </label>
              <label className="checkout-scenario">
                <RadioGroup.Item
                  className="checkout-scenario__control"
                  value="DELIVERY"
                  id="delivery"
                >
                  <RadioGroup.Indicator className="checkout-scenario__indicator" />
                </RadioGroup.Item>
                <span>{t('checkout.delivery')}</span>
              </label>
            </RadioGroup.Root>

            {scenario === 'DELIVERY' && (
              <div className="input">
                <label className="input__label" htmlFor="checkout-zone">
                  {t('checkout.zone')}
                </label>
                {isLoadingZones ? (
                  <Loader label={t('checkout.loadingZones')} />
                ) : deliveryZoneError ? (
                  <div className="checkout-page__error">
                    <ErrorMessage title={t('checkout.zonesError')}>
                      {deliveryZoneError}
                    </ErrorMessage>
                    <Button
                      type="button"
                      variant="outline"
                      onClick={() => setZoneRetryCount((count) => count + 1)}
                    >
                      {t('common.retry')}
                    </Button>
                  </div>
                ) : (
                  <>
                    <select
                      className={`input__control${fieldErrors.deliveryZoneId ? ' input__control--error' : ''}`}
                      id="checkout-zone"
                      value={selectedDeliveryZoneId ?? ''}
                      aria-invalid={Boolean(fieldErrors.deliveryZoneId) || undefined}
                      aria-describedby={
                        [
                          selectedDeliveryZone ? 'checkout-zone-details' : undefined,
                          fieldErrors.deliveryZoneId ? 'checkout-zone-error' : undefined,
                        ]
                          .filter(Boolean)
                          .join(' ') || undefined
                      }
                      onChange={(event) => {
                        const nextZoneId = Number(event.target.value);
                        setSelectedDeliveryZoneId(nextZoneId);
                        setQuote(undefined);
                        setSlotStart('');
                        setFieldErrors((current) => ({ ...current, deliveryZoneId: undefined }));
                        setOrderError(undefined);
                        idempotencyKey.current = undefined;
                      }}
                    >
                      {deliveryZones.map((zone) => (
                        <option key={zone.id} value={zone.id}>
                          {zoneName(zone.name)}
                        </option>
                      ))}
                    </select>
                    {selectedDeliveryZone && (
                      <p
                        id="checkout-zone-details"
                        className="input__message checkout-zone__details"
                      >
                        {t('checkout.zoneFee', {
                          fee: formatPrice(selectedDeliveryZone.fee, selectedDeliveryZone.currency),
                          minutes: selectedDeliveryZone.deliveryMinutes,
                        })}
                      </p>
                    )}
                    {fieldErrors.deliveryZoneId && (
                      <span
                        id="checkout-zone-error"
                        className="input__message input__message--error"
                        role="alert"
                      >
                        {fieldErrors.deliveryZoneId}
                      </span>
                    )}
                  </>
                )}
              </div>
            )}

            {scenario === 'PICKUP' && (
              <div className="input">
                <label className="input__label" htmlFor="checkout-cafe">
                  {t('checkout.cafe')}
                </label>
                {isLoadingCafes ? (
                  <Loader label={t('checkout.loadingCafes')} />
                ) : cafeError ? (
                  <div className="checkout-page__error">
                    <ErrorMessage title={t('checkout.cafesError')}>{cafeError}</ErrorMessage>
                    <Button
                      type="button"
                      variant="outline"
                      onClick={() => setCafeRetryCount((count) => count + 1)}
                    >
                      {t('common.retry')}
                    </Button>
                  </div>
                ) : (
                  <>
                    <select
                      className={`input__control${fieldErrors.cafeId ? ' input__control--error' : ''}`}
                      id="checkout-cafe"
                      value={selectedCafeId ?? ''}
                      aria-invalid={Boolean(fieldErrors.cafeId) || undefined}
                      aria-describedby={
                        [
                          selectedCafe ? 'checkout-cafe-details' : undefined,
                          fieldErrors.cafeId ? 'checkout-cafe-error' : undefined,
                        ]
                          .filter(Boolean)
                          .join(' ') || undefined
                      }
                      onChange={(event) => {
                        const nextCafeId = Number(event.target.value);
                        setSelectedCafeId(nextCafeId);
                        setSlotStart('');
                        setFieldErrors((current) => ({ ...current, cafeId: undefined }));
                        setOrderError(undefined);
                        idempotencyKey.current = undefined;
                      }}
                    >
                      {cafes.map((cafe) => (
                        <option key={cafe.id} value={cafe.id}>
                          {cafeName(cafe.name)}
                        </option>
                      ))}
                    </select>
                    {selectedCafe && (
                      <p
                        id="checkout-cafe-details"
                        className="input__message checkout-cafe__details"
                      >
                        {t('checkout.cafeDetails', {
                          address: cafeAddress(selectedCafe.address),
                          opens: selectedCafe.opensAt,
                          closes: selectedCafe.closesAt,
                        })}
                      </p>
                    )}
                    {fieldErrors.cafeId && (
                      <span
                        id="checkout-cafe-error"
                        className="input__message input__message--error"
                        role="alert"
                      >
                        {fieldErrors.cafeId}
                      </span>
                    )}
                  </>
                )}
              </div>
            )}

            {scenario === 'DELIVERY' && (
              <Input
                label={t('checkout.address')}
                value={details.deliveryAddress}
                maxLength={200}
                error={fieldErrors.deliveryAddress}
                onChange={(event) => updateField('deliveryAddress', event.target.value)}
              />
            )}

            <div className="checkout-date">
              <span id="checkout-date-label" className="input__label">
                {t('checkout.orderDate')}
              </span>
              <button
                type="button"
                className="input__control checkout-date__trigger"
                aria-labelledby="checkout-date-label checkout-date-value"
                aria-controls="checkout-date-picker"
                aria-expanded={isDatePickerOpen}
                data-invalid={Boolean(fieldErrors.orderDate) || undefined}
                aria-describedby={fieldErrors.orderDate ? 'checkout-date-error' : undefined}
                onClick={() => setIsDatePickerOpen((open) => !open)}
              >
                <span id="checkout-date-value">{formatOrderDate(orderDate)}</span>
              </button>
              {fieldErrors.orderDate && (
                <span
                  id="checkout-date-error"
                  className="input__message input__message--error"
                  role="alert"
                >
                  {fieldErrors.orderDate}
                </span>
              )}
              {isDatePickerOpen && (
                <div id="checkout-date-picker" className="checkout-date__panel">
                  <DayPicker
                    mode="single"
                    locale={DAY_PICKER_LOCALE[locale]}
                    selected={dateKeyToLocalDate(orderDate)}
                    defaultMonth={dateKeyToLocalDate(orderDate)}
                    startMonth={dateKeyToLocalDate(today)}
                    endMonth={dateKeyToLocalDate(maxDate)}
                    disabled={(date) => {
                      const key = localDateToKey(date);
                      return key < today || key > maxDate;
                    }}
                    onSelect={(date) => {
                      if (date) {
                        setOrderDate(localDateToKey(date));
                        setSlotStart('');
                        setFieldErrors((current) => ({ ...current, orderDate: undefined }));
                        setOrderError(undefined);
                        idempotencyKey.current = undefined;
                        setIsDatePickerOpen(false);
                      }
                    }}
                  />
                </div>
              )}
            </div>

            <div className="input">
              <label className="input__label" htmlFor="checkout-slot">
                {t('checkout.slot')}
              </label>
              {isLoadingSlots ? (
                <Loader label={t('checkout.loadingSlots')} />
              ) : slotsError ? (
                <div className="checkout-page__error">
                  <ErrorMessage title={t('checkout.slotsError')}>{slotsError}</ErrorMessage>
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() => setSlotsRetryCount((count) => count + 1)}
                  >
                    {t('common.retry')}
                  </Button>
                </div>
              ) : (
                <select
                  className={`input__control${fieldErrors.slotStart ? ' input__control--error' : ''}`}
                  id="checkout-slot"
                  ref={slotSelectRef}
                  value={slotStart}
                  disabled={!timeSlots}
                  aria-invalid={Boolean(fieldErrors.slotStart) || undefined}
                  aria-describedby={
                    [
                      timeSlots ? 'checkout-slot-details' : undefined,
                      fieldErrors.slotStart ? 'checkout-slot-error' : undefined,
                    ]
                      .filter(Boolean)
                      .join(' ') || undefined
                  }
                  onChange={(event) => {
                    setSlotStart(event.target.value);
                    if (event.target.value) setIsSlotFull(false);
                    setFieldErrors((current) => ({ ...current, slotStart: undefined }));
                    setOrderError(undefined);
                    idempotencyKey.current = undefined;
                  }}
                >
                  <option value="">{t('checkout.chooseTime')}</option>
                  {timeSlots?.slots.map((slot) => {
                    const unavailableLabel =
                      slot.reason === 'FULL'
                        ? ` — ${t('checkout.slotFull')}`
                        : slot.reason === 'PAST'
                          ? ` — ${t('checkout.slotPast')}`
                          : '';
                    return (
                      <option key={slot.start} value={slot.start} disabled={!slot.available}>
                        {slot.start}–{slot.end}
                        {unavailableLabel}
                      </option>
                    );
                  })}
                </select>
              )}
              {timeSlots && !slotsError && (
                <p id="checkout-slot-details" className="input__message checkout-slot__details">
                  {t('checkout.localTime')}
                </p>
              )}
              {fieldErrors.slotStart && (
                <span id="checkout-slot-error" className="input__message input__message--error">
                  {fieldErrors.slotStart}
                </span>
              )}
            </div>
          </section>

          <section className="checkout-section" aria-labelledby="contact-title">
            <h2 id="contact-title">{t('fields.contactTitle')}</h2>
            <Input
              label={t('fields.name')}
              value={details.contactName}
              maxLength={100}
              error={fieldErrors.contactName}
              onChange={(event) => updateField('contactName', event.target.value)}
            />
            <Input
              label={t('fields.phone')}
              hint={t('fields.phoneHint')}
              type="tel"
              autoComplete="tel"
              value={details.contactPhone}
              error={fieldErrors.contactPhone}
              onChange={(event) => updateField('contactPhone', event.target.value)}
            />
            <Input
              label={t('fields.emailOptional')}
              type="email"
              autoComplete="email"
              value={details.contactEmail}
              maxLength={254}
              error={fieldErrors.contactEmail}
              onChange={(event) => updateField('contactEmail', event.target.value)}
            />
            <div className="input">
              <label className="input__label" htmlFor="checkout-comment">
                {t('fields.commentOptional')}
              </label>
              <textarea
                className="input__control checkout-comment"
                id="checkout-comment"
                value={details.comment}
                maxLength={300}
                aria-invalid={Boolean(fieldErrors.comment) || undefined}
                aria-describedby={fieldErrors.comment ? 'checkout-comment-error' : undefined}
                onChange={(event) => updateField('comment', event.target.value)}
              />
              {fieldErrors.comment && (
                <span
                  id="checkout-comment-error"
                  className="input__message input__message--error"
                  role="alert"
                >
                  {fieldErrors.comment}
                </span>
              )}
            </div>
          </section>
        </div>

        <aside className="checkout-form__summary" aria-label={t('checkout.summary')}>
          {Object.entries(itemErrors).map(([index, messages]) => {
            const item = items[Number(index)];
            if (!item) return null;
            const errorId = `checkout-item-${index}-error`;
            return (
              <div
                key={index}
                className="checkout-line-errors"
                role="group"
                aria-label={t('checkout.lineErrors', {
                  name: productName(item.name),
                  quantity: item.quantity,
                })}
                data-invalid="true"
                aria-describedby={errorId}
                tabIndex={-1}
              >
                <strong>
                  {productName(item.name)} × {item.quantity}
                </strong>
                {item.modifiers.length > 0 && (
                  <p>{item.modifiers.map(({ name }) => modifierName(name)).join(', ')}</p>
                )}
                <ul id={errorId}>
                  {messages.map((message, messageIndex) => (
                    <li key={`${message}-${messageIndex}`}>{message}</li>
                  ))}
                </ul>
              </div>
            );
          })}
          {currentQuote && <TotalsBlock quote={currentQuote} />}
          {fieldErrorMessages.length > 0 && (
            <div className="checkout-error-summary" role="alert">
              <strong>{t('checkout.checkDetails')}</strong>
              <ul>
                {fieldErrorMessages.map(([field, message]) => (
                  <li key={field}>{message}</li>
                ))}
              </ul>
            </div>
          )}
          <Button
            type="submit"
            size="large"
            loading={isSending}
            disabled={
              isSending ||
              isSlotFull ||
              !currentQuote ||
              isLoadingQuote ||
              (scenario === 'PICKUP' &&
                (isLoadingCafes || Boolean(cafeError) || !selectedCafeId)) ||
              (scenario === 'DELIVERY' &&
                (isLoadingZones || Boolean(deliveryZoneError) || !selectedDeliveryZoneId)) ||
              isLoadingSlots ||
              Boolean(slotsError) ||
              !timeSlots
            }
          >
            {t('checkout.placeOrder')}
          </Button>
          <Link className="checkout-form__back" to="/cart">
            {t('checkout.backToCart')}
          </Link>
        </aside>
      </form>
    </section>
  );
}
