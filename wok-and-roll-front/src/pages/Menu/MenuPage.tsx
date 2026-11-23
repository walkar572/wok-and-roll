import { useEffect, useState } from 'react';
import { Button, ErrorMessage, Loader } from '../../components/common';
import { Icon } from '../../components/common/Icon';
import { useCart } from '../../context/CartContext';
import { useCountry } from '../../context/CountryContext';
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
        setError(guestErrorMessage(requestError, 'We could not load the menu. Please try again.'));
      })
      .finally(() => {
        if (active) setIsLoading(false);
      });

    return () => {
      active = false;
    };
  }, [country, categoryId, retryCount]);

  return (
    <section className="menu-page" aria-labelledby="menu-title">
      <header className="menu-page__header">
        <p className="eyebrow">Fresh from the wok</p>
        <h1 id="menu-title">The menu</h1>
        <p>A little something for every craving.</p>
      </header>

      <div className="menu-toolbar">
        <nav className="menu-categories" aria-label="Filter menu by category">
          <Button
            variant={categoryId === undefined ? 'primary' : 'outline'}
            aria-pressed={categoryId === undefined}
            onClick={() => setSelectedCategory(null)}
          >
            All dishes
          </Button>
          {categories.map((category) => (
            <Button
              key={category.id}
              variant={categoryId === category.id ? 'primary' : 'outline'}
              aria-pressed={categoryId === category.id}
              onClick={() => setSelectedCategory({ country, id: category.id })}
            >
              {category.name}
            </Button>
          ))}
        </nav>
        <p className="menu-toolbar__country">
          Prices for {country}
        </p>
      </div>

      {isLoading && <Loader label="Loading the menu" />}
      {error && (
        <div className="menu-page__error">
          <ErrorMessage title="Menu unavailable">{error}</ErrorMessage>
          <Button variant="outline" onClick={() => setRetryCount((count) => count + 1)}>
            Try again
          </Button>
        </div>
      )}
      {!isLoading && !error && products.length === 0 && (
        <div className="menu-empty">
          <h2>No dishes found</h2>
          <p>There are no available dishes for this selection.</p>
        </div>
      )}
      {!isLoading && !error && products.length > 0 && (
        <div className="menu-grid">
          {products.map((product) => (
            <article className="menu-card" key={product.id}>
              {product.imageUrl ? (
                <img
                  className="menu-card__image"
                  src={product.imageUrl}
                  alt={product.name}
                  loading="lazy"
                />
              ) : (
                <div className="menu-card__image menu-card__image--placeholder" aria-hidden="true">
                  ✳
                </div>
              )}
              <div className="menu-card__content">
                <div className="menu-card__heading">
                  <h2>{product.name}</h2>
                  {!product.inStock && <span className="menu-card__stock">Out of stock</span>}
                </div>
                {product.description && <p>{product.description}</p>}
                <div className="menu-card__footer">
                  <strong>{formatPrice(product.price, product.currency)}</strong>
                  <Button
                    className="menu-card__action"
                    variant="outline"
                    size="small"
                    aria-label={`${product.hasModifiers ? 'Customize' : 'Details'}, ${product.name}`}
                    onClick={() => setSelectedProductId(product.id)}
                  >
                    <span className="menu-card__action-text">
                      {product.hasModifiers ? 'Customize' : 'Details'}
                    </span>
                    <Icon className="menu-card__action-icon" name="plus" />
                  </Button>
                </div>
              </div>
            </article>
          ))}
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
