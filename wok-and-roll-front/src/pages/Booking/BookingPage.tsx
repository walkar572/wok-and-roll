import { useEffect, useRef, useState, type FormEvent } from 'react';
import { DayPicker } from 'react-day-picker';
import 'react-day-picker/style.css';
import { Button, ErrorMessage, Input, Loader } from '../../components/common';
import { useAuth } from '../../context/AuthContext';
import { useCountry } from '../../context/CountryContext';
import { cafeAddress, cafeName, t as translate, useI18n } from '../../i18n';
import { DAY_PICKER_LOCALE } from '../../i18n/dayPicker';
import { apiCodeMessage, apiFieldMessage } from '../../services/apiMessages';
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
  const { t, locale } = useI18n();
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
        // "Book a table" on a café card of the home page opens the form with that café.
        const wanted = Number(new URLSearchParams(window.location.search).get('cafe'));
        setCafeId((result.find(({ id }) => id === wanted) ?? result[0])?.id);
        if (result.length === 0) setCafesError(translate('booking.noCafes'));
      })
      .catch((requestError: unknown) => {
        if (active)
          setCafesError(guestErrorMessage(requestError, translate('booking.cafesFallback')));
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
  const noTimesLeft = times.length > 0 && times.every((option) => option.disabled);

  // Late in the evening today has no free time any more: start from tomorrow instead.
  useEffect(() => {
    if (cafe && date === today && noTimesLeft) {
      setDate(localDateKey(addDays(dateKeyToDate(today), 1)));
    }
    // Only when the café or "today" changes, not when the guest picks today again on purpose.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [cafe, today]);

  function changed(field: Field) {
    setErrors((current) => ({ ...current, [field]: undefined }));
    setFormError(undefined);
    idempotencyKey.current = undefined;
  }

  function validate(): Partial<Record<Field, string>> {
    const found: Partial<Record<Field, string>> = {};
    if (!cafeId) found.cafeId = t('booking.validationCafe');
    if (date < today || date > maxDate)
      found.date = t('booking.validationDate', { days: BOOKING_RULES.maxDaysAhead });
    const option = times.find((item) => item.value === time);
    if (!time) found.time = t('booking.validationTime');
    else if (!option || option.disabled) found.time = t('booking.validationTimeGone');
    const guestCount = Number(guests);
    if (!Number.isInteger(guestCount) || guestCount < BOOKING_RULES.minGuests) {
      found.guests = t('booking.validationGuests');
    } else if (guestCount > BOOKING_RULES.maxGuests) {
      found.guests = t('booking.validationGuestsMany');
    }
    const name = contact.contactName.trim();
    if (name.length < 2 || name.length > 100) found.contactName = t('validation.name');
    if (!/^\+\d{8,15}$/.test(contact.contactPhone.replace(/\s/g, ''))) {
      found.contactPhone = t('validation.phone');
    }
    const email = contact.contactEmail.trim();
    if (email && (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) || email.length > 254)) {
      found.contactEmail = t('validation.email');
    }
    if (contact.comment.length > 300) found.comment = t('validation.comment');
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
          if ((FIELDS as string[]).includes(field))
            fieldErrors[field as Field] = apiFieldMessage(field, message, requestError.code);
          else other.push(message);
        }
        setErrors(fieldErrors);
        setFocusError(true);
        if (requestError.code === 'NO_TABLE_AVAILABLE') idempotencyKey.current = undefined;
        setFormError(
          other.length > 0
            ? other.join(' ')
            : requestError.code === 'NO_TABLE_AVAILABLE'
              ? (apiCodeMessage(requestError.code) ?? requestError.message)
              : undefined,
        );
      } else {
        setFormError(guestErrorMessage(requestError, t('booking.bookFallback')));
      }
    } finally {
      setSending(false);
    }
  }

  if (booking) {
    return (
      <section className="booking-page" aria-labelledby="booking-done-title">
        <p className="eyebrow">{t('booking.doneEyebrow')}</p>
        <h1 id="booking-done-title" ref={resultRef} tabIndex={-1}>
          {t('booking.doneTitle')}
        </h1>
        <p className="checkout-success__number">
          {t('booking.number')} <strong>{booking.bookingNumber}</strong>
        </p>
        <dl className="checkout-success__details">
          <div>
            <dt>{t('booking.cafe')}</dt>
            <dd>
              {cafeName(booking.cafe.name)}, {cafeAddress(booking.cafe.address)}
            </dd>
          </div>
          <div>
            <dt>{t('booking.date')}</dt>
            <dd>{formatDate(booking.date)}</dd>
          </div>
          <div>
            <dt>{t('booking.time')}</dt>
            <dd>
              {booking.time}–{booking.endTime}
            </dd>
          </div>
          <div>
            <dt>{t('booking.guests')}</dt>
            <dd>
              {t('booking.guestsValue', { guests: booking.guests, seats: booking.tableSeats })}
            </dd>
          </div>
          <div>
            <dt>{t('fields.name')}</dt>
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
            {t('booking.another')}
          </Button>
        </div>
      </section>
    );
  }

  const errorList = Object.values(errors).filter(Boolean);

  return (
    <section className="booking-page" aria-labelledby="booking-title">
      <p className="eyebrow">{t('booking.eyebrow')}</p>
      <h1 id="booking-title">{t('booking.title')}</h1>
      <p className="page-description">
        {t('booking.description', { minutes: BOOKING_RULES.durationMinutes })}
      </p>

      {formError && <ErrorMessage title={t('booking.notBooked')}>{formError}</ErrorMessage>}

      <form ref={formRef} className="checkout-form booking-form" onSubmit={submit} noValidate>
        <div className="checkout-form__fields">
          <section className="checkout-section" aria-labelledby="booking-when">
            <h2 id="booking-when">{t('booking.whereWhen')}</h2>
            <div className="input">
              <label className="input__label" htmlFor="booking-cafe">
                {t('booking.cafe')}
              </label>
              {cafesLoading ? (
                <Loader label={t('booking.loadingCafes')} />
              ) : cafesError ? (
                <div className="checkout-page__error">
                  <ErrorMessage title={t('booking.cafesNotLoaded')}>{cafesError}</ErrorMessage>
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() => setCafesRetry((count) => count + 1)}
                  >
                    {t('common.retry')}
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
                        {cafeName(item.name)}
                      </option>
                    ))}
                  </select>
                  {cafe && (
                    <p id="booking-cafe-details" className="input__message">
                      {t('booking.cafeDetails', {
                        address: cafeAddress(cafe.address),
                        opens: cafe.opensAt,
                        closes: cafe.closesAt,
                      })}
                      {cafe.phone ? ` ${t('booking.cafePhone', { phone: cafe.phone })}` : ''}
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
                {t('booking.date')}
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
                    locale={DAY_PICKER_LOCALE[locale]}
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
                  {t('booking.time')}
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
                  <option value="">{t('booking.chooseTime')}</option>
                  {times.map((option) => (
                    <option key={option.value} value={option.value} disabled={option.disabled}>
                      {option.value}
                      {option.disabled ? ` — ${t('booking.tooSoon')}` : ''}
                    </option>
                  ))}
                </select>
                <span id="booking-time-hint" className="input__message">
                  {noTimesLeft ? t('booking.noTimesLeft') : t('booking.localTime')}
                </span>
                {errors.time && (
                  <span id="booking-time-error" className="input__message input__message--error">
                    {errors.time}
                  </span>
                )}
              </div>
              <Input
                label={t('booking.guests')}
                type="number"
                inputMode="numeric"
                min={BOOKING_RULES.minGuests}
                max={BOOKING_RULES.maxGuests}
                step={1}
                value={guests}
                hint={t('booking.guestsHint')}
                error={errors.guests}
                onChange={(event) => {
                  setGuests(event.target.value);
                  changed('guests');
                }}
              />
            </div>
          </section>

          <section className="checkout-section" aria-labelledby="booking-contact">
            <h2 id="booking-contact">{t('fields.contactTitle')}</h2>
            <Input
              label={t('fields.name')}
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
              label={t('fields.phone')}
              type="tel"
              autoComplete="tel"
              value={contact.contactPhone}
              hint={t('fields.phoneHint')}
              error={errors.contactPhone}
              onChange={(event) => {
                setContact((current) => ({ ...current, contactPhone: event.target.value }));
                changed('contactPhone');
              }}
            />
            <Input
              label={t('fields.emailOptional')}
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
                {t('fields.commentOptional')}
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

        <aside className="checkout-form__summary" aria-label={t('booking.summaryLabel')}>
          <h2 className="booking-summary__title">{t('booking.summaryTitle')}</h2>
          <dl className="booking-summary">
            <div>
              <dt>{t('booking.cafe')}</dt>
              <dd>{cafe ? cafeName(cafe.name) : '—'}</dd>
            </div>
            <div>
              <dt>{t('booking.date')}</dt>
              <dd>{formatDate(date)}</dd>
            </div>
            <div>
              <dt>{t('booking.time')}</dt>
              <dd>{time || '—'}</dd>
            </div>
            <div>
              <dt>{t('booking.guests')}</dt>
              <dd>{guests || '—'}</dd>
            </div>
          </dl>
          {errorList.length > 0 && (
            <div className="checkout-error-summary" role="alert">
              <strong>{t('booking.checkDetails')}</strong>
              <ul>
                {errorList.map((message) => (
                  <li key={message}>{message}</li>
                ))}
              </ul>
            </div>
          )}
          <Button type="submit" size="large" loading={sending} disabled={sending || !cafe}>
            {t('booking.submit')}
          </Button>
        </aside>
      </form>
    </section>
  );
}
