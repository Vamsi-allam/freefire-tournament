function handleUnauthorized(res) {
	if (res.status === 401 || res.status === 403) {
		// Let global interceptor handle Redux; also navigate to home softly
		try {
			if (location.pathname !== '/') {
				window.location.replace('/');
			}
		} catch {}
	}
}
// Basic API helpers for matches (relative paths use Vite proxy in dev)
// In production (Vercel), set VITE_API_BASE_URL to your Render backend URL
const API_BASE = import.meta.env.VITE_API_BASE_URL || ""; // keep empty for same-origin/proxy

function authHeaders() {
	// Check both localStorage and see if token exists
	const token = localStorage.getItem('token');
	console.log('Token from localStorage:', token ? 'Token exists' : 'No token found');
	return token ? { 'Authorization': `Bearer ${token}` } : {};
}

export async function createMatch(payload) {
	const res = await fetch(`${API_BASE}/api/matches`, {
		method: 'POST',
		headers: { 'Content-Type': 'application/json', ...authHeaders() },
		body: JSON.stringify(payload)
	});
		if (!res.ok) { handleUnauthorized(res); throw new Error('Failed to create match'); }
	return res.json();
}

export async function listMatches() {
	const res = await fetch(`${API_BASE}/api/matches`, { headers: authHeaders() });
		if (!res.ok) { handleUnauthorized(res); throw new Error('Failed to list matches'); }
	return res.json();
}

export async function listUpcomingMatches() {
	const res = await fetch(`${API_BASE}/api/matches/upcoming`, { headers: authHeaders() });
		if (!res.ok) { handleUnauthorized(res); throw new Error('Failed to list upcoming matches'); }
	return res.json();
}

export async function updateMatch(id, matchData) {
	const res = await fetch(`${API_BASE}/api/matches/${id}`, {
		method: 'PUT',
		headers: { 'Content-Type': 'application/json', ...authHeaders() },
		body: JSON.stringify(matchData)
	});
		if (!res.ok) { handleUnauthorized(res); throw new Error('Failed to update match'); }
	return res.json();
}

export async function deleteMatch(id) {
	const res = await fetch(`${API_BASE}/api/matches/${id}`, {
		method: 'DELETE',
		headers: { ...authHeaders() }
	});
	if (!res.ok) { handleUnauthorized(res); throw new Error('Failed to delete match'); }
	// Backend may return empty body for DELETE; try to parse JSON, else return text
	try { return await res.json(); } catch { return await res.text().catch(() => ''); }
}

export async function saveCredentials(id, roomId, roomPassword) {
	const res = await fetch(`${API_BASE}/api/matches/${id}/credentials`, {
		method: 'POST',
		headers: { 'Content-Type': 'application/json', ...authHeaders() },
		body: JSON.stringify({ roomId, roomPassword })
	});
		if (!res.ok) { 
		const txt = await res.text().catch(() => '');
		if (res.status === 401 || res.status === 403) {
			throw new Error('Not authorized. Admin access required or session expired. Please sign in again.');
		}
		throw new Error(txt || 'Failed to save credentials');
	}
	return res.json();
}

export async function sendCredentialsToPlayers(id) {
	const res = await fetch(`${API_BASE}/api/matches/${id}/send-credentials`, {
		method: 'POST',
		headers: { 'Content-Type': 'application/json', ...authHeaders() }
	});
		if (!res.ok) { 
		const txt = await res.text().catch(() => '');
		if (res.status === 401 || res.status === 403) {
			throw new Error('Not authorized. Admin access required or session expired. Please sign in again.');
		}
		throw new Error(txt || 'Failed to send credentials');
	}
	return res.text();
}

// Wallet API functions
export async function getWalletBalance() {
	const res = await fetch(`${API_BASE}/api/wallet/balance`, { 
		headers: authHeaders() 
	});
		if (!res.ok) { handleUnauthorized(res); throw new Error('Failed to get wallet balance'); }
	return res.json();
}

export async function getTransactionHistory() {
	const res = await fetch(`${API_BASE}/api/wallet/transactions`, { 
		headers: authHeaders() 
	});
		if (!res.ok) { handleUnauthorized(res); throw new Error('Failed to get transaction history'); }
	return res.json();
}

export async function addMoney(amount) {
	const res = await fetch(`${API_BASE}/api/wallet/add`, {
		method: 'POST',
		headers: { 'Content-Type': 'application/json', ...authHeaders() },
		body: JSON.stringify({ amount })
	});
		if (!res.ok) { handleUnauthorized(res); throw new Error('Failed to add money'); }
	return res.json();
}

export async function withdrawMoney(amount) {
	const res = await fetch(`${API_BASE}/api/wallet/withdraw`, {
		method: 'POST',
		headers: { 'Content-Type': 'application/json', ...authHeaders() },
		body: JSON.stringify({ amount })
	});
		if (!res.ok) { handleUnauthorized(res); throw new Error('Failed to withdraw money'); }
	return res.json();
}

// New OTP-based withdrawal functions
export async function initiateWithdrawal(amount, withdrawalMethod, details) {
	const payload = {
		amount: parseFloat(amount),
		withdrawalMethod: withdrawalMethod.toUpperCase()
	};

	// Add method-specific details
	if (withdrawalMethod.toLowerCase() === 'bank') {
		payload.accountNumber = details.accountNumber;
		payload.ifscCode = details.ifscCode;
		payload.accountHolderName = details.accountHolderName;
	} else if (withdrawalMethod.toLowerCase() === 'upi') {
		payload.upiId = details.upiId;
	}

	const res = await fetch(`${API_BASE}/api/wallet/withdraw/initiate`, {
		method: 'POST',
		headers: { 'Content-Type': 'application/json', ...authHeaders() },
		body: JSON.stringify(payload)
	});
	
		if (!res.ok) { 
		const errorData = await res.json();
		throw new Error(errorData.error || 'Failed to initiate withdrawal');
	}
	
	return res.json();
}

export async function verifyWithdrawalOtp(otpCode) {
	const res = await fetch(`${API_BASE}/api/wallet/withdraw/verify`, {
		method: 'POST',
		headers: { 'Content-Type': 'application/json', ...authHeaders() },
		body: JSON.stringify({ otpCode })
	});
	
		if (!res.ok) { 
		const errorData = await res.json();
		throw new Error(errorData.error || 'Failed to verify OTP');
	}
	
	return res.json();
}

// Match Results API functions
export async function getMatchParticipants(matchId) {
	const res = await fetch(`${API_BASE}/api/match-results/${matchId}/participants`, { 
		headers: authHeaders() 
	});
		if (!res.ok) { handleUnauthorized(res); throw new Error('Failed to get match participants'); }
	return res.json();
}

export async function getMatchResults(matchId) {
	const res = await fetch(`${API_BASE}/api/match-results/${matchId}/results`, { 
		headers: authHeaders() 
	});
		if (!res.ok) { handleUnauthorized(res); throw new Error('Failed to get match results'); }
	return res.json();
}

export async function updateMatchResult(matchId, resultData) {
	const res = await fetch(`${API_BASE}/api/match-results/${matchId}/update-result`, {
		method: 'PUT',
		headers: { 'Content-Type': 'application/json', ...authHeaders() },
		body: JSON.stringify(resultData)
	});
		if (!res.ok) { handleUnauthorized(res); throw new Error('Failed to update match result'); }
	return res.json();
}

export async function getPrizeDistribution(matchId) {
	const res = await fetch(`${API_BASE}/api/match-results/${matchId}/prize-distribution`, { 
		headers: authHeaders() 
	});
		if (!res.ok) { handleUnauthorized(res); throw new Error('Failed to get prize distribution'); }
	return res.json();
}

export async function creditAllPrizes(matchId) {
	const res = await fetch(`${API_BASE}/api/match-results/${matchId}/credit-all-prizes`, {
		method: 'POST',
		headers: { 'Content-Type': 'application/json', ...authHeaders() }
	});
		if (!res.ok) { handleUnauthorized(res); throw new Error('Failed to credit prizes'); }
	return res.text();
}

// Registrations (for viewing participants / slots)
export async function getMatchRegistrations(matchId) {
	const res = await fetch(`${API_BASE}/api/registrations/match/${matchId}`, { headers: authHeaders() });
		if (!res.ok) { handleUnauthorized(res); throw new Error('Failed to load registrations'); }
	return res.json();
}

// Get user's own registrations
export async function getUserRegistrations() {
	console.log('getUserRegistrations: Headers being sent:', authHeaders());

	const res = await fetch(`${API_BASE}/api/registrations/my-registrations`, { headers: authHeaders() });
		if (!res.ok) { 
		console.log('getUserRegistrations: Response status:', res.status);
		throw new Error('Failed to load your registrations');
	}
	return res.json();
}

// (Optional) Supabase direct table interactions would live elsewhere; placeholder for future
export async function saveProfileToSupabase(supabase, { id, name, phone }) {
	const { error } = await supabase.from('profiles').upsert({ id, name, phone });
	if (error) throw error;
	return true;
}

// UPI Add Money (no gateway) helpers
export async function initiateUpi(amount, payerUpiId, paymentApp) {
	const res = await fetch(`${API_BASE}/api/upi/initiate`, {
		method: 'POST',
		headers: { 'Content-Type': 'application/json', ...authHeaders() },
		body: JSON.stringify({ amount: Number(amount), payerUpiId, paymentApp })
	});
	const data = await res.json();
		if (!res.ok) { handleUnauthorized(res); throw new Error(data.error || 'Failed to initiate UPI'); }
	return data; // {id, amount, deeplink, payeeVpa, payeeName, note, referenceId, paymentApp}
}

export async function submitUpiUtr(paymentOrId, utr) {
	let payload;
	if (typeof paymentOrId === 'number') {
		payload = { paymentId: paymentOrId, utr };
	} else if (paymentOrId && typeof paymentOrId === 'object') {
		const p = paymentOrId;
		payload = {
			paymentId: p.id ?? null,
			utr,
			amount: p.amount != null ? Number(p.amount) : undefined,
			paymentApp: p.paymentApp,
			referenceId: p.referenceId,
			payerUpiId: p.upiId || p.payerUpiId
		};
	} else {
		payload = { paymentId: null, utr };
	}
	const res = await fetch(`${API_BASE}/api/upi/submit-utr`, {
		method: 'POST',
		headers: { 'Content-Type': 'application/json', Accept: 'application/json', ...authHeaders() },
		body: JSON.stringify(payload)
	});

	// Try to parse JSON safely; fall back to text
	const contentType = res.headers.get('content-type') || '';
	let data;
	try {
		data = contentType.includes('application/json') ? await res.json() : { message: await res.text() };
	} catch {
		data = { message: 'No response body' };
	}

	if (!res.ok) {
		if (res.status === 401 || res.status === 403) {
			throw new Error('Please sign in to submit UTR.');
		}
		throw new Error(data.error || data.message || 'Failed to submit UTR');
	}
	return data;
}

// UPI: list my UPI payments (excluding INITIATED)
export async function listMyUpiPayments() {
	const res = await fetch(`${API_BASE}/api/upi/my`, { headers: authHeaders() });
	if (!res.ok) throw new Error('Failed to load UPI payments');
	return res.json();
}

// Admin Withdrawal Management
export async function listPendingWithdrawals() {
	const res = await fetch(`${API_BASE}/api/withdrawals/admin/pending`, { headers: authHeaders() });
	if (!res.ok) throw new Error('Failed to load pending withdrawals');
	return res.json();
}

export async function actOnWithdrawal(requestId, action, notes = '') {
	const res = await fetch(`${API_BASE}/api/withdrawals/admin/action`, {
		method: 'POST',
		headers: { 'Content-Type': 'application/json', ...authHeaders() },
		body: JSON.stringify({ requestId, action, notes })
	});
	const data = await res.json().catch(() => ({}));
	if (!res.ok) throw new Error(data.error || 'Failed to process withdrawal');
	return data;
}

// User: list my withdrawals
export async function listMyWithdrawals() {
	const res = await fetch(`${API_BASE}/api/wallet/withdrawals`, { headers: authHeaders() });
	if (!res.ok) throw new Error('Failed to load your withdrawals');
	return res.json();
}

// Support: submit a support ticket (email, phone, message)
export async function submitSupportRequest({ email, phone, message }) {
	const res = await fetch(`${API_BASE}/api/support`, {
		method: 'POST',
		headers: { 'Content-Type': 'application/json', ...authHeaders() },
		body: JSON.stringify({ email, phone, message })
	});
		// If endpoint doesn't exist yet, treat as soft-success for now
		if (res.status === 404) {
			console.warn('submitSupportRequest: /api/support not found. Falling back to local success.');
			return { message: 'received (local fallback)' };
		}
		const contentType = res.headers.get('content-type') || '';
		const data = contentType.includes('application/json') ? await res.json().catch(() => ({})) : { message: await res.text().catch(() => '') };
		if (!res.ok) throw new Error(data.error || data.message || 'Failed to submit support request');
		return data;
}

