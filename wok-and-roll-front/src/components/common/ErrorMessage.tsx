import type { ReactNode } from 'react';

export type MessageVariant = 'error' | 'success' | 'warning' | 'info';

export interface ErrorMessageProps {
  children: ReactNode;
  title?: string;
  variant?: MessageVariant;
  className?: string;
}

export function ErrorMessage({
  children,
  title,
  variant = 'error',
  className = '',
}: ErrorMessageProps) {
  const classes = ['message', `message--${variant}`, className].filter(Boolean).join(' ');

  return (
    <div className={classes} role={variant === 'error' ? 'alert' : 'status'}>
      {title && <strong className="message__title">{title}</strong>}
      <div>{children}</div>
    </div>
  );
}