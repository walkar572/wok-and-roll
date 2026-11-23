import { RouterProvider } from 'react-router-dom';
import { router } from './router';
import { CountryProvider } from '../context/CountryContext';
import { CartProvider } from '../context/CartContext';

function App() {
  return (
    <CountryProvider>
      <CartProvider>
        <RouterProvider router={router} future={{ v7_startTransition: true }} />
      </CartProvider>
    </CountryProvider>
  );
}

export default App;
