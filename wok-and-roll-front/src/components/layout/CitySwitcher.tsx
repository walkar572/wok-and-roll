import * as Select from '@radix-ui/react-select';
import { useId } from 'react';
import { Icon } from '../common/Icon';
import { useCountry } from '../../context/CountryContext';
import { cityName, countryName, useI18n } from '../../i18n';

/** The value of a city in the list: a city name is unique only inside its country. */
function cityKey(country: string, city: string): string {
  return `${country}:${city}`;
}

/**
 * The city switcher (contract v1.9). The city gives the country: its prices and currency, its
 * cafés with their phones and hours, its delivery zones. The language does not change. Used in the
 * header and above the cafés on the home page; both show the same choice.
 */
export function CitySwitcher({
  variant = 'header',
  onOpenChange,
}: {
  /** header: in the header bar; section: above the cafés; menu: in the black mobile menu. */
  variant?: 'header' | 'section' | 'menu';
  /** The header closes its mobile menu while the list is open. */
  onOpenChange?: (open: boolean) => void;
}) {
  const { cities, city, country, currentCountry, isLoadingCountries, countriesError, setCity } =
    useCountry();
  const { t } = useI18n();
  const idBase = useId();
  const current = city ? cityName(city) : countryName(country, currentCountry.name);
  const label = t('nav.cityCurrent', {
    name: city ? `${current}, ${countryName(country, currentCountry.name)}` : current,
  });

  return (
    <div className={`country-control city-control city-control--${variant}`}>
      {variant !== 'header' && (
        <span className="city-control__label" aria-hidden="true">
          {t('nav.city')}
        </span>
      )}
      <Select.Root
        value={city ? cityKey(country, city) : undefined}
        onOpenChange={onOpenChange}
        onValueChange={(value) => {
          const next = cities.find((item) => cityKey(item.country.code, item.name) === value);
          if (next) setCity(next);
        }}
      >
        <Select.Trigger className="country-trigger city-trigger" aria-label={label}>
          <Icon className="country-trigger__globe" name="pin" />
          <span className="city-trigger__name">
            <Select.Value>{current}</Select.Value>
          </span>
          <Select.Icon className="country-trigger__chevron">
            <Icon name="chevron" />
          </Select.Icon>
        </Select.Trigger>
        <Select.Portal>
          <Select.Content
            className="country-menu"
            position="popper"
            side="bottom"
            align={variant === 'header' ? 'end' : 'start'}
            sideOffset={8}
            collisionPadding={12}
          >
            <Select.Viewport className="country-menu__list">
              <Select.Group>
                <Select.Label className="country-menu__title">{t('nav.cityList')}</Select.Label>
                {cities.map((item) => {
                  const value = cityKey(item.country.code, item.name);
                  const id = `${idBase}-${item.country.code}-${item.name.replace(/\W/g, '')}`;
                  return (
                    <Select.Item
                      key={value}
                      className="country-option city-option"
                      value={value}
                      textValue={cityName(item.name)}
                      aria-labelledby={`${id}-name ${id}-meta`}
                    >
                      <span className="country-option__text">
                        <span className="country-option__name" id={`${id}-name`}>
                          <Select.ItemText>{cityName(item.name)}</Select.ItemText>
                        </span>
                        <span className="country-option__meta" id={`${id}-meta`}>
                          {countryName(item.country.code, item.country.name)} ·{' '}
                          {item.country.currency}
                        </span>
                      </span>
                      <Select.ItemIndicator className="country-option__check">
                        <Icon name="check" />
                      </Select.ItemIndicator>
                    </Select.Item>
                  );
                })}
              </Select.Group>
            </Select.Viewport>
          </Select.Content>
        </Select.Portal>
      </Select.Root>
      {variant === 'header' &&
        (isLoadingCountries ? (
          <span className="country-control__status" role="status">
            {t('nav.loadingCountries')}
          </span>
        ) : countriesError ? (
          <span className="country-control__status" role="status" title={countriesError}>
            {import.meta.env.DEV ? t('nav.sampleData') : t('nav.countryUnavailable')}
          </span>
        ) : null)}
    </div>
  );
}
