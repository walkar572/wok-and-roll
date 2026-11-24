import { useId } from 'react';

// Five lanterns; where each hangs, how far it goes down and how fast is in the CSS (.hero-lantern
// and its nth-child rules), so the layout can change by screen width.
const LANTERNS = 5;

/**
 * Red paper lanterns that hang on threads from under the header, go slowly down and up again and
 * sway a little: the mood of the brand, like the falling petals of a sushi bar. Pure decoration:
 * hidden from screen readers, never in the way of a click, and still for reduced motion.
 */
export function HeroLanterns() {
  const id = useId().replace(/:/g, '');
  const lantern = `${id}-lantern`;
  const glow = `${id}-glow`;

  return (
    <div className="hero-lanterns" aria-hidden="true">
      <svg className="hero-lanterns__defs" width="0" height="0" focusable="false">
        <defs>
          <radialGradient id={glow} cx="45%" cy="40%" r="62%">
            <stop offset="0" stopColor="#f6c39c" />
            <stop offset="0.35" stopColor="#e0624a" />
            <stop offset="0.75" stopColor="#b43b2c" />
            <stop offset="1" stopColor="#7e281e" />
          </radialGradient>
          <symbol id={lantern} viewBox="0 0 48 80">
            <rect x="15" y="6" width="18" height="7" rx="2" fill="#d0a352" />
            <ellipse cx="24" cy="31" rx="21" ry="19" fill={`url(#${glow})`} />
            <g fill="none" stroke="#7e281e" strokeWidth="1.2" opacity="0.55">
              <ellipse cx="24" cy="31" rx="13" ry="19" />
              <ellipse cx="24" cy="31" rx="5" ry="19" />
            </g>
            <rect x="15" y="49" width="18" height="7" rx="2" fill="#d0a352" />
            <path d="M24 0v6M24 56v5" stroke="#d0a352" strokeWidth="2" />
            <circle cx="24" cy="63" r="2.6" fill="#d0a352" />
            <path
              d="M22 65l-1.5 14M24 65v15M26 65l1.5 14"
              stroke="#d0a352"
              strokeWidth="1.4"
              strokeLinecap="round"
            />
          </symbol>
        </defs>
      </svg>
      {Array.from({ length: LANTERNS }, (_, index) => (
        <span key={index} className="hero-lantern">
          <span className="hero-lantern__sway">
            <svg className="hero-lantern__body" viewBox="0 0 48 80" focusable="false">
              <use href={`#${lantern}`} />
            </svg>
          </span>
        </span>
      ))}
    </div>
  );
}
