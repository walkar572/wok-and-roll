import { useEffect, useId, useState, type Ref } from 'react';
import { Button } from '../../components/common';
import { MAX_QUANTITY, MIN_QUANTITY, type CartItem } from '../../context/CartContext';
import { formatPrice, type CartQuoteLine } from '../../services/api';

export const QUANTITY_RULE = `Enter a whole number from ${MIN_QUANTITY} to ${MAX_QUANTITY}.`;

// Accepts only digits, so 0, -1, 1.5, 1e2, empty and text are all rejected.
export function parseQuantity(value: string): number | undefined {
  const text = value.trim();
  if (!/^\d+$/.test(text)) return undefined;

  const quantity = Number(text);
  return quantity >= MIN_QUANTITY && quantity <= MAX_QUANTITY ? quantity : undefined;
}

interface CartLineProps {
  item: CartItem;
  quotedLine?: CartQuoteLine;
  currency?: string;
  quantityError?: string;
  lineError?: string;
  headingRef?: Ref<HTMLHeadingElement>;
  onQuantityChange: (quantity: number) => void;
  onRemove: () => void;
}

export function CartLine({
  item,
  quotedLine,
  currency,
  quantityError,
  lineError,
  headingRef,
  onQuantityChange,
  onRemove,
}: CartLineProps) {
  const inputId = useId();
  const messageId = `${inputId}-message`;
  const { quantity } = item;
  const [draft, setDraft] = useState(String(quantity));
  const [inputError, setInputError] = useState<string>();
  const message = inputError ?? quantityError;
  const isInvalid = parseQuantity(draft) === undefined || Boolean(quantityError);
  const modifiers = quotedLine?.modifiers ?? item.modifiers;

  useEffect(() => {
    setDraft(String(quantity));
  }, [quantity]);

  function saveQuantity(nextQuantity: number) {
    setInputError(undefined);
    setDraft(String(nextQuantity));
    if (nextQuantity !== quantity) onQuantityChange(nextQuantity);
  }

  return (
    <article className="cart-line">
      {item.imageUrl ? (
        <img className="cart-line__image" src={item.imageUrl} alt="" />
      ) : (
        <span className="cart-line__image cart-line__image--placeholder" aria-hidden="true">
          ✳
        </span>
      )}
      <div className="cart-line__details">
        <h2 ref={headingRef} tabIndex={-1}>
          {quotedLine?.name ?? item.name}
        </h2>
        {modifiers.length > 0 && (
          <ul className="cart-line__modifiers">
            {modifiers.map((modifier) => (
              <li key={modifier.id}>{modifier.name}</li>
            ))}
          </ul>
        )}
        {quotedLine && currency && <p>{formatPrice(quotedLine.unitPrice, currency)} each</p>}
        {lineError && <p className="cart-line__error">{lineError}</p>}
      </div>
      <div className="cart-line__controls">
        <label className="cart-line__quantity-label" htmlFor={inputId}>
          Quantity
        </label>
        <div className="cart-line__actions">
          <div className="quantity-stepper">
            <button
              className="quantity-stepper__button"
              type="button"
              aria-label={`Decrease quantity of ${item.name}`}
              disabled={quantity <= MIN_QUANTITY}
              onClick={() => saveQuantity(quantity - 1)}
            >
              −
            </button>
            <input
              className="quantity-stepper__input"
              id={inputId}
              aria-label={`Quantity for ${item.name}`}
              aria-invalid={isInvalid || undefined}
              aria-describedby={message ? messageId : undefined}
              type="number"
              inputMode="numeric"
              min={MIN_QUANTITY}
              max={MAX_QUANTITY}
              step={1}
              value={draft}
              onChange={(event) => {
                const { value } = event.target;
                setDraft(value);

                const nextQuantity = parseQuantity(value);
                if (nextQuantity === undefined) {
                  setInputError(QUANTITY_RULE);
                  return;
                }

                setInputError(undefined);
                if (nextQuantity !== quantity) onQuantityChange(nextQuantity);
              }}
              onBlur={() => {
                if (parseQuantity(draft) === undefined) {
                  setInputError(`${QUANTITY_RULE} The quantity stays ${quantity}.`);
                }
                setDraft(String(quantity));
              }}
            />
            <button
              className="quantity-stepper__button"
              type="button"
              aria-label={`Increase quantity of ${item.name}`}
              disabled={quantity >= MAX_QUANTITY}
              onClick={() => saveQuantity(quantity + 1)}
            >
              +
            </button>
          </div>
          <Button type="button" variant="ghost" size="small" onClick={onRemove}>
            Remove
          </Button>
        </div>
        <p className="cart-line__quantity-message" id={messageId} aria-live="polite">
          {message}
        </p>
      </div>
      {quotedLine && currency && (
        <strong className="cart-line__total">{formatPrice(quotedLine.lineTotal, currency)}</strong>
      )}
    </article>
  );
}
