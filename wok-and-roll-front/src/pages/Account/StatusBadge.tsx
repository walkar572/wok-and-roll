import type { OrderStatus } from '../../services/api';

const LABELS: Record<OrderStatus, string> = {
  NEW: 'New',
  CONFIRMED: 'Confirmed',
  DONE: 'Done',
  CANCELLED: 'Cancelled',
};

const SYMBOLS: Record<OrderStatus, string> = {
  NEW: '●',
  CONFIRMED: '◆',
  DONE: '✓',
  CANCELLED: '✕',
};

/** The order status as text with a symbol, never by colour alone. Unknown values show as text. */
export function StatusBadge({ status }: { status: OrderStatus | string }) {
  const known = status in LABELS ? (status as OrderStatus) : null;
  return (
    <span className={`status-badge status-badge--${known?.toLowerCase() ?? 'unknown'}`}>
      <span aria-hidden="true">{known ? SYMBOLS[known] : '•'}</span>
      {known ? LABELS[known] : status}
    </span>
  );
}
