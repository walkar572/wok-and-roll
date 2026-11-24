import { useId } from 'react';

/**
 * An openwork Chinese railing along the top edge of the dish photo: thin gold rails and the
 * square "回" fret between them, drawn with lines only over a soft shade that fades out, so the
 * photo shows through. The top rail runs along the very edge. It sits under the price card.
 * Decoration only, hidden from screen readers.
 */
export function HeroFence() {
  const fret = `${useId().replace(/:/g, '')}-fret`;

  return (
    <div className="hero-fence" aria-hidden="true">
      <svg width="100%" height="48" focusable="false">
        <defs>
          <pattern id={fret} width="30" height="48" patternUnits="userSpaceOnUse">
            <path className="hero-fence__fret" d="M4 37V8H27V32H11V16H20V25" />
          </pattern>
        </defs>
        <rect width="100%" height="48" fill={`url(#${fret})`} />
        <rect className="hero-fence__rail" y="1" width="100%" height="2" />
        <rect className="hero-fence__rail" y="36" width="100%" height="2" />
        <rect
          className="hero-fence__rail hero-fence__rail--thin"
          y="42"
          width="100%"
          height="1.2"
        />
      </svg>
    </div>
  );
}
