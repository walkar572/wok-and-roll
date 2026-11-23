import { createBrowserRouter } from 'react-router-dom';
import { MainLayout } from '../components/layout/MainLayout';
import { RequireRole } from '../components/layout/RequireRole';
import { AccountPage } from '../pages/Account/AccountPage';
import { OrderDetailsPage } from '../pages/Account/OrderDetailsPage';
import { AdminCatalogPage } from '../pages/Admin/AdminCatalogPage';
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
      {
        path: '/reports',
        element: (
          <RequireRole roles={['ADMIN', 'VIEWER']}>
            <ReportsPage />
          </RequireRole>
        ),
      },
      {
        path: '/admin',
        element: (
          <RequireRole roles={['ADMIN']}>
            <AdminCatalogPage />
          </RequireRole>
        ),
      },
      {
        path: '/staff',
        element: (
          <RequireRole roles={['STAFF', 'ADMIN']}>
            <StaffPage />
          </RequireRole>
        ),
      },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]);
