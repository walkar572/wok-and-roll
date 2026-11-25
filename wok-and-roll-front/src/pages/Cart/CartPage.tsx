import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { Button, ErrorMessage, Loader, Receipt } from '../../components/common';
import { ChooseCity } from '../../components/layout/ChooseCity';
import { getCartItemKey, useCart } from '../../context/CartContext';
import { useCountry } from '../../context/CountryContext';
import { placeName, t as translate, useI18n } from '../../i18n';
import { apiCodeMessage, apiFieldMessage } from '../../services/apiMessages';
import { ApiRequestError, guestErrorMessage, quoteCart, type CartQuote } from '../../services/api';
import { CartLine } from './CartLine';

// Field keys for one cart line, for example "items[0].quantity" (API contract, §4).
const LINE_FIELD = /^items\[(\d+)\]\.([A-Za-z]+)/;

interface QuoteError {
  message: string;
  fields: Record<string, string>;
  quantityErrors: Record<number, string>;
  lineErrors: Record<number, string>;
}

function toQuoteError(requestError: unknown): QuoteError {
  const quoteError: QuoteError = {
    message:
      requestError instanceof ApiRequestError && requestError.code === 'VALIDATION_ERROR'
        ? translate('cart.checkHighlighted')
        : guestErrorMessage(requestError, translate('cart.pricesFallback')),
    fields: {},
    quantityErrors: {},
    lineErrors: {},
  };
  if (!(requestError instanceof ApiRequestError)) return quoteError;

  for (const [field, message] of Object.entries(requestError.fields)) {
    const match = LINE_FIELD.exec(field);
    const text = apiFieldMessage(field, message, requestError.code);
    if (!match) {
      quoteError.fields[field] = text;
    } else if (match[2] === 'quantity') {
      quoteError.quantityErrors[Number(match[1])] = text;
    } else {
      // The line gets the reason of the error code when there is one (out of stock, not sold).
      quoteError.lineErrors[Number(match[1])] = apiCodeMessage(requestError.code) ?? text;
    }
  }
  return quoteError;
}

export function CartPage() {
  const { country, city, allCities } = useCountry();
  const { items, setItemQuantity, removeItem } = useCart();
  const { t } = useI18n();
  const [quote, setQuote] = useState<CartQuote>();
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<QuoteError>();
  const [retryCount, setRetryCount] = useState(0);
  const lineHeadings = useRef(new Map<string, HTMLHeadingElement>());
  const emptyHeading = useRef<HTMLHeadingElement>(null);
  const focusAfterRemove = useRef<number | null>(null);
  const currentQuote =
    quote &&
    quote.country === country &&
    quote.lines.length === items.length &&
    quote.lines.every(
      (line, index) =>
        line.productId === items[index].productId && line.quantity === items[index].quantity,
    )
      ? quote
      : undefined;

  useEffect(() => {
    let active = true;
    setQuote(undefined);
    setError(undefined);

    // No prices for "All cities": the quote needs the country of one city.
    if (items.length === 0 || allCities) {
      setIsLoading(false);
      return () => {
        active = false;
      };
    }

    setIsLoading(true);
    quoteCart({
      country,
      scenario: 'PICKUP',
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
        if (active) setError(toQuoteError(requestError));
      })
      .finally(() => {
        if (active) setIsLoading(false);
      });

    return () => {
      active = false;
    };
  }, [country, items, retryCount, allCities]);

  // After Remove, keyboard focus goes to the next line, or to the empty cart message.
  useEffect(() => {
    const removedIndex = focusAfterRemove.current;
    if (removedIndex === null) return;
    focusAfterRemove.current = null;

    if (items.length === 0) {
      emptyHeading.current?.focus();
      return;
    }
    const nextItem = items[Math.min(removedIndex, items.length - 1)];
    lineHeadings.current.get(getCartItemKey(nextItem.productId, nextItem.modifierIds))?.focus();
  }, [items]);

  if (items.length === 0) {
    return (
      <section className="cart-page" aria-labelledby="cart-title">
        <p className="eyebrow">{t('cart.eyebrow')}</p>
        <h1 id="cart-title">{t('cart.title')}</h1>
        <div className="cart-empty">
          <h2 ref={emptyHeading} tabIndex={-1}>
            {t('cart.emptyTitle')}
          </h2>
          <p>{t('cart.emptyText')}</p>
          <Link className="button button--primary" to="/menu">
            {t('cart.browse')}
          </Link>
        </div>
      </section>
    );
  }

  return (
    <section className="cart-page" aria-labelledby="cart-title">
      <p className="eyebrow">{t('cart.eyebrow')}</p>
      <h1 id="cart-title">{t('cart.title')}</h1>
      {/* "All cities" has no prices to review yet: the city list next to the dishes asks first. */}
      {!allCities && (
        <p className="cart-page__description">
          {t('cart.description', { country: placeName(country, city) })}
        </p>
      )}

      {error && (
        <div className="cart-page__error">
          <ErrorMessage title={t('cart.pricesError')}>
            <p>{error.message}</p>
            {Object.entries(error.fields).length > 0 && (
              <ul>
                {Object.entries(error.fields).map(([field, message]) => (
                  <li key={field}>
                    {field === 'country'
                      ? t('cart.fieldCountry')
                      : field === 'items'
                        ? t('cart.fieldItems')
                        : field}
                    : {message}
                  </li>
                ))}
              </ul>
            )}
          </ErrorMessage>
          <Button variant="outline" onClick={() => setRetryCount((count) => count + 1)}>
            {t('common.tryAgain')}
          </Button>
        </div>
      )}

      <div className="receipt-layout">
        <div className="cart-lines">
          {items.map((item, index) => {
            const itemKey = getCartItemKey(item.productId, item.modifierIds);

            return (
              <CartLine
                key={itemKey}
                item={item}
                quotedLine={currentQuote?.lines[index]}
                currency={currentQuote?.currency}
                quantityError={error?.quantityErrors[index]}
                lineError={error?.lineErrors[index]}
                headingRef={(node) => {
                  if (node) lineHeadings.current.set(itemKey, node);
                  else lineHeadings.current.delete(itemKey);
                }}
                onQuantityChange={(quantity) => {
                  setItemQuantity(item.productId, item.modifierIds, quantity);
                }}
                onRemove={() => {
                  focusAfterRemove.current = index;
                  removeItem(item.productId, item.modifierIds);
                }}
              />
            );
          })}
        </div>

        <div className="receipt-layout__side">
          {allCities && <ChooseCity compact />}
          {isLoading && <Loader label={t('cart.loadingPrices')} />}
          {currentQuote && <Receipt quote={currentQuote} />}
          <div className="receipt-layout__actions">
            {!allCities && (
              <Link className="button button--primary button--large" to="/checkout">
                {t('cart.checkout')}
              </Link>
            )}
            <Link className="button button--outline" to="/menu">
              {t('cart.continueShopping')}
            </Link>
          </div>
        </div>
      </div>
    </section>
  );
}
