import { Link } from 'react-router-dom';
import { useI18n } from '../../i18n';

export function Footer() {
  const { t } = useI18n();
  return (
    <footer className="site-footer">
      <Link className="site-footer__brand" to="/">
        Wok <span>&</span> Roll
      </Link>
      <p>{t('nav.tagline')}</p>
      <span className="site-footer__copyright">© {new Date().getFullYear()} Wok & Roll</span>
    </footer>
  );
}
