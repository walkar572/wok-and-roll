import { useId } from 'react';

/**
 * A Chinese railing across the top of the dish photo: red rails and posts with gold knobs and a
 * fret lattice (the square "回" spiral) between them. It sits over the photo and under the price
 * card. Decoration only, hidden from screen readers.
 */
export function HeroFence() {
  const bay = `${useId().replace(/:/g, '')}-bay`;
  // One bay is a post and three spirals; the pattern repeats it across any width.
  const spirals = [8, 32, 56];

  return (
    <div className="hero-fence" aria-hidden="true">
      <svg width="100%" height="100%" focusable="false">
        <defs>
          <pattern id={bay} width="78" height="52" patternUnits="userSpaceOnUse">
            {spirals.map((x) => (
              <path key={x} className="hero-fence__fret" d={`M${x} 39V19h20v20h-14v-14h8v8`} />
            ))}
            <rect className="hero-fence__post" x="0" y="8" width="6" height="44" />
            <circle className="hero-fence__knob" cx="3" cy="6" r="4" />
          </pattern>
        </defs>
        <rect width="100%" height="52" fill={`url(#${bay})`} />
        <rect className="hero-fence__rail" y="12" width="100%" height="5" />
        <rect className="hero-fence__line" y="12" width="100%" height="1" />
        <rect className="hero-fence__rail" y="40" width="100%" height="5" />
        <rect className="hero-fence__line" y="44" width="100%" height="1" />
      </svg>
    </div>
  );
}
