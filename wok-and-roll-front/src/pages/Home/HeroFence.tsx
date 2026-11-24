import { useId } from 'react';

/**
 * A Chinese railing across the top of the dish photo: red rails and posts with gold knobs and two
 * rows of fret lattice (the square "回" spiral, the lower row mirrored). It covers the top of the
 * photo from its edge, so the price card stands wholly in front of it. Decoration only, hidden
 * from screen readers.
 */
export function HeroFence() {
  const bay = `${useId().replace(/:/g, '')}-bay`;
  // One bay is a post and three spirals in each row; the pattern repeats it across any width.
  const spirals = [8, 32, 56];

  return (
    <div className="hero-fence" aria-hidden="true">
      <svg width="100%" height="100%" focusable="false">
        <defs>
          <pattern id={bay} width="78" height="92" patternUnits="userSpaceOnUse">
            {spirals.map((x) => (
              <path
                key={`top-${x}`}
                className="hero-fence__fret"
                d={`M${x} 42V22h20v20h-14v-14h8v8`}
              />
            ))}
            {spirals.map((x) => (
              <path
                key={`bottom-${x}`}
                className="hero-fence__fret"
                d={`M${x + 20} 78V58h-20v20h14v-14h-8v8`}
              />
            ))}
            <rect className="hero-fence__post" x="0" y="8" width="6" height="84" />
            <circle className="hero-fence__knob" cx="3" cy="6" r="4" />
          </pattern>
        </defs>
        <rect width="100%" height="92" fill={`url(#${bay})`} />
        <rect className="hero-fence__rail" y="12" width="100%" height="5" />
        <rect className="hero-fence__line" y="12" width="100%" height="1" />
        <rect className="hero-fence__rail" y="48" width="100%" height="4" />
        <rect className="hero-fence__rail" y="84" width="100%" height="5" />
        <rect className="hero-fence__line" y="88" width="100%" height="1" />
      </svg>
    </div>
  );
}
