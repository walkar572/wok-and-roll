import { Link } from 'react-router-dom';

interface PagePlaceholderProps {
  eyebrow: string;
  title: string;
  description: string;
  symbol: string;
  panelTitle: string;
  panelDescription: string;
  actionLabel: string;
  actionTo: string;
}

export function PagePlaceholder({
  eyebrow,
  title,
  description,
  symbol,
  panelTitle,
  panelDescription,
  actionLabel,
  actionTo,
}: PagePlaceholderProps) {
  return (
    <section className="placeholder-page" aria-labelledby="page-title">
      <p className="eyebrow">{eyebrow}</p>
      <h1 id="page-title" className="placeholder-page__title">{title}</h1>
      <p className="placeholder-page__description">{description}</p>
      <div className="empty-state">
        <span className="empty-state__symbol" aria-hidden="true">{symbol}</span>
        <h2>{panelTitle}</h2>
        <p>{panelDescription}</p>
        <Link className="button button--primary" to={actionTo}>{actionLabel}</Link>
      </div>
    </section>
  );
}
