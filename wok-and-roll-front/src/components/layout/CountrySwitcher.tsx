import * as Select from '@radix-ui/react-select';
import { useId } from 'react';
import { Icon } from '../common/Icon';
import { useCountry } from '../../context/CountryContext';
import { countryEndonym, countryName, languageOfCountry, useI18n } from '../../i18n';

/**
 * The country switcher in the header. The country also sets the language and the currency, so
 * every option shows them in its own language ("United Kingdom · English · GBP"): a guest who does
 * not read the current language still finds their own. Radix Select gives the listbox keyboard
 * support (arrows, Home/End, typing, Escape) and the ARIA roles.
 */
export function CountrySwitcher({
  onOpenChange,
}: {
  /** The header closes its mobile menu while the list is open. */
  onOpenChange?: (open: boolean) => void;
} = {}) {
  const { country, countries, currentCountry, isLoadingCountries, countriesError, setCountry } =
    useCountry();
  const { t } = useI18n();
  const idBase = useId();

  return (
    <div className="country-control">
      <Select.Root
        value={country}
        onOpenChange={onOpenChange}
        onValueChange={(code) => {
          if (countries.some((item) => item.code === code)) setCountry(code);
        }}
      >
        <Select.Trigger
          className="country-trigger"
          aria-label={t('nav.countryCurrent', { name: countryName(country, currentCountry.name) })}
        >
          <Icon className="country-trigger__globe" name="globe" />
          <Select.Value>{country}</Select.Value>
          <Select.Icon className="country-trigger__chevron">
            <Icon name="chevron" />
          </Select.Icon>
        </Select.Trigger>
        <Select.Portal>
          <Select.Content
            className="country-menu"
            position="popper"
            side="bottom"
            align="end"
            sideOffset={8}
            collisionPadding={12}
          >
            <Select.Viewport className="country-menu__list">
              <Select.Group>
                <Select.Label className="country-menu__title">
                  {t('nav.countryLanguage')}
                </Select.Label>
                {countries.map((item) => {
                  const language = languageOfCountry(item.code);
                  const id = `${idBase}-${item.code}`;
                  return (
                    <Select.Item
                      key={item.code}
                      className="country-option"
                      value={item.code}
                      textValue={countryEndonym(item.code, item.name)}
                      // Radix names the option by its code only; the name and the language too.
                      aria-labelledby={`${id}-code ${id}-name ${id}-meta`}
                    >
                      <span className="country-option__code" id={`${id}-code`}>
                        <Select.ItemText>{item.code}</Select.ItemText>
                      </span>
                      <span className="country-option__text" lang={language.tag}>
                        <span className="country-option__name" id={`${id}-name`}>
                          {countryEndonym(item.code, item.name)}
                        </span>
                        <span className="country-option__meta" id={`${id}-meta`}>
                          {language.name} · {item.currency}
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
      {isLoadingCountries ? (
        <span className="country-control__status" role="status">
          {t('nav.loadingCountries')}
        </span>
      ) : countriesError ? (
        <span className="country-control__status" role="status" title={countriesError}>
          {import.meta.env.DEV ? t('nav.sampleData') : t('nav.countryUnavailable')}
        </span>
      ) : null}
    </div>
  );
}
