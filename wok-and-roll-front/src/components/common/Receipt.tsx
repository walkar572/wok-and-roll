import { useCountry } from '../../context/CountryContext';
import {
  cafeAddress,
  clock,
  modifierName,
  placeName,
  productName,
  useI18n,
  zoneName,
} from '../../i18n';
import { formatPrice, type CartQuote, type OrderResponse } from '../../services/api';
import { formatDate } from '../../utils/dates';

export interface ReceiptProps {
  /** A cart quote or an order: both have the lines and the totals. */
  quote: CartQuote | OrderResponse;
}

// The prices of a cart or an order, printed like a café receipt: the brand, the place, the dishes,
// the totals and a barcode. The paper edges and the barcode are only CSS, the slogan is decoration.
export function Receipt({ quote }: ReceiptProps) {
  const { t } = useI18n();
  const { country, city } = useCountry();
  const price = (amount: number) => formatPrice(amount, quote.currency);
  // Under the brand, like the shop address of a receipt: for an order the address of its café (or
  // its delivery zone), its number and time; for a cart its city.
  const details =
    'orderNumber' in quote
      ? [
          quote.scenario === 'PICKUP'
            ? cafeAddress(quote.cafe.address)
            : zoneName(quote.deliveryZone?.name ?? ''),
          t('orderDetails.title', { number: quote.orderNumber }),
          `${formatDate(quote.orderDate)}, ${clock(quote.slotStart)}–${clock(quote.slotEnd)}`,
        ]
      : [placeName(quote.country, quote.country === country ? city : undefined)];

  return (
    <section className="receipt" aria-label={t('totals.label')}>
      <div className="receipt__paper">
        <div className="receipt__head">
          <p className="receipt__brand">
            Wok <span>&amp;</span> Roll
          </p>
          {details.map((line) => (
            <p key={line}>{line}</p>
          ))}
        </div>

        <ul className="receipt__lines" aria-label={t('totals.lines')}>
          {quote.lines.map((line, index) => (
            <li key={`${line.productId}-${index}`}>
              <span className="receipt__dish">{productName(line.name)}</span>
              <span className="receipt__amount">{price(line.lineTotal)}</span>
              {line.modifiers.length > 0 && (
                <span className="receipt__note">
                  {line.modifiers.map((modifier) => modifierName(modifier.name)).join(', ')}
                </span>
              )}
              <span className="receipt__note">
                {line.quantity}&nbsp;×&nbsp;{price(line.unitPrice)}
              </span>
            </li>
          ))}
        </ul>

        <dl className="receipt__totals">
          <div>
            <dt>{t('totals.subtotal')}</dt>
            <dd>{price(quote.subtotal)}</dd>
          </div>
          <div>
            <dt>{t('totals.vat', { rate: quote.vatRate })}</dt>
            <dd>{price(quote.vat)}</dd>
          </div>
          <div>
            <dt>{t('totals.amountInclVat')}</dt>
            <dd>{price(quote.amountInclVat)}</dd>
          </div>
          <div>
            <dt>{t('totals.discount', { rate: quote.discountRate })}</dt>
            <dd>
              {quote.discount > 0 ? '−' : ''}
              {price(quote.discount)}
            </dd>
          </div>
          <div>
            <dt>{t('totals.deliveryFee')}</dt>
            <dd>{price(quote.deliveryFee)}</dd>
          </div>
          <div className="receipt__total">
            <dt>{t('totals.total')}</dt>
            <dd>{price(quote.total)}</dd>
          </div>
        </dl>

        <div className="receipt__foot" aria-hidden="true">
          <span className="receipt__barcode" />
          <span>Wok. Roll. {t('home.titleRest')}</span>
        </div>
      </div>
    </section>
  );
}
