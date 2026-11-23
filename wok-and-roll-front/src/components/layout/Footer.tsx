import { Link } from 'react-router-dom';

export function Footer() {
  return (
    <footer className="site-footer">
      <Link className="site-footer__brand" to="/">
        Wok <span>&</span> Roll
      </Link>
      <p>Good food, no fuss.</p>
      <span className="site-footer__copyright">© {new Date().getFullYear()} Wok & Roll</span>
    </footer>
  );
}
