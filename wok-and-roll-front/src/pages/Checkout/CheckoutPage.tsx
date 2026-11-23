import { useEffect, useRef, useState, type FormEvent } from 'react';
import * as RadioGroup from '@radix-ui/react-radio-group';
import { DayPicker } from 'react-day-picker';
import 'react-day-picker/style.css';
import { Link, useNavigate } from 'react-router-dom';
import { Button, ErrorMessage, Input, Loader, TotalsBlock } from '../../components/common';
import { useCart, type CartItem } from '../../context/CartContext';
import { useCountry } from '../../context/CountryContext';
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

function dateKeyInTimeZone(timeZone: string): string {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(new Date());
  const part = (type: string) => parts.find((value) => value.type === type)?.value ?? '';
  return `${part('year')}-${part('month')}-${part('day')}`;
}

function dateKeyToLocalDate(value: string): Date {
  const [year, month, day] = value.split('-').map(Number);
  return new Date(year, month - 1, day, 12);
}

function localDateToKey(value: Date): string {
  const year = value.getFullYear();
  const month = String(value.getMonth() + 1).padStart(2, '0');
  const day = String(value.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function addDays(value: Date, count: number): Date {
  const result = new Date(value);
  result.setDate(result.getDate() + count);
  return result;
}

function formatOrderDate(value: string): string {
  return new Intl.DateTimeFormat('en-GB', {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  }).format(dateKeyToLocalDate(value));
}

function createIdempotencyKey(): string {
  if (typeof crypto.randomUUID === 'function') return crypto.randomUUID();

  const bytes = crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('');

  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

function validateContact(details: ContactDetails, scenario: Scenario, orderDate: string, today: string) {
  const errors: Partial<Record<CheckoutErrorField, string>> = {};
  const name = details.contactName.trim();
  const phone = details.contactPhone.replace(/\s/g, '');
  const email = details.contactEmail.trim();
  const address = details.deliveryAddress.trim();

  if (name.length < 2 || name.length > 100) {
    errors.contactName = 'Enter a name from 2 to 100 characters.';
  }
  if (!/^\+\d{8,15}$/.test(phone)) {
    errors.contactPhone = 'Enter a phone number with + and 8 to 15 digits.';
  }
  if (email && (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) || email.length > 254)) {
    errors.contactEmail = 'Enter a valid email address.';
  }
  if (scenario === 'DELIVERY' && (address.length < 5 || address.length > 200)) {
    errors.deliveryAddress = 'Enter an address from 5 to 200 characters.';
  }
  if (details.comment.length > 300) errors.comment = 'Comment must be 300 characters or fewer.';
  if (orderDate < today || orderDate > localDateToKey(addDays(dateKeyToLocalDate(today), 7))) {
    errors.orderDate = 'Choose a date from today through the next 7 days.';
  }
  return errors;
}

const FIELD_LABELS: Record<string, string> = {
  expectedTotal: 'Order total',
  country: 'Country',
  scenario: 'Order type',
  items: 'Items',
  'Idempotency-Key': 'Order request',
};

function fieldLabel(fieldName: string): string {
  const known = FIELD_LABELS[fieldName];
  if (known) return known;

  const words = fieldName
    .replace(/^items\[(\d+)\]/, (_, index: string) => `item ${Number(index) + 1}`)
    .replace(/[._]/g, ' ')
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .trim()
    .toLowerCase();
  return words.charAt(0).toUpperCase() + words.slice(1);
}

function mapApiFieldErrors(fields: Record<string, string>, cartItems: CartItem[]) {
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

  for (const [fieldName, message] of Object.entries(fields)) {
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
  const currentQuote = quote
    && quote.country === country
    && quote.scenario === scenario
    && quote.lines.length === items.length
    && quote.lines.every((line, index) => (
      line.productId === items[index].productId
      && line.quantity === items[index].quantity
    ))
    ? quote
    : undefined;
  const slotsCafeId = scenario === 'PICKUP' ? selectedCafeId : selectedDeliveryZone?.cafeId;
  const slotSelectionKey = scenario === 'PICKUP'
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
    formRef.current?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus();
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
          setDeliveryZoneError('No delivery zones are available in this country.');
          return;
        }
        setDeliveryZones(result);
        setSelectedDeliveryZoneId(result[0].id);
      })
      .catch((requestError: unknown) => {
        if (active) {
          setDeliveryZoneError(guestErrorMessage(
            requestError,
            'We could not load delivery zones. Please try again.',
          ));
        }
      })
      .finally(() => {
        if (active) setIsLoadingZones(false);
      });

    return () => {
      active = false;
    };
  }, [country, zoneRetryCount]);

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
          setCafeError('No pickup cafés are available in this country.');
          return;
        }
        setCafes(result);
        setSelectedCafeId(result[0].id);
      })
      .catch((requestError: unknown) => {
        if (active) {
          setCafeError(guestErrorMessage(
            requestError,
            'We could not load pickup cafés. Please try again.',
          ));
        }
      })
      .finally(() => {
        if (active) setIsLoadingCafes(false);
      });

    return () => {
      active = false;
    };
  }, [country, cafeRetryCount]);

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
          setSlotsError(guestErrorMessage(
            requestError,
            'We could not load time slots. Please try again.',
          ));
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
  }, [orderDate, slotSelectionKey, slotsCafeId, slotsRetryCount]);

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
          if (requestError instanceof ApiRequestError && Object.keys(requestError.fields).length > 0) {
            const mappedErrors = mapApiFieldErrors(requestError.fields, items);
            setItemErrors(mappedErrors.itemErrors);
            if (Object.keys(mappedErrors.itemErrors).length > 0) setFocusFirstError(true);
          }
          setQuoteError(guestErrorMessage(
            requestError,
            'We could not get the current prices. Please try again.',
          ));
        }
      })
      .finally(() => {
        if (active) setIsLoadingQuote(false);
      });

    return () => {
      active = false;
    };
  }, [country, items, retryCount, scenario, selectedDeliveryZoneId]);

  function updateField(field: ContactField, value: string) {
    setDetails((current) => ({ ...current, [field]: value }));
    setFieldErrors((current) => ({ ...current, [field]: undefined }));
    setOrderError(undefined);
    idempotencyKey.current = undefined;
  }

  async function submitOrder(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const errors = validateContact(details, scenario, orderDate, today);
    if (!slotStart) errors.slotStart = 'Choose an available time slot.';
    if (scenario === 'PICKUP' && !selectedCafeId) {
      errors.cafeId = 'Choose a pickup café.';
    }
    if (scenario === 'DELIVERY' && !selectedDeliveryZoneId) {
      errors.deliveryZoneId = 'Choose a delivery zone.';
    }
    setFieldErrors(errors);
    setItemErrors({});
    setOrderError(undefined);
    if (Object.keys(errors).length > 0) {
      setFocusFirstError(true);
      return;
    }
    if (!currentQuote || isLoadingQuote) {
      setOrderError('Wait for the latest order total before placing your order.');
      return;
    }

    setIsSending(true);
    try {
      idempotencyKey.current ??= createIdempotencyKey();
      const order = await placeOrder({
        country,
        orderDate,
        scenario,
        cafeId: scenario === 'PICKUP' ? selectedCafeId ?? null : null,
        deliveryZoneId: scenario === 'DELIVERY' ? selectedDeliveryZoneId ?? null : null,
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
      }, idempotencyKey.current);
      clearCart();
      navigate('/checkout/success', { state: { order } });
    } catch (requestError: unknown) {
      if (requestError instanceof ApiRequestError && requestError.code === 'PRICE_CHANGED') {
        idempotencyKey.current = undefined;
        setQuote(undefined);
        setOrderError('The price changed. We refreshed your quote; review the new total and try again.');
        setRetryCount((count) => count + 1);
      } else if (requestError instanceof ApiRequestError && requestError.code === 'SLOT_FULL') {
        const message = 'That time slot just filled up. Choose another available time.';
        setSlotStart('');
        setIsSlotFull(true);
        setFieldErrors((current) => ({ ...current, slotStart: message }));
        setOrderError(message);
        slotReloadPending.current = true;
        setSlotsRetryCount((count) => count + 1);
      } else if (requestError instanceof ApiRequestError && Object.keys(requestError.fields).length > 0) {
        const mappedErrors = mapApiFieldErrors(requestError.fields, items);
        setFieldErrors(mappedErrors.fieldErrors);
        setItemErrors(mappedErrors.itemErrors);
        setOrderError(mappedErrors.unhandled.length > 0
          ? `Please review these order details: ${mappedErrors.unhandled.join('; ')}`
          : undefined);
        if (
          Object.keys(mappedErrors.fieldErrors).length > 0
          || Object.keys(mappedErrors.itemErrors).length > 0
        ) {
          setFocusFirstError(true);
        }
      } else {
        setOrderError(guestErrorMessage(requestError, 'We could not place your order. Please try again.'));
      }
    } finally {
      setIsSending(false);
    }
  }

  if (items.length === 0) {
    return (
      <PagePlaceholder
        eyebrow="Almost there"
        title="Checkout"
        description="A few more details and your order will be on its way."
        symbol="♡"
        panelTitle="Start with something tasty"
        panelDescription="Add an item to your cart before heading to checkout."
        actionLabel="Explore the menu"
        actionTo="/menu"
      />
    );
  }

  const fieldErrorMessages = Object.entries(fieldErrors).filter(
    (entry): entry is [string, string] => Boolean(entry[1]),
  );

  return (
    <section className="checkout-page" aria-labelledby="checkout-title">
      <p className="eyebrow">Almost there</p>
      <h1 id="checkout-title">Checkout</h1>
      <p className="checkout-page__description">Choose a time and tell us how to reach you.</p>

      {orderError && <ErrorMessage title="Order not placed">{orderError}</ErrorMessage>}
      {quoteError && (
        <div className="checkout-page__error">
          <ErrorMessage title="Could not update order prices">{quoteError}</ErrorMessage>
          <Button variant="outline" onClick={() => setRetryCount((count) => count + 1)}>
            Retry
          </Button>
        </div>
      )}
      {isLoadingQuote && <Loader label="Getting current cart prices" />}

      <form ref={formRef} className="checkout-form" onSubmit={submitOrder} noValidate>
        <div className="checkout-form__fields">
          <section className="checkout-section" aria-labelledby="fulfillment-title">
            <h2 id="fulfillment-title">How would you like your order?</h2>
            <RadioGroup.Root
              className="checkout-scenarios"
              aria-label="Order type"
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
                <span>Pickup</span>
              </label>
              <label className="checkout-scenario">
                <RadioGroup.Item className="checkout-scenario__control" value="DELIVERY" id="delivery">
                  <RadioGroup.Indicator className="checkout-scenario__indicator" />
                </RadioGroup.Item>
                <span>Delivery</span>
              </label>
            </RadioGroup.Root>

            {scenario === 'DELIVERY' && (
              <div className="input">
                <label className="input__label" htmlFor="checkout-zone">Delivery zone</label>
                {isLoadingZones ? (
                  <Loader label="Loading delivery zones" />
                ) : deliveryZoneError ? (
                  <div className="checkout-page__error">
                    <ErrorMessage title="Could not load delivery zones">
                      {deliveryZoneError}
                    </ErrorMessage>
                    <Button
                      type="button"
                      variant="outline"
                      onClick={() => setZoneRetryCount((count) => count + 1)}
                    >
                      Retry
                    </Button>
                  </div>
                ) : (
                  <>
                    <select
                      className={`input__control${fieldErrors.deliveryZoneId ? ' input__control--error' : ''}`}
                      id="checkout-zone"
                      value={selectedDeliveryZoneId ?? ''}
                      aria-invalid={Boolean(fieldErrors.deliveryZoneId) || undefined}
                      aria-describedby={[
                        selectedDeliveryZone ? 'checkout-zone-details' : undefined,
                        fieldErrors.deliveryZoneId ? 'checkout-zone-error' : undefined,
                      ].filter(Boolean).join(' ') || undefined}
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
                          {zone.name}
                        </option>
                      ))}
                    </select>
                    {selectedDeliveryZone && (
                      <p id="checkout-zone-details" className="input__message checkout-zone__details">
                        Delivery fee {formatPrice(selectedDeliveryZone.fee, selectedDeliveryZone.currency)}.
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
                <label className="input__label" htmlFor="checkout-cafe">Pickup café</label>
                {isLoadingCafes ? (
                  <Loader label="Loading pickup cafés" />
                ) : cafeError ? (
                  <div className="checkout-page__error">
                    <ErrorMessage title="Could not load pickup cafés">{cafeError}</ErrorMessage>
                    <Button
                      type="button"
                      variant="outline"
                      onClick={() => setCafeRetryCount((count) => count + 1)}
                    >
                      Retry
                    </Button>
                  </div>
                ) : (
                  <>
                    <select
                      className={`input__control${fieldErrors.cafeId ? ' input__control--error' : ''}`}
                      id="checkout-cafe"
                      value={selectedCafeId ?? ''}
                      aria-invalid={Boolean(fieldErrors.cafeId) || undefined}
                      aria-describedby={[
                        selectedCafe ? 'checkout-cafe-details' : undefined,
                        fieldErrors.cafeId ? 'checkout-cafe-error' : undefined,
                      ].filter(Boolean).join(' ') || undefined}
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
                          {cafe.name}
                        </option>
                      ))}
                    </select>
                    {selectedCafe && (
                      <p id="checkout-cafe-details" className="input__message checkout-cafe__details">
                        {selectedCafe.address}. Open {selectedCafe.opensAt}–{selectedCafe.closesAt}.
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
                label="Delivery address"
                value={details.deliveryAddress}
                maxLength={200}
                error={fieldErrors.deliveryAddress}
                onChange={(event) => updateField('deliveryAddress', event.target.value)}
              />
            )}

            <div className="checkout-date">
              <span id="checkout-date-label" className="input__label">Order date</span>
              <button
                type="button"
                className="input__control checkout-date__trigger"
                aria-labelledby="checkout-date-label checkout-date-value"
                aria-controls="checkout-date-picker"
                aria-expanded={isDatePickerOpen}
                aria-invalid={Boolean(fieldErrors.orderDate) || undefined}
                aria-describedby={fieldErrors.orderDate ? 'checkout-date-error' : undefined}
                onClick={() => setIsDatePickerOpen((open) => !open)}
              >
                <span id="checkout-date-value">{formatOrderDate(orderDate)}</span>
              </button>
              {fieldErrors.orderDate && (
                <span id="checkout-date-error" className="input__message input__message--error" role="alert">
                  {fieldErrors.orderDate}
                </span>
              )}
              {isDatePickerOpen && (
                <div id="checkout-date-picker" className="checkout-date__panel">
                  <DayPicker
                    mode="single"
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
              <label className="input__label" htmlFor="checkout-slot">Time slot</label>
              {isLoadingSlots ? (
                <Loader label="Loading time slots" />
              ) : slotsError ? (
                <div className="checkout-page__error">
                  <ErrorMessage title="Could not load time slots">{slotsError}</ErrorMessage>
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() => setSlotsRetryCount((count) => count + 1)}
                  >
                    Retry
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
                  aria-describedby={[
                    timeSlots ? 'checkout-slot-details' : undefined,
                    fieldErrors.slotStart ? 'checkout-slot-error' : undefined,
                  ].filter(Boolean).join(' ') || undefined}
                  onChange={(event) => {
                    setSlotStart(event.target.value);
                    if (event.target.value) setIsSlotFull(false);
                    setFieldErrors((current) => ({ ...current, slotStart: undefined }));
                    setOrderError(undefined);
                    idempotencyKey.current = undefined;
                  }}
                >
                  <option value="">Choose a time</option>
                  {timeSlots?.slots.map((slot) => {
                    const unavailableLabel = slot.reason === 'FULL'
                      ? ' — Full'
                      : slot.reason === 'PAST'
                        ? ' — Past'
                        : '';
                    return (
                      <option key={slot.start} value={slot.start} disabled={!slot.available}>
                        {slot.start}–{slot.end}{unavailableLabel}
                      </option>
                    );
                  })}
                </select>
              )}
              {timeSlots && !slotsError && (
                <p id="checkout-slot-details" className="input__message checkout-slot__details">
                  Times are in the café&apos;s local time.
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
            <h2 id="contact-title">Contact details</h2>
            <Input
              label="Name"
              value={details.contactName}
              maxLength={100}
              error={fieldErrors.contactName}
              onChange={(event) => updateField('contactName', event.target.value)}
            />
            <Input
              label="Phone"
              type="tel"
              autoComplete="tel"
              value={details.contactPhone}
              error={fieldErrors.contactPhone}
              onChange={(event) => updateField('contactPhone', event.target.value)}
            />
            <Input
              label="Email (optional)"
              type="email"
              autoComplete="email"
              value={details.contactEmail}
              maxLength={254}
              error={fieldErrors.contactEmail}
              onChange={(event) => updateField('contactEmail', event.target.value)}
            />
            <div className="input">
              <label className="input__label" htmlFor="checkout-comment">
                Comment (optional)
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

        <aside className="checkout-form__summary" aria-label="Order summary">
          {Object.entries(itemErrors).map(([index, messages]) => {
            const item = items[Number(index)];
            if (!item) return null;
            const errorId = `checkout-item-${index}-error`;
            return (
              <div
                key={index}
                className="checkout-line-errors"
                role="group"
                aria-label={`Errors for ${item.name}, quantity ${item.quantity}`}
                aria-invalid="true"
                aria-describedby={errorId}
                tabIndex={-1}
              >
                <strong>{item.name} × {item.quantity}</strong>
                {item.modifiers.length > 0 && (
                  <p>{item.modifiers.map(({ name }) => name).join(', ')}</p>
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
              <strong>Check these details before you place your order</strong>
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
              isSending
              || isSlotFull
              || !currentQuote
              || isLoadingQuote
              || (scenario === 'PICKUP' && (isLoadingCafes || Boolean(cafeError) || !selectedCafeId))
              || (
                scenario === 'DELIVERY'
                && (isLoadingZones || Boolean(deliveryZoneError) || !selectedDeliveryZoneId)
              )
              || isLoadingSlots
              || Boolean(slotsError)
              || !timeSlots
            }
          >
            Place order
          </Button>
          <Link className="checkout-form__back" to="/cart">Back to cart</Link>
        </aside>
      </form>
    </section>
  );
}
