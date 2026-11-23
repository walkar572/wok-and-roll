import { formatPrice, type CartQuote } from '../../services/api';

export interface TotalsBlockProps {
  quote: CartQuote;
}

export function TotalsBlock({ quote }: TotalsBlockProps) {
  return (
    <section className="cart-summary" aria-label="Price summary">
      <dl>
        <div>
          <dt>Subtotal</dt>
          <dd>{formatPrice(quote.subtotal, quote.currency)}</dd>
        </div>
        <div>
          <dt>VAT ({quote.vatRate}%)</dt>
          <dd>{formatPrice(quote.vat, quote.currency)}</dd>
        </div>
        <div>
          <dt>Amount incl. VAT</dt>
          <dd>{formatPrice(quote.amountInclVat, quote.currency)}</dd>
        </div>
        <div>
          <dt>Discount ({quote.discountRate}%)</dt>
          <dd>
            {quote.discount > 0 ? '-' : ''}
            {formatPrice(quote.discount, quote.currency)}
          </dd>
        </div>
        <div>
          <dt>Delivery fee</dt>
          <dd>{formatPrice(quote.deliveryFee, quote.currency)}</dd>
        </div>
        <div className="cart-summary__total">
          <dt>Total</dt>
          <dd>{formatPrice(quote.total, quote.currency)}</dd>
        </div>
      </dl>
    </section>
  );
}
