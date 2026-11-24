import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './app/App';
import './App.css';
import './design-tokens.css';
import './figma-layout.css';
import './features.css';
import './home-decor.css';
import './admin-panel.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
