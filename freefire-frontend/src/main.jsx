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

// Auto-fallback to HashRouter if the user opens a deep link (non-root path)
// This prevents 404s on static hosts/CDNs that don't rewrite to index.html.
const deepLinked = typeof window !== 'undefined' && window.location.pathname && window.location.pathname !== '/';
const useHash = (import.meta.env.VITE_USE_HASH_ROUTER === 'true') || deepLinked;

// If using HashRouter due to deep link, normalize URL to /#/path so routes match
try {
  if (useHash && typeof window !== 'undefined') {
    const { pathname, search, hash } = window.location;
    if (pathname !== '/' && !hash) {
      const newUrl = '/#' + pathname + (search || '');
      window.history.replaceState(null, '', newUrl);
    }
  }
} catch {}
const Router = useHash ? HashRouter : BrowserRouter;

const app = (
  <Provider store={store}>
  <Router>
      <Boot />
  </Router>
  </Provider>
);

root.render(app);
