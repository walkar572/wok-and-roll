import { createBrowserRouter } from 'react-router-dom';
import { MainLayout } from '../components/layout/MainLayout';
import { CartPage } from '../pages/Cart/CartPage';
import { CheckoutPage } from '../pages/Checkout/CheckoutPage';
import { OrderSuccessPage } from '../pages/Checkout/OrderSuccessPage';
import { HomePage } from '../pages/Home/HomePage';
import { MenuPage } from '../pages/Menu/MenuPage';
import { NotFoundPage } from '../pages/NotFound/NotFoundPage';
import { ReportsPage } from '../pages/Reports/ReportsPage';

export const router = createBrowserRouter([
  {
    element: <MainLayout />,
    children: [
      { path: '/', element: <HomePage /> },
      { path: '/menu', element: <MenuPage /> },
      { path: '/cart', element: <CartPage /> },
      { path: '/checkout', element: <CheckoutPage /> },
      { path: '/checkout/success', element: <OrderSuccessPage /> },
      { path: '/reports', element: <ReportsPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]);
