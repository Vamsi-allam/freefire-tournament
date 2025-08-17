import React, { useEffect } from 'react';
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

// Ensure favicon works in production by using a Vite-resolved asset URL
try {
  const logoUrl = new URL('./assets/logo.jpg', import.meta.url).href;
  const ensureLink = (rel) => {
    let link = document.querySelector(`link[rel="${rel}"]`);
    if (!link) {
      link = document.createElement('link');
      link.rel = rel;
      document.head.appendChild(link);
    }
    link.href = logoUrl;
    if (rel === 'icon' || rel === 'shortcut icon') link.type = 'image/jpeg';
  };
  ['icon', 'shortcut icon', 'apple-touch-icon'].forEach(ensureLink);
} catch {}

// Ensure correct page title in all environments
try {
  if (document && document.title !== 'Prime Arena') {
    document.title = 'Prime Arena';
  }
} catch {}

// One-time migration: move any auth/UI state from localStorage -> sessionStorage
// and purge lingering localStorage entries so DevTools no longer shows them.
try {
  if (typeof window !== 'undefined') {
    const ls = window.localStorage;
    const ss = window.sessionStorage;

    const knownKeys = [
      'token', 'userRole', 'userName', 'userEmail', 'userPhone', 'userGameId', 'userAvatar',
      'supabaseSession', 'supabaseAccessToken', 'needsProfileCompletion',
      'ui.lastRoute',
      // Wallet/UI flows
      'ui.wallet.activeSection','ui.wallet.transactionFilter','ui.wallet.showAddMoneyModal','ui.wallet.showWithdrawModal',
      'ui.addMoney.amount','ui.addMoney.method','ui.addMoney.showUpiForm','ui.addMoney.upiPaymentData','ui.addMoney.utr',
      'ui.user.activeTab','ui.user.showWalletModal'
    ];

    const safeSet = (k, v) => { try { ss.setItem(k, v); } catch {} };
    const safeRemoveLS = (k) => { try { ls.removeItem(k); } catch {} };

    // Migrate known app keys first (overwrite in sessionStorage, then remove from localStorage)
    knownKeys.forEach((key) => {
      try {
        const val = ls.getItem(key);
        if (val !== null) {
          safeSet(key, val);
          safeRemoveLS(key);
        }
      } catch {}
    });

    // Collect all localStorage keys first to avoid index shifting while removing
    let allKeys = [];
    try {
      for (let i = 0; i < ls.length; i++) {
        const k = ls.key(i);
        if (k) allKeys.push(k);
      }
    } catch {}

    // Migrate and purge any Supabase Gotrue keys (sb-*) or anything with 'supabase'
    allKeys
      .filter((k) => k.startsWith('sb-') || k.includes('supabase'))
      .forEach((k) => {
        try {
          const val = ls.getItem(k);
          if (val !== null) safeSet(k, val);
          safeRemoveLS(k);
        } catch {}
      });

    // Final cleanup: ensure known keys are not left behind in localStorage
    knownKeys.forEach(safeRemoveLS);
  }
} catch {}

// Install auth guards before rendering
try { initAuthGuards(store); } catch {}

const container = document.getElementById('root');
const root = createRoot(container);

// Optional: wrapper to handle an initial route restore very early
function Boot() {
  // Install a global listener to react to 401/expired tokens
  useEffect(() => {
    const onUnauthorized = (e) => {
      const detail = e?.detail || {};
      const msg = detail.message || (detail.isExpired ? 'Session expired. Please sign in again.' : 'Please sign in to continue.');
      try {
        // Broadcast a UI notification event; pages with snackbars can show it
        window.dispatchEvent(new CustomEvent('ui:snackbar', { detail: { message: msg, severity: 'warning' } }));
      } catch {}
      // Open sign-in modal if on homepage; else router guard will redirect
      try { window.dispatchEvent(new CustomEvent('open-signin-modal')); } catch {}
    };
    window.addEventListener('app:unauthorized', onUnauthorized);
    return () => window.removeEventListener('app:unauthorized', onUnauthorized);
  }, []);

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
