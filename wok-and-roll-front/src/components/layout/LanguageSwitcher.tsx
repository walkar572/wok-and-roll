import * as Select from '@radix-ui/react-select';
import { useId } from 'react';
import { Icon } from '../common/Icon';
import { isLocale, LANGUAGE_CODE, LANGUAGE_NAME, LOCALES, INTL_LOCALE } from '../../i18n/locales';
import { useI18n } from '../../i18n';

/**
 * The language switcher in the header: only the texts change, the city and its prices stay
 * (contract v1.9). Every language is written in itself ("Oʻzbekcha", "English (US)"), so a guest
 * who does not read the current language still finds their own. Radix Select gives the listbox
 * keyboard support (arrows, Home/End, typing, Escape) and the ARIA roles.
 */
export function LanguageSwitcher({
  onOpenChange,
}: {
  /** The header closes its mobile menu while the list is open. */
  onOpenChange?: (open: boolean) => void;
}) {
  const { locale, setLanguage, t } = useI18n();
  const idBase = useId();

  return (
    <div className="country-control language-control">
      <Select.Root
        value={locale}
        onOpenChange={onOpenChange}
        onValueChange={(value) => {
          if (isLocale(value)) setLanguage(value);
        }}
      >
        <Select.Trigger
          className="country-trigger"
          aria-label={t('nav.languageCurrent', { name: LANGUAGE_NAME[locale] })}
        >
          <Icon className="country-trigger__globe" name="globe" />
          <Select.Value>{LANGUAGE_CODE[locale]}</Select.Value>
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
                <Select.Label className="country-menu__title">{t('nav.languageList')}</Select.Label>
                {LOCALES.map((item) => {
                  const id = `${idBase}-${item}`;
                  return (
                    <Select.Item
                      key={item}
                      className="country-option"
                      value={item}
                      textValue={LANGUAGE_NAME[item]}
                      // Radix names the option by its code only; the name too.
                      aria-labelledby={`${id}-code ${id}-name`}
                    >
                      <span className="country-option__code" id={`${id}-code`}>
                        <Select.ItemText>{LANGUAGE_CODE[item]}</Select.ItemText>
                      </span>
                      <span className="country-option__text" lang={INTL_LOCALE[item]}>
                        <span className="country-option__name" id={`${id}-name`}>
                          {LANGUAGE_NAME[item]}
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
    </div>
  );
}
