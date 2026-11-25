import { useI18n } from '../../i18n';
import type { Product } from '../../services/api';
import { Icon } from './Icon';

/**
 * The marks under a dish: spicy in red with a chili, vegetarian in green with a leaf. The server
 * reads them from the ingredients (contract v1.16); a dish with neither shows nothing.
 */
export function DishMarks({
  product,
  className,
}: {
  product: Pick<Product, 'spicy' | 'vegetarian'>;
  className?: string;
}) {
  const { t } = useI18n();
  if (!product.spicy && !product.vegetarian) return null;
  return (
    <ul className={className ? `dish-marks ${className}` : 'dish-marks'}>
      {product.spicy && (
        <li className="dish-mark dish-mark--spicy">
          <Icon name="chili" width={14} height={14} fill="currentColor" />
          {t('menu.spicy')}
        </li>
      )}
      {product.vegetarian && (
        <li className="dish-mark dish-mark--vegetarian">
          <Icon name="leaf" width={14} height={14} />
          {t('menu.vegetarian')}
        </li>
      )}
    </ul>
  );
}
