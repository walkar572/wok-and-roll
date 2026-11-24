import { useId } from 'react';
import { useCountry } from '../../context/CountryContext';
import { cityName, countryName, useI18n } from '../../i18n';
import { Icon } from '../common/Icon';

/**
 * Shown instead of prices while "All cities" is chosen: the menu, the cart, checkout and booking
 * need one city (its prices, currency and cafés). One tap on a city chooses it for the whole site.
 */
export function ChooseCity({ compact = false }: { compact?: boolean }) {
  const { cities, setCity } = useCountry();
  const { t } = useI18n();
  const titleId = useId();

  return (
    <section
      className={`choose-city${compact ? ' choose-city--compact' : ''}`}
      aria-labelledby={titleId}
    >
      <h2 id={titleId}>{t('chooseCity.title')}</h2>
      <p>{t('chooseCity.text')}</p>
      <ul className="choose-city__list">
        {cities.map((item) => (
          <li key={`${item.country.code}:${item.name}`}>
            <button type="button" className="choose-city__option" onClick={() => setCity(item)}>
              <Icon name="pin" />
              <span className="choose-city__text">
                <strong>{cityName(item.name)}</strong>
                <span>
                  {countryName(item.country.code, item.country.name)} · {item.country.currency}
                </span>
              </span>
            </button>
          </li>
        ))}
      </ul>
    </section>
  );
}
