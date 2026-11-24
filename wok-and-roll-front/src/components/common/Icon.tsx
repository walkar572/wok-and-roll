import type { SVGProps } from 'react';

const paths = {
  arrow: 'M4 12h16m-6-6 6 6-6 6',
  bowl: 'M3.5 11h17c0 4.7-3.8 8.5-8.5 8.5S3.5 15.7 3.5 11ZM9.5 8.5 13 3.5m1 5 5-4',
  cart: 'M3 3h2l2 12h11l3-9H6M9 20h.01M18 20h.01',
  check: 'm5 12.5 4.5 4.5L19 7.5',
  chevron: 'm6 9 6 6 6-6',
  clock: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18Zm0-13.5V12l3 2',
  close: 'M6 6l12 12M18 6 6 18',
  globe:
    'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18Zm-9-9h18M12 3c2.4 2.5 3.6 5.5 3.6 9s-1.2 6.5-3.6 9c-2.4-2.5-3.6-5.5-3.6-9S9.6 5.5 12 3Z',
  left: 'm15 18-6-6 6-6',
  menu: 'M4 6h16M4 12h16M4 18h16',
  pause: 'M9 6v12M15 6v12',
  phone:
    'M6.6 3.5H9l1.4 4.2-2.1 1.5a12 12 0 0 0 6.4 6.4l1.5-2.1 4.2 1.4v2.4a2.2 2.2 0 0 1-2.4 2.2C10.9 19 5 13.1 4.5 5.9a2.2 2.2 0 0 1 2.1-2.4Z',
  pin: 'M12 21s-7-6.1-7-11.4a7 7 0 0 1 14 0C19 14.9 12 21 12 21Zm0-8.9a2.6 2.6 0 1 0 0-5.2 2.6 2.6 0 0 0 0 5.2Z',
  play: 'M8 5.5v13l10.5-6.5L8 5.5Z',
  plus: 'M12 5v14M5 12h14',
  right: 'm9 18 6-6-6-6',
};

export type IconName = keyof typeof paths;

/** The path of an icon, for markup made outside React (the map markers). */
export function iconPath(name: IconName): string {
  return paths[name];
}

export function Icon({ name, ...props }: SVGProps<SVGSVGElement> & { name: IconName }) {
  return (
    <svg
      width="20"
      height="20"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
      {...props}
    >
      <path d={paths[name]} />
    </svg>
  );
}
