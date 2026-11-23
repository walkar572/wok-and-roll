import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Button, ErrorMessage, Loader } from '../../components/common';
import { Icon } from '../../components/common/Icon';
import { useCountry } from '../../context/CountryContext';
import { formatPrice, guestErrorMessage, loadProducts, type Product } from '../../services/api';

export function HomePage() {
  const { country } = useCountry();
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
        setError(
          guestErrorMessage(requestError, 'We could not load our favorites. Please try again.'),
        );
      })
      .finally(() => {
        if (active) setIsLoading(false);
      });

    return () => {
      active = false;
    };
  }, [country, retryCount]);

  return (
    <div className="home-page">
      <section className="home-hero" aria-labelledby="home-title">
        <div className="home-hero__copy">
          <p className="eyebrow">Japanese soul. Wok-fired energy.</p>
          <h1 id="home-title">
            Wok.
            <br />
            Roll. Repeat.
          </h1>
          <p className="home-hero__description">
            Sushi, sizzling noodles and a little something worth slowing down for.
          </p>
          <Link className="button button--primary" to="/menu">
            Find your favorite <Icon name="arrow" />
          </Link>
        </div>
        <img
          className="home-hero__image"
          src="/images/salmon-roll.png"
          alt="Salmon and avocado sushi rolls on a ceramic plate"
          width="512"
          height="512"
        />
      </section>

      <section className="home-shortcuts" aria-labelledby="favorites-title">
        <div className="section-heading">
          <div>
            <p className="eyebrow">From our kitchen</p>
            <h2 id="favorites-title">A few favorites</h2>
          </div>
          <Link to="/menu">
            View the menu <Icon name="arrow" />
          </Link>
        </div>
        {isLoading && <Loader label="Loading favorites" />}
        {error && (
          <div className="home-shortcuts__error">
            <ErrorMessage title="Favorites unavailable">{error}</ErrorMessage>
            <Button variant="outline" onClick={() => setRetryCount((count) => count + 1)}>
              Try again
            </Button>
          </div>
        )}
        {!isLoading && !error && favorites.length === 0 && (
          <p>No featured dishes are available for {country}.</p>
        )}
        {!isLoading && !error && favorites.length > 0 && (
          <div className="favorite-grid">
            {favorites.map((item) => (
              <Link className="favorite-card" to="/menu" key={item.id}>
                {item.imageUrl ? (
                  <img
                    src={item.imageUrl}
                    alt={item.name}
                    width="512"
                    height="512"
                    loading="lazy"
                  />
                ) : (
                  <span className="favorite-card__image-placeholder" aria-hidden="true">
                    ✳
                  </span>
                )}
                <div>
                  <h3>{item.name}</h3>
                  {item.description && <p>{item.description}</p>}
                  <span>{formatPrice(item.price, item.currency)}</span>
                </div>
              </Link>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
