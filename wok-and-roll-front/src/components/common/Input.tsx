import { useId, type InputHTMLAttributes } from 'react';

export type InputStatus = 'default' | 'success' | 'error';

export interface InputProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'id'> {
  label: string;
  id?: string;
  hint?: string;
  error?: string;
  status?: InputStatus;
}

export function Input({
  label,
  id,
  hint,
  error,
  status = 'default',
  disabled,
  className = '',
  ...props
}: InputProps) {
  const generatedId = useId();
  const inputId = id ?? generatedId;
  const message = error ?? hint;
  const messageId = message ? `${inputId}-message` : undefined;
  const resolvedStatus = error ? 'error' : status;
  const classes = ['input__control', `input__control--${resolvedStatus}`, className]
    .filter(Boolean)
    .join(' ');

  return (
    <div className={`input${disabled ? ' input--disabled' : ''}`}>
      <label className="input__label" htmlFor={inputId}>
        {label}
      </label>
      <input
        className={classes}
        id={inputId}
        disabled={disabled}
        aria-invalid={resolvedStatus === 'error' || undefined}
        aria-describedby={messageId}
        {...props}
      />
      {message && (
        <span className={`input__message input__message--${resolvedStatus}`} id={messageId}>
          {message}
        </span>
      )}
    </div>
  );
}
