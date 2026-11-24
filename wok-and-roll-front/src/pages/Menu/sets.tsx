// Sets on the menu (contract v1.10): what is in a set, and how much it saves against its dishes
// bought one by one. The struck-through price is hidden from screen readers; they hear the words
// ("Separately: 239 000,00 soʻm") instead, because a strike-through is not read out.
import { productName, t } from '../../i18n';
import { formatPrice, type Product } from '../../services/api';

/** "2 × Philadelphia roll, California roll" in the current language; null for a dish. */
export function setComposition(product: Pick<Product, 'setItems'>): string | null {
  if (product.setItems.length === 0) return null;
  return product.setItems
    .map(({ name, quantity }) =>
      quantity > 1
        ? t('menu.setItem', { count: quantity, name: productName(name) })
        : productName(name),
    )
    .join(', ');
}

/** The saving of a set in whole percent, or null when there is none to show. */
export function setSaving(product: Pick<Product, 'price' | 'regularPrice'>): number | null {
  const { price, regularPrice } = product;
  if (regularPrice === null || regularPrice <= 0 || price >= regularPrice) return null;
  const percent = Math.round((1 - price / regularPrice) * 100);
  return percent > 0 ? percent : null;
}

/** The price, and for a set also its dishes one by one, struck through. */
export function DishPrice({
  product,
  className,
}: {
  product: Pick<Product, 'price' | 'regularPrice' | 'currency'>;
  className?: string;
}) {
  const saving = setSaving(product);
  return (
    <span className={`dish-price${className ? ` ${className}` : ''}`}>
      <strong>{formatPrice(product.price, product.currency)}</strong>
      {saving !== null && product.regularPrice !== null && (
        <>
          <s className="dish-price__regular" aria-hidden="true">
            {formatPrice(product.regularPrice, product.currency)}
          </s>
          <span className="sr-only">
            {t('menu.separately', { price: formatPrice(product.regularPrice, product.currency) })}
          </span>
        </>
      )}
    </span>
  );
}

/** "−17%" on the photo of a set; screen readers hear "Save 17%". */
export function SavingBadge({ percent, className }: { percent: number; className?: string }) {
  return (
    <span className={`saving-badge${className ? ` ${className}` : ''}`}>
      <span aria-hidden="true">−{percent}%</span>
      <span className="sr-only">{t('menu.save', { percent })}</span>
    </span>
  );
}
