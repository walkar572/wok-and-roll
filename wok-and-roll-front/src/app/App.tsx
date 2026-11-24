import { RouterProvider } from 'react-router-dom';
import { router } from './router';
import { CountryProvider } from '../context/CountryContext';
import { CartProvider } from '../context/CartContext';
import { AuthProvider } from '../context/AuthContext';
import { I18nProvider } from '../i18n';

function App() {
  return (
    <AuthProvider>
      <CountryProvider>
        <I18nProvider>
          <CartProvider>
            <RouterProvider router={router} future={{ v7_startTransition: true }} />
          </CartProvider>
        </I18nProvider>
      </CountryProvider>
    </AuthProvider>
  );
}

export default App;
