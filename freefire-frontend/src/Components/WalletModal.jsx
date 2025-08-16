import React, { useState, useEffect } from 'react';
import { Snackbar, Alert } from '@mui/material';
import { useSelector, useDispatch } from 'react-redux';
import { updateProfile } from '../redux/userSlice';
import { getWalletBalance, getTransactionHistory, addMoney, withdrawMoney, listMyUpiPayments, listMyWithdrawals, getUserRegistrations } from '../utils/api';
import AddMoneyModal from './AddMoneyModal';
import WithdrawModal from './WithdrawModal';
import ProfileCompletionModal from './ProfileCompletionModal';
import './WalletModal.css';

const WalletModal = ({ isOpen, onClose }) => {
  const dispatch = useDispatch();
  const { userData } = useSelector((state) => state.user);
  const [walletData, setWalletData] = useState({
    balance: 0,
    totalAdded: 0,
    totalSpent: 0,
    moneyAdded: 0,
  transactions: 0
  });
  
  const [activeSection, setActiveSection] = useState(() => {
    try { return localStorage.getItem('ui.wallet.activeSection') || 'history'; } catch { return 'history'; }
  }); // 'history' or 'analytics'
  const [transactionFilter, setTransactionFilter] = useState(() => {
    try { return localStorage.getItem('ui.wallet.transactionFilter') || 'all'; } catch { return 'all'; }
  }); // 'all', 'added', 'spent'
  const [transactions, setTransactions] = useState([]);
  const [upiPayments, setUpiPayments] = useState([]); // pending/approved/rejected
  const [withdrawals, setWithdrawals] = useState([]); // my withdrawal requests
  const [registrations, setRegistrations] = useState([]); // my match registrations to determine completion
  const [isLoading, setIsLoading] = useState(false);
  const [showAddMoneyModal, setShowAddMoneyModal] = useState(() => {
    try { return localStorage.getItem('ui.wallet.showAddMoneyModal') === 'true'; } catch { return false; }
  });
  const [showWithdrawModal, setShowWithdrawModal] = useState(() => {
    try { return localStorage.getItem('ui.wallet.showWithdrawModal') === 'true'; } catch { return false; }
  });
  const [showProfileCompletion, setShowProfileCompletion] = useState(false);
  const [profileCompleted, setProfileCompleted] = useState(false);
  // Snackbar
  const [snack, setSnack] = useState({ open: false, message: '', severity: 'info' });
  const openSnack = (message, severity = 'info') => setSnack({ open: true, message, severity });
  const closeSnack = () => setSnack(s => ({ ...s, open: false }));

  // Check if user profile is complete
  const isProfileComplete = () => {
    return userData?.phone && userData?.gameId && userData?.name;
  };

  useEffect(() => {
    if (isOpen) {
      const currentProfileStatus = isProfileComplete();
      setProfileCompleted(currentProfileStatus);
      fetchWalletData();
  fetchTransactions();
  fetchUpiPayments();
  fetchWithdrawals();
  fetchRegistrations();
    }
  }, [isOpen, userData]); // Added userData dependency to re-check when user data changes

  // Reset modal states when wallet modal closes
  useEffect(() => {
    if (!isOpen) {
      setShowAddMoneyModal(false);
      setShowWithdrawModal(false);
      setShowProfileCompletion(false);
    }
  }, [isOpen]);

  // Persist UI state for refresh continuity
  useEffect(() => {
    try { localStorage.setItem('ui.wallet.activeSection', activeSection); } catch {}
  }, [activeSection]);
  useEffect(() => {
    try { localStorage.setItem('ui.wallet.transactionFilter', transactionFilter); } catch {}
  }, [transactionFilter]);
  useEffect(() => {
    try { localStorage.setItem('ui.wallet.showAddMoneyModal', String(showAddMoneyModal)); } catch {}
  }, [showAddMoneyModal]);
  useEffect(() => {
    try { localStorage.setItem('ui.wallet.showWithdrawModal', String(showWithdrawModal)); } catch {}
  }, [showWithdrawModal]);

  const fetchWalletData = async () => {
    try {
      const token = localStorage.getItem("token");
      console.log("Token found:", !!token);
      
      if (!token) {
        console.error("No token found");
        return;
      }
      
      const data = await getWalletBalance();
      setWalletData(prev => ({
        ...prev,
        balance: data.balance || 0
      }));
    } catch (error) {
      console.error("Failed to fetch wallet data:", error);
    }
  };

  const fetchTransactions = async () => {
    try {
      setIsLoading(true);
      const token = localStorage.getItem("token");
      
      if (!token) {
        console.error("No token found for transactions");
        setTransactions([]);
        return;
      }
      
      const data = await getTransactionHistory();
      setTransactions(data || []);
      
      // Keep only transactions count here; detailed stats computed in a separate effect
      setWalletData(prev => ({
        ...prev,
        transactions: data.length
      }));
    } catch (error) {
      console.error("Failed to fetch transactions:", error);
      // Set empty transactions as fallback
      setTransactions([]);
    } finally {
      setIsLoading(false);
    }
  };

  // Recompute dashboard stats based on refined rules whenever data changes
  useEffect(() => {
    const txns = Array.isArray(transactions) ? transactions : [];

    // Helper: determine if a CREDIT is true add-money (exclude refunds/prizes)
    const isAddMoneyCredit = (t) => {
      if (!t || t.type !== 'CREDIT') return false;
      const ref = (t.referenceId || '').toString();
      const desc = (t.description || '').toString();
      // Exclude refunds and prizes
      if (ref.startsWith('WRF_') || ref.startsWith('REF_') || ref.startsWith('PRIZE_')) return false;
      // Include UPI add money or explicit add via gateway
      if (ref.startsWith('UPI_')) return true;
      if (/UPI Add Money|Money added via|Add Money/i.test(desc)) return true;
      return false;
    };

    // Helper: extract match title from transaction description
    const extractTitle = (desc) => {
      if (!desc) return '';
      const s = desc.toString();
      const prefix = 'Tournament Registration - ';
      if (s.startsWith(prefix)) return s.slice(prefix.length).trim();
      return s.trim();
    };

    // Helper: normalize titles for robust matching
    const normalizeTitle = (title) => {
      return (title || '')
        .toString()
        .toLowerCase()
        .trim()
        .replace(/\s+/g, ' ')        // collapse whitespace
        .replace(/[^a-z0-9 ]/g, '')   // drop punctuation/specials
        .replace(/\s/g, '');          // remove spaces for compact key
    };

    // Completed matches set from registrations
    const completedRegs = (registrations || [])
      .filter(r => ((r?.match?.status || r?.status || '') + '').toUpperCase() === 'COMPLETED')
      .map(r => {
        const title = (r?.match?.title || r?.matchTitle || '').toString().trim();
        return {
          title,
          normTitle: normalizeTitle(title),
          amountPaid: Number(r?.amountPaid ?? 0),
          entryFee: Number(r?.match?.entryFee ?? 0)
        };
      })
      .filter(r => r.title);

    // Build set of refunded match titles from refund credits
    const refundedTitleKeys = new Set(
      txns
        .filter(t => t?.type === 'CREDIT' && typeof t?.referenceId === 'string' && t.referenceId.startsWith('REF_'))
        .map(t => (t.description || '').toString())
        .map(desc => {
          // Expect formats like:
          //  - "Refund: Match cancelled due to low registrations - <title>"
          //  - "Refund: Match cancelled by admin - <title>"
          //  - "Refund for failed registration - <title>"
          const idx = desc.lastIndexOf(' - ');
          const title = idx >= 0 ? desc.slice(idx + 3).trim() : '';
          return normalizeTitle(title);
        })
        .filter(Boolean)
    );

    // Build debit buckets by normalized title from transaction history
    const debitBuckets = new Map(); // normTitle => number[] amounts
    txns
      .filter(t => t?.type === 'DEBIT' && typeof t?.referenceId === 'string' && t.referenceId.startsWith('TRN_'))
      .forEach(t => {
        const title = extractTitle(t.description);
        const key = normalizeTitle(title);
        if (!key) return;
        const list = debitBuckets.get(key) || [];
        list.push(Number(t.amount || 0));
        debitBuckets.set(key, list);
      });

    // Totals
    const trueTotalAdded = txns
      .filter(isAddMoneyCredit)
      .reduce((sum, t) => sum + Number(t.amount || 0), 0);

    // Compute spent by iterating completed registrations and matching debits (exclude refunded)
    const filteredTotalSpent = completedRegs.reduce((sum, reg) => {
      if (!reg.normTitle || refundedTitleKeys.has(reg.normTitle)) return sum;
      const bucket = debitBuckets.get(reg.normTitle) || [];
      let amt = 0;
      if (bucket.length > 0) {
        amt = Number(bucket.shift() || 0); // consume one debit for this title
        debitBuckets.set(reg.normTitle, bucket);
      } else if (reg.amountPaid > 0) {
        amt = Number(reg.amountPaid);
      } else if (reg.entryFee > 0) {
        amt = Number(reg.entryFee);
      }
      return sum + amt;
    }, 0);

    // Profit: sum of admin-sent prize credits
    const profitTotal = txns
      .filter(t => t?.type === 'CREDIT' && typeof t?.referenceId === 'string' && t.referenceId.startsWith('PRIZE_'))
      .reduce((sum, t) => sum + Number(t.amount || 0), 0);

    const withdrawTotal = (withdrawals || [])
      .filter(w => ['PENDING', 'PAID'].includes(String(w.status || '').toUpperCase()))
      .reduce((sum, w) => sum + Number(w.amount || 0), 0);

    setWalletData(prev => ({
      ...prev,
      trueTotalAdded,
      filteredTotalSpent,
  withdrawTotal,
  profitTotal
    }));
  }, [transactions, registrations, withdrawals]);

  const fetchUpiPayments = async () => {
    try {
      const token = localStorage.getItem('token');
      if (!token) { setUpiPayments([]); return; }
      const data = await listMyUpiPayments();
      setUpiPayments(Array.isArray(data) ? data : []);
    } catch (e) {
      console.warn('Failed to load UPI payments:', e.message);
      setUpiPayments([]);
    }
  };

  const fetchWithdrawals = async () => {
    try {
      const token = localStorage.getItem('token');
      if (!token) { setWithdrawals([]); return; }
      const data = await listMyWithdrawals();
      setWithdrawals(Array.isArray(data) ? data : []);
    } catch (e) {
      console.warn('Failed to load withdrawals:', e.message);
      setWithdrawals([]);
    }
  };

  const fetchRegistrations = async () => {
    try {
      const token = localStorage.getItem('token');
      if (!token) { setRegistrations([]); return; }
      const data = await getUserRegistrations();
      setRegistrations(Array.isArray(data) ? data : []);
    } catch (e) {
      console.warn('Failed to load registrations:', e.message);
      setRegistrations([]);
    }
  };

  const handleAddMoney = async (amount, paymentMethod) => {
    try {
      await addMoney(amount);
      await fetchWalletData(); // Refresh wallet data
      await fetchTransactions(); // Refresh transactions
    } catch (error) {
      console.error('Error adding money:', error);
      throw error;
    }
  };

  const handleAddMoneyClick = () => {
    // Check if profile is complete before showing add money modal
    const currentProfileStatus = isProfileComplete();
    if (!currentProfileStatus) {
      setShowProfileCompletion(true);
      setProfileCompleted(false);
    } else {
      setShowAddMoneyModal(true);
      setProfileCompleted(true);
    }
  };

  const handleProfileCompletion = async (profileData) => {
    try {
      const token = localStorage.getItem("token");
      const response = await fetch('/api/profile/complete', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify(profileData)
      });

      if (response.ok) {
        const updatedUser = await response.json();
        
        // Update local storage with new user data
        localStorage.setItem('userName', profileData.displayName);
        localStorage.setItem('userPhone', profileData.phoneNumber);
        localStorage.setItem('userGameId', profileData.gameId);
        
        // Update Redux store with new profile data
        dispatch(updateProfile(profileData));
        
        setProfileCompleted(true);
        setShowProfileCompletion(false);
        
        // Now show add money modal
        setTimeout(() => {
          setShowAddMoneyModal(true);
        }, 100);
        
      } else {
        throw new Error('Failed to update profile');
      }
    } catch (error) {
      console.error('Error completing profile:', error);
      openSnack('Failed to update profile. Please try again.', 'error');
    }
  };

  const handleWithdraw = async (transactionResult) => {
    try {
      // The withdrawal is already completed at this point (after OTP verification)
      // We just need to refresh the data
      await fetchWalletData(); // Refresh wallet data
  await fetchTransactions(); // Refresh transactions
  await fetchWithdrawals(); // Ensure pending withdrawal shows up
      
      // Show success message
      console.log('Withdrawal completed successfully:', transactionResult);
      
    } catch (error) {
      console.error('Error updating wallet data after withdrawal:', error);
      // Even if refresh fails, the withdrawal was successful
      throw error;
    }
  };

  const getFilteredTransactions = () => {
    // Start with real wallet transactions
    let list = Array.isArray(transactions) ? [...transactions] : [];

    // Build a set/map of existing referenceIds to avoid duplicating shadow entries
    const existingRefs = new Set(
      list
        .map(t => t && t.referenceId)
        .filter(Boolean)
    );

    // Map UPI payments by referenceId for quick lookup (used to annotate credited items)
    const upiByRef = new Map(
      (upiPayments || [])
        .filter(p => p && p.referenceId)
        .map(p => [p.referenceId, p])
    );

    const wdByRef = new Map((withdrawals || []).map(w => {
      const ref = w.referenceId && String(w.referenceId).trim().length > 0 ? w.referenceId : `WREQ_${w.id}`;
      return [ref, w];
    }));

    // Add UPI Add Money shadow items (pending/rejected not in wallet)
    const upiShadow = (upiPayments || [])
      .filter(p => p.status === 'UTR_SUBMITTED' || p.status === 'REJECTED')
      .map(p => ({
        id: `upi-${p.id}`,
        type: 'CREDIT',
        amount: p.amount,
        description: `UPI Add Money ${p.status === 'UTR_SUBMITTED' ? '(Pending Verification)' : '(Rejected)'}${p.utr ? ` (UTR: ${p.utr})` : ''}`,
        referenceId: p.referenceId,
        createdAt: p.createdAt || p.updatedAt || new Date().toISOString(),
        __upiStatus: p.status
      }));

    // Add Withdrawal request shadow items
    const wdShadow = (withdrawals || []).map(w => ({
      id: `wd-${w.id}`,
      type: 'DEBIT',
      amount: Number(w.amount || 0),
      description: `Withdrawal ${w.status === 'PENDING' ? '(Pending)' : w.status === 'PAID' ? '(Paid)' : '(Rejected)'} via ${w.method}${w.method === 'UPI' && w.upiId ? ` (${w.upiId})` : ''}${w.referenceId ? ` (Ref: ${w.referenceId})` : ''}`,
      referenceId: (w.referenceId && String(w.referenceId).trim().length > 0) ? w.referenceId : `WREQ_${w.id}`,
      createdAt: w.createdAt || w.updatedAt || new Date().toISOString(),
      __withdrawalStatus: w.status
    }));

    // Exclude withdrawal shadows that already have a real transaction with same referenceId
  const wdShadowDeduped = wdShadow.filter(w => !existingRefs.has(w.referenceId));

    // Annotate real transactions with withdrawal/UPI status when applicable
    list = list.map(t => {
      // Annotate credited UPI Add Money transactions
      if (t && t.referenceId && upiByRef.has(t.referenceId)) {
        const p = upiByRef.get(t.referenceId);
        const st = String(p.status || '').toUpperCase();
        if (st === 'APPROVED' || st === 'CREDITED' || st === 'PAID') {
          t = {
            ...t,
            __upiStatus: 'CREDITED',
            description: t.description || `UPI Add Money (Credited)`
          };
        }
      }

      // Annotate refund credits for rejected withdrawals (referenceId starts with WRF_)
      if (t && t.type === 'CREDIT' && typeof t.referenceId === 'string' && t.referenceId.startsWith('WRF_')) {
        t = {
          ...t,
          __refundStatus: 'REFUNDED',
          description: t.description || 'Withdrawal Refund'
        };
      }

      if (t && t.referenceId && wdByRef.has(t.referenceId)) {
        const w = wdByRef.get(t.referenceId);
        const statusText = w.status === 'PENDING' ? '(Pending)' : w.status === 'PAID' ? '(Paid)' : '(Rejected)';
        const methodText = `via ${w.method}` + (w.method === 'UPI' && w.upiId ? ` (${w.upiId})` : '');
        return {
          ...t,
          __withdrawalStatus: w.status,
          description: t.description || `Withdrawal ${statusText} ${methodText}`
        };
      }
      return t;
    });

    list = [...upiShadow, ...wdShadowDeduped, ...list];

    // Apply filter by type
    if (transactionFilter === 'added') {
      // Show credits: money added (gateway/UPI), and refunds
      list = list.filter(t => t.type === 'CREDIT');
    } else if (transactionFilter === 'spent') {
      // Show only tournament registration debits AND only when the match is COMPLETED
      // Build a set of completed match titles from user's registrations
      const completedTitles = new Set(
        (registrations || [])
          .filter(r => {
            const st = (r?.match?.status || r?.status || '').toString().toUpperCase();
            return st === 'COMPLETED';
          })
          .map(r => (r?.match?.title || r?.matchTitle || '').toString().trim())
          .filter(Boolean)
      );

      const extractTitle = (desc) => {
        if (!desc) return '';
        const s = desc.toString();
        const prefix = 'Tournament Registration - ';
        if (s.startsWith(prefix)) return s.slice(prefix.length).trim();
        return s.trim();
      };

      list = list.filter(t => {
        if (!(t?.type === 'DEBIT' && typeof t?.referenceId === 'string' && t.referenceId.startsWith('TRN_'))) return false;
        const title = extractTitle(t.description);
        if (!title) return false;
        return completedTitles.has(title);
      });
    } else if (transactionFilter === 'withdrawals') {
      // Show only withdrawals (real or shadow) by referenceId pattern or presence of withdrawal status
      list = list.filter(t => t.type === 'DEBIT' && (
        (typeof t.referenceId === 'string' && t.referenceId.startsWith('WREQ_')) ||
        Boolean(t.__withdrawalStatus)
      ));
    }

    // Sort newest first by createdAt
    list.sort((a,b) => new Date(b.createdAt) - new Date(a.createdAt));
    return list;
  };

  const formatDate = (dateString) => {
    const date = new Date(dateString);
    return date.toLocaleDateString('en-IN', {
      day: 'numeric',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  };

  if (!isOpen) return null;

  return (
    <div className="wallet-modal-overlay" onClick={onClose}>
      <div className="wallet-modal" onClick={(e) => e.stopPropagation()}>
        <div className="wallet-modal-header">
          <h2>My Wallet</h2>
          <p>Manage your PrimeArena wallet and transactions</p>
          <button className="wallet-close-btn" onClick={onClose}>×</button>
        </div>

        <div className="wallet-content">
          {/* Wallet Balance Section */}
          <div className="wallet-balance-section">
            <div className="balance-card">
              <div className="balance-header">
                <div className="balance-icon">💳</div>
                <div className="balance-info">
                  <h3>Wallet Balance</h3>
                  <div className="balance-amount">₹{walletData.balance.toLocaleString('en-IN')}</div>
                  <p>Available for tournaments</p>
                </div>
                <div className="welcome-info">
                  <p>Welcome back,</p>
                  <span>{userData?.name || 'User'}</span>
                </div>
              </div>
            </div>

            {/* Action Buttons */}
            <div className="wallet-actions">
              <button className="add-money-btn" onClick={handleAddMoneyClick}>
                <span className="btn-icon">+</span>
                Add Money
              </button>
              <button className="withdraw-btn" onClick={() => setShowWithdrawModal(true)}>
                <span className="btn-icon">↓</span>
                Withdraw
              </button>
            </div>
          </div>

          {/* Statistics Cards */}
          <div className="wallet-stats">
            <div className="stat-card green">
              <div className="stat-value">₹{Number(walletData.trueTotalAdded || 0)}</div>
              <div className="stat-label">Total Added</div>
            </div>
            <div className="stat-card red">
              <div className="stat-value">₹{Number(walletData.profitTotal || 0)}</div>
              <div className="stat-label">Profit</div>
            </div>
            <div className="stat-card blue">
              <div className="stat-value">₹{Number(walletData.withdrawTotal || 0)}</div>
              <div className="stat-label">Withdraw</div>
            </div>
            <div className="stat-card purple">
              <div className="stat-value">{walletData.transactions}</div>
              <div className="stat-label">Transactions</div>
            </div>
          </div>

          {/* Section Navigation */}
          <div className="section-navigation">
            <button 
              className={`section-btn ${activeSection === 'history' ? 'active' : ''}`}
              onClick={() => setActiveSection('history')}
            >
              Transaction History
            </button>
            <button 
              className={`section-btn ${activeSection === 'analytics' ? 'active' : ''}`}
              onClick={() => setActiveSection('analytics')}
            >
              📊 Analytics
            </button>
          </div>

          {/* Transaction History Section */}
          {activeSection === 'history' && (
            <div className="transaction-section">
              <div className="transaction-header">
                <h3>Transaction History</h3>
                <p>View all your wallet transactions</p>
              </div>

              {/* Transaction Filters */}
              <div className="transaction-filters">
                <button 
                  className={`filter-btn ${transactionFilter === 'all' ? 'active' : ''}`}
                  onClick={() => setTransactionFilter('all')}
                >
                  All
                </button>
                <button 
                  className={`filter-btn ${transactionFilter === 'added' ? 'active' : ''}`}
                  onClick={() => setTransactionFilter('added')}
                >
                  Money Added
                </button>
                <button 
                  className={`filter-btn ${transactionFilter === 'spent' ? 'active' : ''}`}
                  onClick={() => setTransactionFilter('spent')}
                >
                  Spent
                </button>
                <button 
                  className={`filter-btn ${transactionFilter === 'withdrawals' ? 'active' : ''}`}
                  onClick={() => setTransactionFilter('withdrawals')}
                >
                  Withdrawals
                </button>
              </div>

              {/* Transaction List */}
              <div className="transaction-list">
                {isLoading ? (
                  <div className="loading-state">
                    <div className="spinner"></div>
                    <p>Loading transactions...</p>
                  </div>
                ) : getFilteredTransactions().length === 0 ? (
                  <div className="no-transactions">
                    <div className="no-transactions-icon">💳</div>
                    <h4>No Transactions Yet</h4>
                    <p>Start by adding money to your wallet</p>
                    <button className="add-money-cta" onClick={handleAddMoneyClick}>
                      Add Money
                    </button>
                  </div>
                ) : (
                  getFilteredTransactions().map((transaction) => {
                    // Try to extract tournament info if present
                    let tournamentTitle = '';
                    let tournamentDate = '';
                    // If transaction is a tournament entry or prize, try to find match info from registrations
                    if (transaction.referenceId && (transaction.referenceId.startsWith('TRN_') || transaction.referenceId.startsWith('PRIZE_'))) {
                      const reg = (registrations || []).find(r => {
                        // Match by registrationId or matchId in referenceId
                        const ref = String(transaction.referenceId);
                        return (
                          (r?.registrationId && ref.includes(String(r.registrationId))) ||
                          (r?.match?.id && ref.includes(String(r.match.id)))
                        );
                      });
                      if (reg && reg.match) {
                        tournamentTitle = reg.match.title || '';
                        tournamentDate = reg.match.scheduledAt ? formatDate(reg.match.scheduledAt) : '';
                      }
                    }
                    return (
                      <div key={transaction.id} className="transaction-item">
                        <div className="transaction-icon">
                          {transaction.type === 'CREDIT' ? '💰' : '🏆'}
                        </div>
                        <div className="transaction-details">
                          <div className="transaction-title">
                            {transaction.description || 
                              (transaction.type === 'CREDIT' ? 'Money Added' : 'Tournament Entry')}
                          </div>
                          <div className="transaction-date">
                            {formatDate(transaction.createdAt)}
                            {tournamentTitle && (
                              <>
                                {' \u2022 '}
                                {tournamentTitle}
                                {tournamentDate ? ` — ${tournamentDate}` : ''}
                              </>
                            )}
                          </div>
                          {transaction.__upiStatus && (
                            <div className={`txn-status-badge ${String(transaction.__upiStatus).toLowerCase()}`}>
                              {String(transaction.__upiStatus) === 'UTR_SUBMITTED' ? 'Pending' : (String(transaction.__upiStatus).toUpperCase() === 'APPROVED' ? 'CREDITED' : String(transaction.__upiStatus))}
                            </div>
                          )}
                          {transaction.__withdrawalStatus && (
                            <div className={`txn-status-badge ${String(transaction.__withdrawalStatus).toLowerCase()}`}>
                              {String(transaction.__withdrawalStatus) === 'PENDING' ? 'Pending' : String(transaction.__withdrawalStatus)}
                            </div>
                          )}
                          {transaction.__refundStatus && (
                            <div className={`txn-status-badge refunded`}>
                              REFUNDED
                            </div>
                          )}
                        </div>
                        <div className={`transaction-amount ${transaction.type.toLowerCase()}`}>
                          {transaction.type === 'CREDIT' ? '+' : '-'}₹{transaction.amount}
                        </div>
                      </div>
                    );
                  })
                )}
              </div>
            </div>
          )}

          {/* Analytics Section */}
          {activeSection === 'analytics' && (
            <div className="analytics-section">
              <div className="analytics-header">
                <h3>Wallet Analytics</h3>
                <p>Detailed insights into your spending patterns</p>
              </div>

              <div className="analytics-cards">
                <div className="analytics-card">
                  <h4>Earnings Breakdown</h4>
                  <div className="spending-item">
                    <span>Admin Prize Credits</span>
                    <span>₹{Number(walletData.profitTotal || 0)}</span>
                  </div>
                  <div className="spending-item">
                    <span>Withdrawals</span>
                    <span>₹{Number(walletData.withdrawTotal || 0)}</span>
                  </div>
                </div>

                <div className="analytics-card">
                  <h4>Monthly Summary</h4>
                  <div className="monthly-item">
                    <span>This Month Added</span>
                    <span className="positive">+₹{Number(walletData.trueTotalAdded || 0)}</span>
                  </div>
                  <div className="monthly-item">
                    <span>This Month Prize Earnings</span>
                    <span className="positive">+₹{Number(walletData.profitTotal || 0)}</span>
                  </div>
                </div>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Add Money Modal */}
      <AddMoneyModal
        isOpen={showAddMoneyModal}
        onClose={() => setShowAddMoneyModal(false)}
        onAddMoney={handleAddMoney}
      />

      {/* Withdraw Modal */}
  <WithdrawModal 
        isOpen={showWithdrawModal}
        onClose={() => setShowWithdrawModal(false)}
        onWithdraw={handleWithdraw}
        currentBalance={walletData.balance}
      />
      
      <ProfileCompletionModal
        isOpen={showProfileCompletion}
        onClose={() => {
          setShowProfileCompletion(false);
          // Don't set profileCompleted to true here - only when actually completed
        }}
        onSubmit={handleProfileCompletion}
        userData={userData}
      />
      <Snackbar
        open={snack.open}
        autoHideDuration={3500}
        onClose={closeSnack}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
      >
        <Alert onClose={closeSnack} severity={snack.severity} variant="filled" sx={{ width: '100%' }}>
          {snack.message}
        </Alert>
      </Snackbar>
    </div>
  );
};
export default WalletModal;
