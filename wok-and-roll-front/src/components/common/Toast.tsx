import type { HTMLAttributes, ReactNode } from 'react';
import { useI18n } from '../../i18n';

export type ToastVariant = 'success' | 'error' | 'warning' | 'info';

export interface ToastProps extends Omit<HTMLAttributes<HTMLDivElement>, 'title'> {
  children: ReactNode;
  title?: string;
  variant?: ToastVariant;
  onDismiss?: () => void;
  dismissLabel?: string;
}

export function Toast({
  children,
  title,
  variant = 'info',
  onDismiss,
  dismissLabel,
  className = '',
  ...props
}: ToastProps) {
  const { t } = useI18n();
  const classes = ['toast', `toast--${variant}`, className].filter(Boolean).join(' ');

  return (
    <div
      className={classes}
      role={variant === 'error' ? 'alert' : 'status'}
      aria-live={variant === 'error' ? 'assertive' : 'polite'}
      {...props}
    >
      <div className="toast__content">
        {title && <strong className="toast__title">{title}</strong>}
        <div>{children}</div>
      </div>
      {onDismiss && (
        <button
          className="toast__dismiss"
          type="button"
          aria-label={dismissLabel ?? t('common.dismiss')}
          onClick={onDismiss}
        >
          ×
        </button>
      )}
    </div>
  );
}
