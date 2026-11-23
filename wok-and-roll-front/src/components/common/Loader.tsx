export interface LoaderProps {
  label?: string;
  size?: 'small' | 'medium' | 'large';
}

export function Loader({ label = 'Loading', size = 'medium' }: LoaderProps) {
  return (
    <span className={`loader loader--${size}`} role="status">
      <span className="loader__spinner" aria-hidden="true" />
      <span className="loader__label">{label}</span>
    </span>
  );
}
