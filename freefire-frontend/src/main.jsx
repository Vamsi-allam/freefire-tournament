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

// One-time migration: move any auth/UI state from localStorage -> sessionStorage
try {
  if (typeof window !== 'undefined') {
    const ls = window.localStorage;
    const ss = window.sessionStorage;
    const migrateKey = (key) => {
      try {
        const val = ls.getItem(key);
        if (val !== null && ss.getItem(key) === null) {
          ss.setItem(key, val);
          ls.removeItem(key);
        }
      } catch {}
    };

    // Known keys our app sets
    [
      'token', 'userRole', 'userName', 'userEmail', 'userPhone', 'userGameId', 'userAvatar',
      'supabaseSession', 'supabaseAccessToken', 'needsProfileCompletion',
      'ui.lastRoute',
      // Wallet/UI flows
      'ui.wallet.activeSection','ui.wallet.transactionFilter','ui.wallet.showAddMoneyModal','ui.wallet.showWithdrawModal',
      'ui.addMoney.amount','ui.addMoney.method','ui.addMoney.showUpiForm','ui.addMoney.upiPaymentData','ui.addMoney.utr',
      'ui.user.activeTab','ui.user.showWalletModal'
    ].forEach(migrateKey);

    // Also migrate any Supabase sb-* keys (gotrue) if present
    try {
      for (let i = 0; i < ls.length; i++) {
        const k = ls.key(i);
        if (k && (k.startsWith('sb-') || k.includes('supabase'))) {
          migrateKey(k);
        }
      }
    } catch {}
  }
} catch {}

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
