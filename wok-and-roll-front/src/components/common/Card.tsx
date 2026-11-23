import type { HTMLAttributes } from 'react';

export type CardProps = HTMLAttributes<HTMLElement>;

export function Card({ className = '', ...props }: CardProps) {
  const classes = ['card', className].filter(Boolean).join(' ');

  return <article className={classes} {...props} />;
}
