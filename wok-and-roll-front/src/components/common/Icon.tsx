import type { SVGProps } from 'react';

const paths = {
  arrow: 'M4 12h16m-6-6 6 6-6 6',
  bowl: 'M3.5 11h17c0 4.7-3.8 8.5-8.5 8.5S3.5 15.7 3.5 11ZM9.5 8.5 13 3.5m1 5 5-4',
  calendar: 'M4.5 6.5h15v13h-15v-13ZM4.5 10.5h15M8.5 4v4M15.5 4v4',
  chart: 'M4 20h16M7 16.5v-5M12 16.5V7M17 16.5v-8',
  cart: 'M3 3h2l2 12h11l3-9H6M9 20h.01M18 20h.01',
  check: 'm5 12.5 4.5 4.5L19 7.5',
  chevron: 'm6 9 6 6 6-6',
  clock: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18Zm0-13.5V12l3 2',
  close: 'M6 6l12 12M18 6 6 18',
  external: 'M14 4h6v6M20 4l-8.5 8.5M18 14v5.5H4.5V6H10',
  grid: 'M4.5 4.5h6v6h-6v-6ZM13.5 4.5h6v6h-6v-6ZM4.5 13.5h6v6h-6v-6ZM13.5 13.5h6v6h-6v-6Z',
  globe:
    'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18Zm-9-9h18M12 3c2.4 2.5 3.6 5.5 3.6 9s-1.2 6.5-3.6 9c-2.4-2.5-3.6-5.5-3.6-9S9.6 5.5 12 3Z',
  left: 'm15 18-6-6 6-6',
  logout: 'M10 4.5H5v15h5M14.5 8l4 4-4 4M18.5 12H9.5',
  menu: 'M4 6h16M4 12h16M4 18h16',
  pause: 'M9 6v12M15 6v12',
  phone:
    'M6.6 3.5H9l1.4 4.2-2.1 1.5a12 12 0 0 0 6.4 6.4l1.5-2.1 4.2 1.4v2.4a2.2 2.2 0 0 1-2.4 2.2C10.9 19 5 13.1 4.5 5.9a2.2 2.2 0 0 1 2.1-2.4Z',
  pin: 'M12 21s-7-6.1-7-11.4a7 7 0 0 1 14 0C19 14.9 12 21 12 21Zm0-8.9a2.6 2.6 0 1 0 0-5.2 2.6 2.6 0 0 0 0 5.2Z',
  play: 'M8 5.5v13l10.5-6.5L8 5.5Z',
  plus: 'M12 5v14M5 12h14',
  receipt: 'M6 3.5h12v17l-2-1.5-2 1.5-2-1.5-2 1.5-2-1.5-2 1.5v-17ZM9 8h6M9 11.5h6M9 15h3.5',
  right: 'm9 18 6-6-6-6',
  store:
    'M4 9.5 5.5 4.5h13L20 9.5M4 9.5h16M4 9.5c0 1.4 1.1 2.5 2.7 2.5s2.6-1.1 2.6-2.5c0 1.4 1.1 2.5 2.7 2.5s2.7-1.1 2.7-2.5c0 1.4 1 2.5 2.6 2.5S20 10.9 20 9.5M5.5 12v7.5h13V12M10 19.5v-4.5h4v4.5',
  users:
    'M9 11a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7ZM3 20c0-3.3 2.7-6 6-6s6 2.7 6 6M15.5 4.3a3.5 3.5 0 0 1 0 6.4M17.5 14.4c2 .8 3.5 2.9 3.5 5.6',
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
