import { useEffect, useRef, useState, type FormEvent } from 'react';
import { DayPicker } from 'react-day-picker';
import 'react-day-picker/style.css';
import { Button, ErrorMessage, Input, Loader } from '../../components/common';
import { useAuth } from '../../context/AuthContext';
import { useCountry } from '../../context/CountryContext';
import { ApiRequestError, guestErrorMessage, loadCafes, type Cafe } from '../../services/api';
import { BOOKING_RULES, bookTable, type BookingResponse } from '../../services/bookingApi';
import { createIdempotencyKey } from '../../services/parse';
import {
  addDays,
  dateKeyInTimeZone,
  dateKeyToDate,
  formatDate,
  localDateKey,
  minutesNowInTimeZone,
  minutesToTime,
  timeToMinutes,
} from '../../utils/dates';

type Field =
  | 'cafeId'
  | 'date'
  | 'time'
  | 'guests'
  | 'contactName'
  | 'contactPhone'
  | 'contactEmail'
  | 'comment';

interface TimeOption {
  value: string;
  disabled: boolean;
}

/** 30-minute steps from opening to 90 minutes before closing; today only 60+ minutes ahead. */
export function bookingTimes(
  cafe: Cafe,
  date: string,
  today: string,
  nowMinutes: number,
): TimeOption[] {
  const open = timeToMinutes(cafe.opensAt);
  const last = timeToMinutes(cafe.closesAt) - BOOKING_RULES.durationMinutes;
  const options: TimeOption[] = [];
  for (let start = open; start <= last; start += BOOKING_RULES.stepMinutes) {
    options.push({
      value: minutesToTime(start),
      disabled: date === today && start < nowMinutes + BOOKING_RULES.minLeadMinutes,
    });
  }
  return options;
}

const FIELDS: Field[] = [
  'cafeId',
  'date',
  'time',
  'guests',
  'contactName',
  'contactPhone',
  'contactEmail',
  'comment',
];

/** Book a table (#183, API contract §3.10). */
export function BookingPage() {
  const { country, currentCountry } = useCountry();
  const { me } = useAuth();
  const [cafes, setCafes] = useState<Cafe[]>([]);
  const [cafeId, setCafeId] = useState<number>();
  const [cafesLoading, setCafesLoading] = useState(true);
  const [cafesError, setCafesError] = useState<string>();
  const [cafesRetry, setCafesRetry] = useState(0);
  const cafe = cafes.find(({ id }) => id === cafeId);
  const timeZone = cafe?.timeZone ?? currentCountry.timeZone;
  const today = dateKeyInTimeZone(timeZone);
  const maxDate = localDateKey(addDays(dateKeyToDate(today), BOOKING_RULES.maxDaysAhead));

  const [date, setDate] = useState(today);
  const [pickerOpen, setPickerOpen] = useState(false);
  const [time, setTime] = useState('');
  const [guests, setGuests] = useState('2');
  const [contact, setContact] = useState({
    contactName: '',
    contactPhone: '',
    contactEmail: '',
    comment: '',
  });
  const [errors, setErrors] = useState<Partial<Record<Field, string>>>({});
  const [formError, setFormError] = useState<string>();
  const [sending, setSending] = useState(false);
  const [booking, setBooking] = useState<BookingResponse>();
  const [focusError, setFocusError] = useState(false);
  const formRef = useRef<HTMLFormElement>(null);
  const idempotencyKey = useRef<string>();
  const resultRef = useRef<HTMLHeadingElement>(null);

  useEffect(() => {
    let active = true;
    setCafesLoading(true);
    setCafesError(undefined);
    setCafes([]);
    setCafeId(undefined);
    setTime('');
    loadCafes(country)
      .then((result) => {
        if (!active) return;
        setCafes(result);
        setCafeId(result[0]?.id);
        if (result.length === 0) setCafesError('There are no cafés in this country yet.');
      })
      .catch((requestError: unknown) => {
        if (active) setCafesError(guestErrorMessage(requestError, 'We could not load the cafés.'));
      })
      .finally(() => {
        if (active) setCafesLoading(false);
      });
    return () => {
      active = false;
    };
  }, [country, cafesRetry]);

  // A logged-in guest does not type the name and phone again.
  useEffect(() => {
    if (!me) return;
    setContact((current) => ({
      ...current,
      contactName: current.contactName || me.fullName,
      contactPhone: current.contactPhone || me.phone || '',
      contactEmail: current.contactEmail || me.email,
    }));
  }, [me]);

  // Keep the date inside the allowed range when the café (and maybe its time zone) changes.
  useEffect(() => {
    if (date < today || date > maxDate) setDate(today);
  }, [today, maxDate, date]);

  useEffect(() => {
    if (!focusError) return;
    formRef.current
      ?.querySelector<HTMLElement>('[aria-invalid="true"], [data-invalid="true"]')
      ?.focus();
    setFocusError(false);
  }, [focusError, errors]);

  useEffect(() => {
    if (booking) resultRef.current?.focus();
  }, [booking]);

  const times = cafe ? bookingTimes(cafe, date, today, minutesNowInTimeZone(timeZone)) : [];

  function changed(field: Field) {
    setErrors((current) => ({ ...current, [field]: undefined }));
    setFormError(undefined);
    idempotencyKey.current = undefined;
  }

  function validate(): Partial<Record<Field, string>> {
    const found: Partial<Record<Field, string>> = {};
    if (!cafeId) found.cafeId = 'Choose a café.';
    if (date < today || date > maxDate)
      found.date = `Choose a date from today to ${BOOKING_RULES.maxDaysAhead} days ahead.`;
    const option = times.find((item) => item.value === time);
    if (!time) found.time = 'Choose a time.';
    else if (!option || option.disabled)
      found.time = 'This time is no longer available. Choose a later time.';
    const guestCount = Number(guests);
    if (!Number.isInteger(guestCount) || guestCount < BOOKING_RULES.minGuests) {
      found.guests = 'Enter a whole number of guests from 1 to 8.';
    } else if (guestCount > BOOKING_RULES.maxGuests) {
      found.guests = 'For more than 8 guests, please call the café.';
    }
    const name = contact.contactName.trim();
    if (name.length < 2 || name.length > 100)
      found.contactName = 'Enter a name from 2 to 100 characters.';
    if (!/^\+\d{8,15}$/.test(contact.contactPhone.replace(/\s/g, ''))) {
      found.contactPhone = 'Enter a phone number with + and 8 to 15 digits.';
    }
    const email = contact.contactEmail.trim();
    if (email && (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) || email.length > 254)) {
      found.contactEmail = 'Enter a valid email address.';
    }
    if (contact.comment.length > 300) found.comment = 'Use 300 characters or fewer.';
    return found;
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const found = validate();
    setErrors(found);
    setFormError(undefined);
    if (Object.keys(found).length > 0) {
      setFocusError(true);
      return;
    }
    setSending(true);
    try {
      idempotencyKey.current ??= createIdempotencyKey();
      const result = await bookTable(
        {
          cafeId: cafeId as number,
          date,
          time,
          guests: Number(guests),
          contactName: contact.contactName.trim(),
          contactPhone: contact.contactPhone.replace(/\s/g, ''),
          contactEmail: contact.contactEmail.trim() || null,
          comment: contact.comment.trim() || null,
        },
        idempotencyKey.current,
      );
      setBooking(result);
    } catch (requestError) {
      if (requestError instanceof ApiRequestError && Object.keys(requestError.fields).length > 0) {
        const fieldErrors: Partial<Record<Field, string>> = {};
        const other: string[] = [];
        for (const [field, message] of Object.entries(requestError.fields)) {
          if ((FIELDS as string[]).includes(field)) fieldErrors[field as Field] = message;
          else other.push(message);
        }
        setErrors(fieldErrors);
        setFocusError(true);
        if (requestError.code === 'NO_TABLE_AVAILABLE') idempotencyKey.current = undefined;
        setFormError(
          other.length > 0
            ? other.join(' ')
            : requestError.code === 'NO_TABLE_AVAILABLE'
              ? requestError.message
              : undefined,
        );
      } else {
        setFormError(
          guestErrorMessage(requestError, 'We could not book the table. Please try again.'),
        );
      }
    } finally {
      setSending(false);
    }
  }

  if (booking) {
    return (
      <section className="booking-page" aria-labelledby="booking-done-title">
        <p className="eyebrow">Table booked</p>
        <h1 id="booking-done-title" ref={resultRef} tabIndex={-1}>
          See you soon!
        </h1>
        <p className="checkout-success__number">
          Booking number: <strong>{booking.bookingNumber}</strong>
        </p>
        <dl className="checkout-success__details">
          <div>
            <dt>Café</dt>
            <dd>
              {booking.cafe.name}, {booking.cafe.address}
            </dd>
          </div>
          <div>
            <dt>Date</dt>
            <dd>{formatDate(booking.date)}</dd>
          </div>
          <div>
            <dt>Time</dt>
            <dd>
              {booking.time}–{booking.endTime}
            </dd>
          </div>
          <div>
            <dt>Guests</dt>
            <dd>
              {booking.guests} (table for {booking.tableSeats})
            </dd>
          </div>
          <div>
            <dt>Name</dt>
            <dd>{booking.contactName}</dd>
          </div>
        </dl>
        <div className="cart-page__actions">
          <Button
            variant="outline"
            onClick={() => {
              setBooking(undefined);
              setTime('');
              idempotencyKey.current = undefined;
            }}
          >
            Book another table
          </Button>
        </div>
      </section>
    );
  }

  const errorList = Object.values(errors).filter(Boolean);

  return (
    <section className="booking-page" aria-labelledby="booking-title">
      <p className="eyebrow">Eat in</p>
      <h1 id="booking-title">Book a table</h1>
      <p className="page-description">
        A table is yours for {BOOKING_RULES.durationMinutes} minutes. We pick the best table for
        your group.
      </p>

      {formError && <ErrorMessage title="Table not booked">{formError}</ErrorMessage>}

      <form ref={formRef} className="checkout-form booking-form" onSubmit={submit} noValidate>
        <div className="checkout-form__fields">
          <section className="checkout-section" aria-labelledby="booking-when">
            <h2 id="booking-when">Where and when</h2>
            <div className="input">
              <label className="input__label" htmlFor="booking-cafe">
                Café
              </label>
              {cafesLoading ? (
                <Loader label="Loading cafés" />
              ) : cafesError ? (
                <div className="checkout-page__error">
                  <ErrorMessage title="Cafés not loaded">{cafesError}</ErrorMessage>
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() => setCafesRetry((count) => count + 1)}
                  >
                    Retry
                  </Button>
                </div>
              ) : (
                <>
                  <select
                    id="booking-cafe"
                    className={`input__control${errors.cafeId ? ' input__control--error' : ''}`}
                    value={cafeId ?? ''}
                    aria-invalid={Boolean(errors.cafeId) || undefined}
                    aria-describedby={[
                      'booking-cafe-details',
                      errors.cafeId ? 'booking-cafe-error' : '',
                    ]
                      .join(' ')
                      .trim()}
                    onChange={(event) => {
                      setCafeId(Number(event.target.value));
                      setTime('');
                      changed('cafeId');
                    }}
                  >
                    {cafes.map((item) => (
                      <option key={item.id} value={item.id}>
                        {item.name}
                      </option>
                    ))}
                  </select>
                  {cafe && (
                    <p id="booking-cafe-details" className="input__message">
                      {cafe.address}. Open {cafe.opensAt}–{cafe.closesAt}
                      {cafe.phone ? `. Phone ${cafe.phone}` : ''}.
                    </p>
                  )}
                  {errors.cafeId && (
                    <span id="booking-cafe-error" className="input__message input__message--error">
                      {errors.cafeId}
                    </span>
                  )}
                </>
              )}
            </div>

            <div className="checkout-date">
              <span id="booking-date-label" className="input__label">
                Date
              </span>
              <button
                type="button"
                className="input__control checkout-date__trigger"
                aria-labelledby="booking-date-label booking-date-value"
                aria-controls="booking-date-picker"
                aria-expanded={pickerOpen}
                data-invalid={Boolean(errors.date) || undefined}
                aria-describedby={errors.date ? 'booking-date-error' : undefined}
                onClick={() => setPickerOpen((open) => !open)}
              >
                <span id="booking-date-value">{formatDate(date)}</span>
              </button>
              {errors.date && (
                <span id="booking-date-error" className="input__message input__message--error">
                  {errors.date}
                </span>
              )}
              {pickerOpen && (
                <div id="booking-date-picker" className="checkout-date__panel">
                  <DayPicker
                    mode="single"
                    selected={dateKeyToDate(date)}
                    defaultMonth={dateKeyToDate(date)}
                    startMonth={dateKeyToDate(today)}
                    endMonth={dateKeyToDate(maxDate)}
                    disabled={(day) => {
                      const key = localDateKey(day);
                      return key < today || key > maxDate;
                    }}
                    onSelect={(day) => {
                      if (!day) return;
                      setDate(localDateKey(day));
                      setTime('');
                      changed('date');
                      setPickerOpen(false);
                    }}
                  />
                </div>
              )}
            </div>

            <div className="booking-row">
              <div className="input">
                <label className="input__label" htmlFor="booking-time">
                  Time
                </label>
                <select
                  id="booking-time"
                  className={`input__control${errors.time ? ' input__control--error' : ''}`}
                  value={time}
                  disabled={!cafe}
                  aria-invalid={Boolean(errors.time) || undefined}
                  aria-describedby={['booking-time-hint', errors.time ? 'booking-time-error' : '']
                    .join(' ')
                    .trim()}
                  onChange={(event) => {
                    setTime(event.target.value);
                    changed('time');
                  }}
                >
                  <option value="">Choose a time</option>
                  {times.map((option) => (
                    <option key={option.value} value={option.value} disabled={option.disabled}>
                      {option.value}
                      {option.disabled ? ' — too soon' : ''}
                    </option>
                  ))}
                </select>
                <span id="booking-time-hint" className="input__message">
                  Local time of the café.
                </span>
                {errors.time && (
                  <span id="booking-time-error" className="input__message input__message--error">
                    {errors.time}
                  </span>
                )}
              </div>
              <Input
                label="Guests"
                type="number"
                inputMode="numeric"
                min={BOOKING_RULES.minGuests}
                max={BOOKING_RULES.maxGuests}
                step={1}
                value={guests}
                hint="1 to 8. For more, please call the café."
                error={errors.guests}
                onChange={(event) => {
                  setGuests(event.target.value);
                  changed('guests');
                }}
              />
            </div>
          </section>

          <section className="checkout-section" aria-labelledby="booking-contact">
            <h2 id="booking-contact">Contact details</h2>
            <Input
              label="Name"
              autoComplete="name"
              value={contact.contactName}
              maxLength={100}
              error={errors.contactName}
              onChange={(event) => {
                setContact((current) => ({ ...current, contactName: event.target.value }));
                changed('contactName');
              }}
            />
            <Input
              label="Phone"
              type="tel"
              autoComplete="tel"
              value={contact.contactPhone}
              hint="International format, for example +998901234567."
              error={errors.contactPhone}
              onChange={(event) => {
                setContact((current) => ({ ...current, contactPhone: event.target.value }));
                changed('contactPhone');
              }}
            />
            <Input
              label="Email (optional)"
              type="email"
              autoComplete="email"
              value={contact.contactEmail}
              maxLength={254}
              error={errors.contactEmail}
              onChange={(event) => {
                setContact((current) => ({ ...current, contactEmail: event.target.value }));
                changed('contactEmail');
              }}
            />
            <div className="input">
              <label className="input__label" htmlFor="booking-comment">
                Comment (optional)
              </label>
              <textarea
                id="booking-comment"
                className="input__control checkout-comment"
                value={contact.comment}
                maxLength={300}
                aria-invalid={Boolean(errors.comment) || undefined}
                aria-describedby={errors.comment ? 'booking-comment-error' : undefined}
                onChange={(event) => {
                  setContact((current) => ({ ...current, comment: event.target.value }));
                  changed('comment');
                }}
              />
              {errors.comment && (
                <span id="booking-comment-error" className="input__message input__message--error">
                  {errors.comment}
                </span>
              )}
            </div>
          </section>
        </div>

        <aside className="checkout-form__summary" aria-label="Booking summary">
          <h2 className="booking-summary__title">Your booking</h2>
          <dl className="booking-summary">
            <div>
              <dt>Café</dt>
              <dd>{cafe?.name ?? '—'}</dd>
            </div>
            <div>
              <dt>Date</dt>
              <dd>{formatDate(date)}</dd>
            </div>
            <div>
              <dt>Time</dt>
              <dd>{time || '—'}</dd>
            </div>
            <div>
              <dt>Guests</dt>
              <dd>{guests || '—'}</dd>
            </div>
          </dl>
          {errorList.length > 0 && (
            <div className="checkout-error-summary" role="alert">
              <strong>Check these details</strong>
              <ul>
                {errorList.map((message) => (
                  <li key={message}>{message}</li>
                ))}
              </ul>
            </div>
          )}
          <Button type="submit" size="large" loading={sending} disabled={sending || !cafe}>
            Book a table
          </Button>
        </aside>
      </form>
    </section>
  );
}
