import type { ReactNode } from 'react';
import { createBrowserRouter, Navigate } from 'react-router-dom';
import { AdminLayout, PanelSectionGuard } from '../components/admin/AdminLayout';
import { MainLayout } from '../components/layout/MainLayout';
import { RequireRole } from '../components/layout/RequireRole';
import type { Role } from '../services/authToken';
import { AccountPage } from '../pages/Account/AccountPage';
import { OrderDetailsPage } from '../pages/Account/OrderDetailsPage';
import { AdminCatalogPage } from '../pages/Admin/AdminCatalogPage';
import { AdminLoginPage } from '../pages/AdminPanel/AdminLoginPage';
import { BookingsPage } from '../pages/AdminPanel/BookingsPage';
import { CafesPage } from '../pages/AdminPanel/CafesPage';
import { OrderPage } from '../pages/AdminPanel/OrderPage';
import { OrdersPage } from '../pages/AdminPanel/OrdersPage';
import { OverviewPage } from '../pages/AdminPanel/OverviewPage';
import { UsersPage } from '../pages/AdminPanel/UsersPage';
import { BookingPage } from '../pages/Booking/BookingPage';
import { CartPage } from '../pages/Cart/CartPage';
import { CheckoutPage } from '../pages/Checkout/CheckoutPage';
import { OrderSuccessPage } from '../pages/Checkout/OrderSuccessPage';
import { HomePage } from '../pages/Home/HomePage';
import { LoginPage } from '../pages/Login/LoginPage';
import { MenuPage } from '../pages/Menu/MenuPage';
import { NotFoundPage } from '../pages/NotFound/NotFoundPage';
import { ReportsPage } from '../pages/Reports/ReportsPage';
import { StaffPage } from '../pages/Staff/StaffPage';

const ADMIN: Role[] = ['ADMIN'];

function section(roles: Role[], page: ReactNode) {
  return <PanelSectionGuard roles={roles}>{page}</PanelSectionGuard>;
}

// Role rules mirror the server (API contract §8): the server checks every request again.
export const router = createBrowserRouter([
  {
    element: <MainLayout />,
    children: [
      { path: '/', element: <HomePage /> },
      { path: '/menu', element: <MenuPage /> },
      { path: '/cart', element: <CartPage /> },
      { path: '/checkout', element: <CheckoutPage /> },
      { path: '/checkout/success', element: <OrderSuccessPage /> },
      { path: '/booking', element: <BookingPage /> },
      { path: '/login', element: <LoginPage /> },
      {
        path: '/account',
        element: (
          <RequireRole>
            <AccountPage />
          </RequireRole>
        ),
      },
      {
        path: '/account/orders/:orderNumber',
        element: (
          <RequireRole>
            <OrderDetailsPage />
          </RequireRole>
        ),
      },
      // Reports and the café work list moved into the admin panel (v1.11); old links still work.
      { path: '/reports', element: <Navigate to="/admin/reports" replace /> },
      { path: '/staff', element: <Navigate to="/admin/cafe-orders" replace /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
  // The admin panel: its own login and its own screen, without the shop's header and footer.
  { path: '/admin/login', element: <AdminLoginPage /> },
  {
    path: '/admin',
    element: <AdminLayout />,
    children: [
      { index: true, element: section(ADMIN, <OverviewPage />) },
      { path: 'orders', element: section(ADMIN, <OrdersPage />) },
      { path: 'orders/:orderNumber', element: section(ADMIN, <OrderPage />) },
      { path: 'bookings', element: section(ADMIN, <BookingsPage />) },
      { path: 'users', element: section(ADMIN, <UsersPage />) },
      { path: 'cafes', element: section(ADMIN, <CafesPage />) },
      { path: 'menu', element: section(ADMIN, <AdminCatalogPage />) },
      { path: 'cafe-orders', element: section(['ADMIN', 'STAFF'], <StaffPage />) },
      { path: 'reports', element: section(['ADMIN', 'VIEWER'], <ReportsPage />) },
      { path: '*', element: <Navigate to="/admin" replace /> },
    ],
  },
]);
