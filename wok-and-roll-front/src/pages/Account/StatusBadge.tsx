import { useI18n } from '../../i18n';
import type { OrderStatus } from '../../services/api';

const SYMBOLS: Record<OrderStatus, string> = {
  NEW: '●',
  CONFIRMED: '◆',
  DONE: '✓',
  CANCELLED: '✕',
};

/** The order status as text with a symbol, never by colour alone. Unknown values show as text. */
export function StatusBadge({ status }: { status: OrderStatus | string }) {
  const { t } = useI18n();
  const known = status in SYMBOLS ? (status as OrderStatus) : null;
  return (
    <span className={`status-badge status-badge--${known?.toLowerCase() ?? 'unknown'}`}>
      <span aria-hidden="true">{known ? SYMBOLS[known] : '•'}</span>
      {known ? t(`status.${known}`) : status}
    </span>
  );
}
