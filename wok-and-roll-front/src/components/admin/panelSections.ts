import type { MessageKey } from '../../i18n';
import type { Role } from '../../services/authToken';
import type { IconName } from '../common/Icon';

export interface PanelSection {
  path: string;
  label: MessageKey;
  icon: IconName;
  /** The same roles as the API behind the section (API contract §8). */
  roles: Role[];
}

/** Who can open the panel at all. A customer is sent to the panel login with a message. */
export const PANEL_ROLES: Role[] = ['ADMIN', 'STAFF', 'VIEWER'];

// The admin sees everything; staff only the café work list; a viewer only the reports.
export const PANEL_SECTIONS: PanelSection[] = [
  { path: '/admin', label: 'panel.sections.overview', icon: 'grid', roles: ['ADMIN'] },
  { path: '/admin/orders', label: 'panel.sections.orders', icon: 'receipt', roles: ['ADMIN'] },
  { path: '/admin/bookings', label: 'panel.sections.bookings', icon: 'calendar', roles: ['ADMIN'] },
  { path: '/admin/users', label: 'panel.sections.users', icon: 'users', roles: ['ADMIN'] },
  { path: '/admin/cafes', label: 'panel.sections.cafes', icon: 'store', roles: ['ADMIN'] },
  { path: '/admin/menu', label: 'panel.sections.menu', icon: 'bowl', roles: ['ADMIN'] },
  {
    path: '/admin/cafe-orders',
    label: 'panel.sections.cafeOrders',
    icon: 'clock',
    roles: ['ADMIN', 'STAFF'],
  },
  {
    path: '/admin/reports',
    label: 'panel.sections.reports',
    icon: 'chart',
    roles: ['ADMIN', 'VIEWER'],
  },
];

export function isPanelRole(role: Role | undefined): role is Role {
  return role !== undefined && PANEL_ROLES.includes(role);
}

export function sectionsFor(role: Role): PanelSection[] {
  return PANEL_SECTIONS.filter((section) => section.roles.includes(role));
}

/** The first section a role can open: the overview for an admin. */
export function panelHome(role: Role): string {
  return sectionsFor(role)[0]?.path ?? '/';
}

/** The section of a path: "/admin/orders/WR-1" belongs to "/admin/orders". */
export function sectionOf(pathname: string): PanelSection | undefined {
  return [...PANEL_SECTIONS]
    .sort((a, b) => b.path.length - a.path.length)
    .find((section) =>
      section.path === '/admin'
        ? pathname === '/admin' || pathname === '/admin/'
        : pathname === section.path || pathname.startsWith(`${section.path}/`),
    );
}
