import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Button, ErrorMessage, Loader } from '../../components/common';
import { DishImage } from '../../components/common/DishImage';
import { Icon } from '../../components/common/Icon';
import { useCountry } from '../../context/CountryContext';
import { countryName, productDescription, productName, useI18n, portionText } from '../../i18n';
import { formatPrice, guestErrorMessage, loadProducts, type Product } from '../../services/api';

export function HomePage() {
  const { country } = useCountry();
  const { t } = useI18n();
  const [favorites, setFavorites] = useState<Product[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [retryCount, setRetryCount] = useState(0);

  useEffect(() => {
    let active = true;
    setIsLoading(true);
    setError(undefined);

    loadProducts(country)
      .then((products) => {
        if (active) setFavorites(products.filter(({ inStock }) => inStock).slice(0, 3));
      })
      .catch((requestError: unknown) => {
        if (!active) return;
        setError(guestErrorMessage(requestError, t('home.favoritesError')));
      })
      .finally(() => {
        if (active) setIsLoading(false);
      });

    return () => {
      active = false;
    };
  }, [country, retryCount, t]);

  return (
    <div className="home-page">
      <section className="home-hero" aria-labelledby="home-title">
        <div className="home-hero__copy">
          <p className="eyebrow">{t('home.eyebrow')}</p>
          <h1 id="home-title">
            {/* "Wok. Roll." is the brand: the same in every language, only the rest is translated. */}
            <span className="home-hero__brand" lang="en" translate="no">
              Wok.
            </span>
            <br />
            <span className="home-hero__brand" lang="en" translate="no">
              Roll.
            </span>{' '}
            {t('home.titleRest')}
          </h1>
          <p className="home-hero__description">{t('home.description')}</p>
          <Link className="button button--primary" to="/menu">
            {t('home.cta')} <Icon name="arrow" />
          </Link>
        </div>
        <img
          className="home-hero__image"
          src="/images/salmon-roll.png"
          alt={t('home.heroAlt')}
          width="512"
          height="512"
        />
      </section>

      <section className="home-shortcuts" aria-labelledby="favorites-title">
        <div className="section-heading">
          <div>
            <p className="eyebrow">{t('home.favoritesEyebrow')}</p>
            <h2 id="favorites-title">{t('home.favoritesTitle')}</h2>
          </div>
          <Link to="/menu">
            {t('home.viewMenu')} <Icon name="arrow" />
          </Link>
        </div>
        {isLoading && <Loader label={t('home.loadingFavorites')} />}
        {error && (
          <div className="home-shortcuts__error">
            <ErrorMessage title={t('home.favoritesUnavailable')}>{error}</ErrorMessage>
            <Button variant="outline" onClick={() => setRetryCount((count) => count + 1)}>
              {t('common.tryAgain')}
            </Button>
          </div>
        )}
        {!isLoading && !error && favorites.length === 0 && (
          <p>{t('home.noFavorites', { country: countryName(country) })}</p>
        )}
        {!isLoading && !error && favorites.length > 0 && (
          <div className="favorite-grid">
            {favorites.map((item) => {
              const description = productDescription(item.name, item.description);
              const portion = portionText(item);
              return (
                <Link className="favorite-card" to="/menu" key={item.id}>
                  <DishImage
                    src={item.imageUrl}
                    alt={productName(item.name)}
                    className="favorite-card__image"
                    placeholderClassName="favorite-card__image-placeholder"
                    width={512}
                    height={512}
                  />
                  <div>
                    <h3>{productName(item.name)}</h3>
                    {(description || portion) && (
                      <div className="favorite-card__text">
                        {description && <p>{description}</p>}
                        {portion && <p className="dish-portion">{portion}</p>}
                      </div>
                    )}
                    <span>{formatPrice(item.price, item.currency)}</span>
                  </div>
                </Link>
              );
            })}
          </div>
        )}
      </section>
    </div>
  );
}
