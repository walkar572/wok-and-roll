import { useEffect, useState } from 'react';
import { Button, ErrorMessage, Loader } from '../../components/common';
import { DishImage } from '../../components/common/DishImage';
import { Icon } from '../../components/common/Icon';
import { useCart } from '../../context/CartContext';
import { useCountry } from '../../context/CountryContext';
import { categoryName, countryName, productDescription, productName, useI18n } from '../../i18n';
import {
  formatPrice,
  guestErrorMessage,
  loadCategories,
  loadProducts,
  type Category,
  type CountryCode,
  type Product,
} from '../../services/api';
import { ProductDetailModal } from './ProductDetailModal';

export function MenuPage() {
  const { country } = useCountry();
  const { addItem } = useCart();
  const { t } = useI18n();
  const [categories, setCategories] = useState<Category[]>([]);
  const [products, setProducts] = useState<Product[]>([]);
  const [selectedCategory, setSelectedCategory] = useState<{
    country: CountryCode;
    id: number;
  } | null>(null);
  const [selectedProductId, setSelectedProductId] = useState<number | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [retryCount, setRetryCount] = useState(0);

  const categoryId = selectedCategory?.country === country ? selectedCategory.id : undefined;

  useEffect(() => {
    let active = true;
    setIsLoading(true);
    setError(undefined);
    setCategories([]);
    setProducts([]);

    Promise.all([loadCategories(country), loadProducts(country, categoryId)])
      .then(([loadedCategories, loadedProducts]) => {
        if (!active) return;
        setCategories(loadedCategories);
        setProducts(loadedProducts);
      })
      .catch((requestError: unknown) => {
        if (!active) return;
        setError(guestErrorMessage(requestError, t('menu.error')));
      })
      .finally(() => {
        if (active) setIsLoading(false);
      });

    return () => {
      active = false;
    };
  }, [country, categoryId, retryCount, t]);

  return (
    <section className="menu-page" aria-labelledby="menu-title">
      <header className="menu-page__header">
        <p className="eyebrow">{t('menu.eyebrow')}</p>
        <h1 id="menu-title">{t('menu.title')}</h1>
        <p>{t('menu.description')}</p>
      </header>

      <div className="menu-toolbar">
        <nav className="menu-categories" aria-label={t('menu.filter')}>
          <Button
            variant={categoryId === undefined ? 'primary' : 'outline'}
            aria-pressed={categoryId === undefined}
            onClick={() => setSelectedCategory(null)}
          >
            {t('menu.all')}
          </Button>
          {categories.map((category) => (
            <Button
              key={category.id}
              variant={categoryId === category.id ? 'primary' : 'outline'}
              aria-pressed={categoryId === category.id}
              onClick={() => setSelectedCategory({ country, id: category.id })}
            >
              {categoryName(category.name)}
            </Button>
          ))}
        </nav>
        <p className="menu-toolbar__country">
          {t('menu.pricesFor', { country: countryName(country) })}
        </p>
      </div>

      {isLoading && <Loader label={t('menu.loading')} />}
      {error && (
        <div className="menu-page__error">
          <ErrorMessage title={t('menu.unavailable')}>{error}</ErrorMessage>
          <Button variant="outline" onClick={() => setRetryCount((count) => count + 1)}>
            {t('common.tryAgain')}
          </Button>
        </div>
      )}
      {!isLoading && !error && products.length === 0 && (
        <div className="menu-empty">
          <h2>{t('menu.emptyTitle')}</h2>
          <p>{t('menu.emptyText')}</p>
        </div>
      )}
      {!isLoading && !error && products.length > 0 && (
        <div className="menu-grid">
          {products.map((product) => {
            const name = productName(product.name);
            const description = productDescription(product.name, product.description);
            const action = product.hasModifiers ? t('menu.customize') : t('menu.details');
            return (
              <article className="menu-card" key={product.id}>
                <DishImage
                  src={product.imageUrl}
                  alt={name}
                  className="menu-card__image"
                  placeholderClassName="menu-card__image--placeholder"
                />
                <div className="menu-card__content">
                  <div className="menu-card__heading">
                    <h2>{name}</h2>
                    {!product.inStock && (
                      <span className="menu-card__stock">{t('menu.outOfStock')}</span>
                    )}
                  </div>
                  {description && <p>{description}</p>}
                  <div className="menu-card__footer">
                    <strong>{formatPrice(product.price, product.currency)}</strong>
                    <Button
                      className="menu-card__action"
                      variant="outline"
                      size="small"
                      aria-label={`${action}: ${name}`}
                      onClick={() => setSelectedProductId(product.id)}
                    >
                      <span className="menu-card__action-text">{action}</span>
                      <Icon className="menu-card__action-icon" name="plus" />
                    </Button>
                  </div>
                </div>
              </article>
            );
          })}
        </div>
      )}

      <ProductDetailModal
        productId={selectedProductId}
        country={country}
        onAdd={addItem}
        onClose={() => setSelectedProductId(null)}
      />
    </section>
  );
}
