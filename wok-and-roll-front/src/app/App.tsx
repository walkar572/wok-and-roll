import { RouterProvider } from 'react-router-dom';
import { router } from './router';
import { CountryProvider } from '../context/CountryContext';
import { CartProvider } from '../context/CartContext';
import { AuthProvider } from '../context/AuthContext';

function App() {
  return (
    <AuthProvider>
      <CountryProvider>
        <CartProvider>
          <RouterProvider router={router} future={{ v7_startTransition: true }} />
        </CartProvider>
      </CountryProvider>
    </AuthProvider>
  );
}

export default App;
