import React from 'react';
import { createRoot } from 'react-dom/client';
import { Provider } from 'react-redux';
import { BrowserRouter, HashRouter, useNavigate } from 'react-router-dom';
import App from './App';
import store from './redux/store';
import { initAuthGuards } from './utils/authGuards';

// Suppress AbortError warnings from audio/video elements
const originalError = console.error;
console.error = (...args) => {
  if (args[0]?.includes?.('AbortError') || args[0]?.includes?.('play() request was interrupted')) {
    return;
  }
  originalError.apply(console, args);
};

// Install auth guards before rendering
try { initAuthGuards(store); } catch {}

const container = document.getElementById('root');
const root = createRoot(container);

// Optional: wrapper to handle an initial route restore very early
function Boot() {
  return <App />;
}

const useHash = import.meta.env.VITE_USE_HASH_ROUTER === 'true';
const Router = useHash ? HashRouter : BrowserRouter;

const app = (
  <Provider store={store}>
  <Router>
      <Boot />
  </Router>
  </Provider>
);

root.render(app);
