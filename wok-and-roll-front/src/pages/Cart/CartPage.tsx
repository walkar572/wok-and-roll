import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { Button, ErrorMessage, Loader, TotalsBlock } from '../../components/common';
import { getCartItemKey, useCart } from '../../context/CartContext';
import { useCountry } from '../../context/CountryContext';
import { ApiRequestError, guestErrorMessage, quoteCart, type CartQuote } from '../../services/api';
import { CartLine } from './CartLine';

// Field keys for one cart line, for example "items[0].quantity" (API contract, §4).
const LINE_FIELD = /^items\[(\d+)\]\.([A-Za-z]+)/;
const FIELD_LABELS: Record<string, string> = { country: 'Country', items: 'Cart' };

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
        ? 'Please check the highlighted fields.'
        : guestErrorMessage(requestError, 'We could not get the current prices. Please try again.'),
    fields: {},
    quantityErrors: {},
    lineErrors: {},
  };
  if (!(requestError instanceof ApiRequestError)) return quoteError;

  for (const [field, message] of Object.entries(requestError.fields)) {
    const match = LINE_FIELD.exec(field);
    if (!match) {
      quoteError.fields[field] = message;
    } else if (match[2] === 'quantity') {
      quoteError.quantityErrors[Number(match[1])] = message;
    } else {
      quoteError.lineErrors[Number(match[1])] = message;
    }
  }
  return quoteError;
}

export function CartPage() {
  const { country } = useCountry();
  const { items, setItemQuantity, removeItem } = useCart();
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

    if (items.length === 0) {
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
  }, [country, items, retryCount]);

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
        <p className="eyebrow">Your picks</p>
        <h1 id="cart-title">Your cart</h1>
        <div className="cart-empty">
          <h2 ref={emptyHeading} tabIndex={-1}>
            Your cart is waiting
          </h2>
          <p>There’s nothing in your cart yet. Find a favorite to get started.</p>
          <Link className="button button--primary" to="/menu">
            Browse the menu
          </Link>
        </div>
      </section>
    );
  }

  return (
    <section className="cart-page" aria-labelledby="cart-title">
      <p className="eyebrow">Your picks</p>
      <h1 id="cart-title">Your cart</h1>
      <p className="cart-page__description">Review your dishes and current prices for {country}.</p>

      {isLoading && <Loader label="Getting current cart prices" />}
      {error && (
        <div className="cart-page__error">
          <ErrorMessage title="Could not update cart prices">
            <p>{error.message}</p>
            {Object.entries(error.fields).length > 0 && (
              <ul>
                {Object.entries(error.fields).map(([field, message]) => (
                  <li key={field}>
                    {FIELD_LABELS[field] ?? field}: {message}
                  </li>
                ))}
              </ul>
            )}
          </ErrorMessage>
          <Button variant="outline" onClick={() => setRetryCount((count) => count + 1)}>
            Try again
          </Button>
        </div>
      )}

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

      {currentQuote && <TotalsBlock quote={currentQuote} />}
      <div className="cart-page__actions">
        <Link className="button button--primary" to="/checkout">
          Checkout
        </Link>
        <Link className="button button--outline" to="/menu">
          Continue shopping
        </Link>
      </div>
    </section>
  );
}
