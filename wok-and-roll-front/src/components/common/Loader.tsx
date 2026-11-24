import { useI18n } from '../../i18n';

export interface LoaderProps {
  label?: string;
  size?: 'small' | 'medium' | 'large';
}

export function Loader({ label, size = 'medium' }: LoaderProps) {
  const { t } = useI18n();
  return (
    <span className={`loader loader--${size}`} role="status">
      <span className="loader__spinner" aria-hidden="true" />
      <span className="loader__label">{label ?? t('common.loading')}</span>
    </span>
  );
}
