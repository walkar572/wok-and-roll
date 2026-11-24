import { useI18n } from '../../i18n';
import { formatPrice, type CartQuote } from '../../services/api';

export interface TotalsBlockProps {
  quote: CartQuote;
}

export function TotalsBlock({ quote }: TotalsBlockProps) {
  const { t } = useI18n();
  return (
    <section className="cart-summary" aria-label={t('totals.label')}>
      <dl>
        <div>
          <dt>{t('totals.subtotal')}</dt>
          <dd>{formatPrice(quote.subtotal, quote.currency)}</dd>
        </div>
        <div>
          <dt>{t('totals.vat', { rate: quote.vatRate })}</dt>
          <dd>{formatPrice(quote.vat, quote.currency)}</dd>
        </div>
        <div>
          <dt>{t('totals.amountInclVat')}</dt>
          <dd>{formatPrice(quote.amountInclVat, quote.currency)}</dd>
        </div>
        <div>
          <dt>{t('totals.discount', { rate: quote.discountRate })}</dt>
          <dd>
            {quote.discount > 0 ? '−' : ''}
            {formatPrice(quote.discount, quote.currency)}
          </dd>
        </div>
        <div>
          <dt>{t('totals.deliveryFee')}</dt>
          <dd>{formatPrice(quote.deliveryFee, quote.currency)}</dd>
        </div>
        <div className="cart-summary__total">
          <dt>{t('totals.total')}</dt>
          <dd>{formatPrice(quote.total, quote.currency)}</dd>
        </div>
      </dl>
    </section>
  );
}
