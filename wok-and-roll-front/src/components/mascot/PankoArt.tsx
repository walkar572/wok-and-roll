/**
 * Panko, the Wok & Roll chef: a red panda in a chef's jacket and toque with a wok over the fire.
 * Drawn in SVG in the brand colours (rust fur, rice white, brand red, saffron). The moving parts
 * are groups with their own class: the CSS animates them (breathing, tail, blink, the wok toss,
 * the flame, the steam, the waving paw). Decoration: the button around it has the name.
 */
export function PankoArt() {
  return (
    <svg className="panko" viewBox="0 0 170 170" focusable="false" aria-hidden="true">
      {/* the shadow on the floor breathes with him */}
      <ellipse className="panko__shadow" cx="84" cy="163" rx="56" ry="5" />

      {/* the fire under the wok */}
      <g className="panko__flame">
        <path d="M131 158c-5-4-6-9-2-14 1 4 3 5 5 5 0-4 2-7 5-9-1 5 2 8 2 12 0 5-5 8-10 6z" />
        <path
          className="panko__flame-core"
          d="M134 157c-2-2-2-5 0-7 1 2 2 3 3 3 0-2 1-3 2-4 0 3 1 4 1 6 0 3-3 4-6 2z"
        />
        <path d="M147 158c-4-3-5-7-2-11 1 3 2 4 4 4 0-3 2-5 4-7-1 4 1 6 1 9 0 4-4 7-7 5z" />
      </g>

      <g className="panko__feet">
        <ellipse cx="66" cy="157" rx="12" ry="6.5" />
        <ellipse cx="92" cy="157" rx="12" ry="6.5" />
      </g>

      <g className="panko__bob">
        {/* the striped tail behind him, wagging from its base */}
        <g className="panko__tail">
          <path className="panko__tail-fur" d="M60 140C34 142 18 122 27 96" />
          <path className="panko__tail-rings" d="M60 140C34 142 18 122 27 96" />
          <circle className="panko__tail-tip" cx="27" cy="96" r="9.5" />
        </g>

        {/* chef's jacket with two rows of saffron buttons */}
        <path
          className="panko__jacket"
          d="M53 112c0-12 12-17 25-17s25 5 25 17l2 37c0 6-4 9-10 9H62c-6 0-10-3-10-9z"
        />
        <path className="panko__jacket-fold" d="M78 102v54" />
        <g className="panko__buttons">
          <circle cx="71" cy="121" r="2.4" />
          <circle cx="71" cy="134" r="2.4" />
          <circle cx="85" cy="121" r="2.4" />
          <circle cx="85" cy="134" r="2.4" />
        </g>

        {/* the paw that waves */}
        <g className="panko__wave">
          <path className="panko__sleeve" d="M57 108c-10 6-14 14-13 24" />
          <circle className="panko__paw" cx="44" cy="134" r="6.5" />
        </g>

        {/* the paw with the wok, and the wok with its food: the toss lifts the food */}
        <g className="panko__wok">
          <path className="panko__sleeve" d="M99 108c8 3 12 9 13 16" />
          <path className="panko__handle" d="M110 126h16" />
          <g className="panko__food">
            <path className="panko__noodles" d="M128 121c3-10 8-10 10 0 2-10 8-10 10 0" />
            <path className="panko__noodles" d="M133 121c2-7 6-7 8 0" />
            <path className="panko__shrimp" d="M150 118c-5-6-1-12 4-10-3 2-3 6 0 9z" />
            <circle className="panko__onion" cx="137" cy="112" r="1.8" />
            <circle className="panko__onion" cx="145" cy="114" r="1.6" />
          </g>
          <path className="panko__pan" d="M121 121h42c-3 14-10 21-21 21s-18-7-21-21z" />
          <path className="panko__rim" d="M120 121h44" />
          <circle className="panko__paw" cx="112" cy="126" r="6.5" />
        </g>

        {/* red neckerchief, knotted */}
        <path className="panko__scarf" d="M61 99h34l-17 15z" />
        <circle className="panko__knot" cx="78" cy="102" r="5" />

        {/* the head */}
        <g className="panko__head">
          <g className="panko__ears">
            <path className="panko__ear" d="M43 50c-8-12-6-28 8-30 6 6 9 15 8 24z" />
            <path className="panko__ear-in" d="M47 44c-4-7-3-16 4-18 3 4 5 10 4 15z" />
            <path className="panko__ear" d="M113 50c8-12 6-28-8-30-6 6-9 15-8 24z" />
            <path className="panko__ear-in" d="M109 44c4-7 3-16-4-18-3 4-5 10-4 15z" />
          </g>
          <ellipse className="panko__fur" cx="78" cy="64" rx="41" ry="34" />
          {/* white cheeks, muzzle and brows, the dark "tear" marks of a red panda */}
          <ellipse className="panko__white" cx="51" cy="74" rx="13" ry="11" />
          <ellipse className="panko__white" cx="105" cy="74" rx="13" ry="11" />
          <ellipse className="panko__white" cx="78" cy="78" rx="18" ry="13" />
          <path className="panko__mark" d="M62 66c1 7 3 12 7 15" />
          <path className="panko__mark" d="M94 66c-1 7-3 12-7 15" />
          <ellipse className="panko__white" cx="64" cy="50" rx="6.5" ry="4" />
          <ellipse className="panko__white" cx="92" cy="50" rx="6.5" ry="4" />
          <g className="panko__eyes">
            <ellipse cx="64" cy="62" rx="5.2" ry="6.2" />
            <ellipse cx="92" cy="62" rx="5.2" ry="6.2" />
            <circle className="panko__glint" cx="66" cy="59.5" r="1.8" />
            <circle className="panko__glint" cx="94" cy="59.5" r="1.8" />
          </g>
          <path className="panko__nose" d="M72 72c4-3 8-3 12 0-1 4-3 6-6 6s-5-2-6-6z" />
          <path className="panko__mouth" d="M72 82c2 3 4 3 6 0 2 3 4 3 6 0" />
          <ellipse className="panko__blush" cx="54" cy="80" rx="5" ry="3" />
          <ellipse className="panko__blush" cx="102" cy="80" rx="5" ry="3" />

          {/* the chef's toque, a little on one side */}
          <g className="panko__hat">
            <circle cx="66" cy="20" r="10" />
            <circle cx="79" cy="14" r="12" />
            <circle cx="92" cy="20" r="10" />
            <rect x="59" y="20" width="40" height="13" rx="3" />
            <rect className="panko__hat-band" x="59" y="26" width="40" height="4" />
          </g>
        </g>

        {/* steam over the wok */}
        <g className="panko__steam">
          <path d="M134 104c-4-5 4-8 0-14" />
          <path d="M146 106c-4-5 4-8 0-14" />
        </g>
      </g>
    </svg>
  );
}
