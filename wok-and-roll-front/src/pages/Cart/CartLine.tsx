import { useEffect, useId, useState, type Ref } from 'react';
import { Button } from '../../components/common';
import { DishImage } from '../../components/common/DishImage';
import { MAX_QUANTITY, MIN_QUANTITY, type CartItem } from '../../context/CartContext';
import { modifierName, productName, useI18n } from '../../i18n';
import { formatPrice, type CartQuoteLine } from '../../services/api';

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
  const { t } = useI18n();
  const inputId = useId();
  const messageId = `${inputId}-message`;
  const { quantity } = item;
  const [draft, setDraft] = useState(String(quantity));
  const [inputError, setInputError] = useState<string>();
  const message = inputError ?? quantityError;
  const isInvalid = parseQuantity(draft) === undefined || Boolean(quantityError);
  const modifiers = quotedLine?.modifiers ?? item.modifiers;
  const name = productName(quotedLine?.name ?? item.name);

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
      <DishImage
        src={item.imageUrl}
        alt=""
        className="cart-line__image"
        placeholderClassName="cart-line__image--placeholder"
      />
      <div className="cart-line__details">
        <h2 ref={headingRef} tabIndex={-1}>
          {name}
        </h2>
        {modifiers.length > 0 && (
          <ul className="cart-line__modifiers">
            {modifiers.map((modifier) => (
              <li key={modifier.id}>{modifierName(modifier.name)}</li>
            ))}
          </ul>
        )}
        {quotedLine && currency && (
          <p>{t('cart.each', { price: formatPrice(quotedLine.unitPrice, currency) })}</p>
        )}
        {lineError && <p className="cart-line__error">{lineError}</p>}
      </div>
      <div className="cart-line__controls">
        <label className="cart-line__quantity-label" htmlFor={inputId}>
          {t('cart.quantity')}
        </label>
        <div className="cart-line__actions">
          <div className="quantity-stepper">
            <button
              className="quantity-stepper__button"
              type="button"
              aria-label={t('cart.decrease', { name })}
              disabled={quantity <= MIN_QUANTITY}
              onClick={() => saveQuantity(quantity - 1)}
            >
              −
            </button>
            <input
              className="quantity-stepper__input"
              id={inputId}
              aria-label={t('cart.quantityFor', { name })}
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
                  setInputError(t('cart.quantityRule'));
                  return;
                }

                setInputError(undefined);
                if (nextQuantity !== quantity) onQuantityChange(nextQuantity);
              }}
              onBlur={() => {
                if (parseQuantity(draft) === undefined) {
                  setInputError(t('cart.quantityStays', { quantity }));
                }
                setDraft(String(quantity));
              }}
            />
            <button
              className="quantity-stepper__button"
              type="button"
              aria-label={t('cart.increase', { name })}
              disabled={quantity >= MAX_QUANTITY}
              onClick={() => saveQuantity(quantity + 1)}
            >
              +
            </button>
          </div>
          <Button
            type="button"
            variant="ghost"
            size="small"
            aria-label={t('cart.removeItem', { name })}
            onClick={onRemove}
          >
            {t('cart.remove')}
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
