import { useId } from 'react';

/**
 * An openwork Chinese railing along the top edge of the dish photo: thin gold rails and the
 * square "回" fret between them, drawn with lines only over a soft shade that fades out, so the
 * photo shows through. It sits under the price card. Decoration only, hidden from screen readers.
 */
export function HeroFence() {
  const fret = `${useId().replace(/:/g, '')}-fret`;

  return (
    <div className="hero-fence" aria-hidden="true">
      <svg width="100%" height="44" focusable="false">
        <defs>
          <pattern id={fret} width="24" height="44" patternUnits="userSpaceOnUse">
            <path className="hero-fence__fret" d="M3 34V14H21V30H9V20H15V25" />
          </pattern>
        </defs>
        <rect width="100%" height="44" fill={`url(#${fret})`} />
        <rect className="hero-fence__rail" y="7" width="100%" height="1.5" />
        <rect className="hero-fence__rail" y="33.5" width="100%" height="1.5" />
        <rect className="hero-fence__rail hero-fence__rail--thin" y="38" width="100%" height="1" />
      </svg>
    </div>
  );
}
