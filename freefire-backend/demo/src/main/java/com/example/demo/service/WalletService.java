package com.example.demo.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.dto.AddMoneyRequest;
import com.example.demo.dto.WalletResponse;
import com.example.demo.dto.WalletTransactionResponse;
import com.example.demo.dto.WithdrawMoneyRequest;
import com.example.demo.dto.WithdrawalOtpResponse;
import com.example.demo.entity.TransactionType;
import com.example.demo.entity.User;
import com.example.demo.entity.Wallet;
import com.example.demo.entity.WalletTransaction;
import com.example.demo.entity.WithdrawalOtp;
import com.example.demo.entity.WithdrawalRequest;
import com.example.demo.entity.WithdrawalStatus;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.WalletRepository;
import com.example.demo.repository.WalletTransactionRepository;
import com.example.demo.repository.WithdrawalRequestRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final WithdrawalOtpService otpService;
    private final EmailService emailService;
    private final WithdrawalRequestRepository withdrawalRequestRepository;

    // app.admin.emails is consumed inside EmailService
    @Transactional
    public Wallet createWalletForUser(User user) {
        if (walletRepository.existsByUserId(user.getId())) {
            return walletRepository.findByUserId(user.getId()).orElseThrow();
        }

        Wallet wallet = Wallet.builder()
                .user(user)
                .balance(BigDecimal.ZERO)
                .build();

        return walletRepository.save(wallet);
    }

    public WalletResponse getWalletBalance(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseGet(() -> createWalletForUser(user));

        // Calculate statistics
        BigDecimal totalAdded = transactionRepository.sumAmountByWalletIdAndType(wallet.getId(), TransactionType.CREDIT);
        BigDecimal totalSpent = transactionRepository.sumAmountByWalletIdAndType(wallet.getId(), TransactionType.DEBIT);
        Long totalTransactions = transactionRepository.countByWalletId(wallet.getId());

        if (totalAdded == null) {
            totalAdded = BigDecimal.ZERO;
        }
        if (totalSpent == null) {
            totalSpent = BigDecimal.ZERO;
        }

        return WalletResponse.builder()
                .id(wallet.getId())
                .balance(wallet.getBalance())
                .totalAdded(totalAdded)
                .totalSpent(totalSpent)
                .totalTransactions(totalTransactions)
                .build();
    }

    @Transactional
    public WalletTransactionResponse addMoney(Long userId, AddMoneyRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseGet(() -> createWalletForUser(user));

        // Update wallet balance
        BigDecimal newBalance = wallet.getBalance().add(request.getAmount());
        wallet.setBalance(newBalance);
        walletRepository.save(wallet);

        // Create transaction record
        WalletTransaction transaction = WalletTransaction.builder()
                .wallet(wallet)
                .type(TransactionType.CREDIT)
                .amount(request.getAmount())
                .description("Money added via " + request.getPaymentMethod())
                .referenceId(request.getPaymentReference())
                .balanceAfter(newBalance)
                .build();

        transaction = transactionRepository.save(transaction);

        return mapToTransactionResponse(transaction);
    }

    @Transactional
    public WalletTransactionResponse debitAmount(Long userId, BigDecimal amount, String description, String referenceId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Wallet not found"));

        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Insufficient balance");
        }

        // Update wallet balance
        BigDecimal newBalance = wallet.getBalance().subtract(amount);
        wallet.setBalance(newBalance);
        walletRepository.save(wallet);

        // Create transaction record
        WalletTransaction transaction = WalletTransaction.builder()
                .wallet(wallet)
                .type(TransactionType.DEBIT)
                .amount(amount)
                .description(description)
                .referenceId(referenceId)
                .balanceAfter(newBalance)
                .build();

        transaction = transactionRepository.save(transaction);

        return mapToTransactionResponse(transaction);
    }

    public List<WalletTransactionResponse> getTransactionHistory(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseGet(() -> createWalletForUser(user));

        List<WalletTransaction> transactions = transactionRepository.findByWalletIdOrderByCreatedAtDesc(wallet.getId());

        return transactions.stream()
                .map(this::mapToTransactionResponse)
                .collect(Collectors.toList());
    }

    /**
     * Initiate withdrawal with OTP - Step 1: Send OTP
     */
    @Transactional
    public WithdrawalOtpResponse initiateWithdrawal(Long userId, WithdrawMoneyRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Wallet not found"));

        // Validate withdrawal amount
        if (request.getAmount().compareTo(BigDecimal.valueOf(100)) < 0) {
            throw new RuntimeException("Minimum withdrawal amount is ₹100");
        }

        if (wallet.getBalance().compareTo(request.getAmount()) < 0) {
            throw new RuntimeException("Insufficient balance");
        }

        // Check if user already has an active OTP
        if (otpService.hasActiveOtp(userId)) {
            throw new RuntimeException("You already have a pending withdrawal OTP. Please verify it or wait for it to expire.");
        }

        // Generate and trigger async email send (do not block HTTP thread)
        WithdrawalOtp otp = otpService.generateAndSendOtp(
                user,
                request.getAmount(),
                request.getWithdrawalMethod(),
                request.getAccountNumber(),
                request.getIfscCode(),
                request.getAccountHolderName(),
                request.getUpiId()
        );

        return WithdrawalOtpResponse.builder()
                .message("OTP has been sent to your registered email address")
                .status("OTP_SENT")
                .otpId(otp.getId().toString())
                .expiresAt(otp.getExpiresAt())
                .amount(request.getAmount())
                .withdrawalMethod(request.getWithdrawalMethod())
                .remainingTimeSeconds(300L) // 5 minutes
                .build();
    }

    /**
     * Complete withdrawal after OTP verification - Step 2: Verify OTP and
     * process withdrawal
     */
    @Transactional
    public WalletTransactionResponse completeWithdrawal(Long userId, String otpCode) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Verify OTP
        WithdrawalOtp verifiedOtp = otpService.verifyOtp(user, otpCode);

        // Create a pending WithdrawalRequest for admin processing
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Wallet not found"));

        if (wallet.getBalance().compareTo(verifiedOtp.getAmount()) < 0) {
            throw new RuntimeException(
                    "Insufficient balance. Your balance may have changed since the OTP was generated.");
        }

        WithdrawalRequest wr = WithdrawalRequest.builder()
                .user(user)
                .amount(verifiedOtp.getAmount())
                .status(WithdrawalStatus.PENDING)
                .method(verifiedOtp.getWithdrawalMethod())
                .upiId(verifiedOtp.getUpiId())
                .accountNumber(verifiedOtp.getAccountNumber())
                .ifscCode(verifiedOtp.getIfscCode())
                .accountHolderName(verifiedOtp.getAccountHolderName())
                .build();
        wr = withdrawalRequestRepository.save(wr);

        // Immediately debit the user's wallet now (funds on hold) and link the transaction
        BigDecimal newBalance = wallet.getBalance().subtract(verifiedOtp.getAmount());
        wallet.setBalance(newBalance);
        walletRepository.save(wallet);

        // Generate a reference like add-money style: PREFIX_<timestamp>_<8hex>
        String referenceId = "WREQ_" + System.currentTimeMillis() + "_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        // Build description without 'Pending Admin'; include reference number for traceability
        String description = "Withdrawal initiated via " + wr.getMethod()
                + ("UPI".equalsIgnoreCase(wr.getMethod()) && wr.getUpiId() != null ? (" to " + wr.getUpiId())
                : ("BANK".equalsIgnoreCase(wr.getMethod()) && wr.getAccountNumber() != null
                ? (" to acct " + wr.getAccountNumber())
                : ""))
                + " (Ref: " + referenceId + ")";

        WalletTransaction transaction = WalletTransaction.builder()
                .wallet(wallet)
                .type(TransactionType.DEBIT)
                .amount(verifiedOtp.getAmount())
                .description(description)
                .referenceId(referenceId)
                .balanceAfter(newBalance)
                .build();
        transaction = transactionRepository.save(transaction);

        // Store reference on the withdrawal request
        wr.setReferenceId(referenceId);
        withdrawalRequestRepository.save(wr);

        // Notify admins about the new withdrawal request
        try {
            emailService.notifyAdminsWithdrawRequest(
                    user.getEmail(),
                    user.getName(),
                    verifiedOtp.getAmount().toPlainString(),
                    wr.getMethod(),
                    wr.getUpiId(),
                    wr.getAccountNumber(),
                    wr.getIfscCode(),
                    wr.getAccountHolderName(),
                    wr.getReferenceId()
            );
        } catch (Exception e) {
            log.warn("Failed to send admin withdraw notification: {}", e.getMessage());
        }

        // Return the real transaction
        return mapToTransactionResponse(transaction);
    }

    @Transactional
    public String adminApproveWithdrawal(Long requestId, String notes) {
        WithdrawalRequest wr = withdrawalRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Withdrawal request not found"));
        if (wr.getStatus() != WithdrawalStatus.PENDING) {
            throw new RuntimeException("Request already processed");
        }

        // Funds are already debited at OTP verification time.
        // Just mark as PAID and notify.
        wr.setStatus(WithdrawalStatus.PAID);
        wr.setAdminNotes(notes);
        withdrawalRequestRepository.save(wr);

        try {
            emailService.sendWithdrawalSuccessNotification(
                    wr.getUser().getEmail(),
                    wr.getUser().getName(),
                    wr.getAmount().toString(),
                    wr.getMethod(),
                    wr.getReferenceId() != null ? wr.getReferenceId() : ("WREQ_" + wr.getId())
            );
        } catch (Exception e) {
            log.warn("Failed to send withdrawal success email to {}: {}", wr.getUser().getEmail(), e.getMessage());
        }
        return "Withdrawal marked as PAID";
    }

    @Transactional
    public String adminRejectWithdrawal(Long requestId, String notes) {
        WithdrawalRequest wr = withdrawalRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Withdrawal request not found"));
        if (wr.getStatus() != WithdrawalStatus.PENDING) {
            throw new RuntimeException("Request already processed");
        }

        // Refund the previously debited amount
        Wallet wallet = walletRepository.findByUserId(wr.getUser().getId())
                .orElseThrow(() -> new RuntimeException("Wallet not found"));

        BigDecimal newBalance = wallet.getBalance().add(wr.getAmount());
        wallet.setBalance(newBalance);
        walletRepository.save(wallet);

        String referenceId = "WRF_" + System.currentTimeMillis();
        String description = "Refund for rejected withdrawal request (" + wr.getMethod() + ")";
        WalletTransaction txn = WalletTransaction.builder()
                .wallet(wallet)
                .type(TransactionType.CREDIT)
                .amount(wr.getAmount())
                .description(description)
                .referenceId(referenceId)
                .balanceAfter(newBalance)
                .build();
        transactionRepository.save(txn);

        wr.setStatus(WithdrawalStatus.REJECTED);
        wr.setAdminNotes(notes);
        // Keep the original debit referenceId on the request for traceability
        withdrawalRequestRepository.save(wr);

        try {
            emailService.sendWithdrawalRejectedNotification(
                    wr.getUser().getEmail(),
                    wr.getUser().getName(),
                    wr.getAmount().toString(),
                    wr.getMethod(),
                    notes
            );
        } catch (Exception e) {
            log.warn("Failed to send withdrawal rejected email to {}: {}", wr.getUser().getEmail(), e.getMessage());
        }

        return "Withdrawal request rejected; funds refunded to wallet";
    }

    @Transactional(readOnly = true)
    public List<com.example.demo.dto.WithdrawalRequestResponse> listPendingWithdrawals() {
        return withdrawalRequestRepository.findByStatusOrderByCreatedAtAsc(WithdrawalStatus.PENDING)
                .stream()
                .map(wr -> com.example.demo.dto.WithdrawalRequestResponse.builder()
                .id(wr.getId())
                .userId(wr.getUser().getId())
                .userEmail(wr.getUser().getEmail())
                .userName(wr.getUser().getName())
                .amount(wr.getAmount())
                .status(wr.getStatus())
                .method(wr.getMethod())
                .upiId(wr.getUpiId())
                .accountNumber(wr.getAccountNumber())
                .ifscCode(wr.getIfscCode())
                .accountHolderName(wr.getAccountHolderName())
                .adminNotes(wr.getAdminNotes())
                .referenceId(wr.getReferenceId())
                .createdAt(wr.getCreatedAt())
                .updatedAt(wr.getUpdatedAt())
                .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public WalletTransactionResponse withdrawMoney(Long userId, WithdrawMoneyRequest request) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Wallet not found"));

        if (wallet.getBalance().compareTo(request.getAmount()) < 0) {
            throw new RuntimeException("Insufficient balance");
        }

        // Update wallet balance
        BigDecimal newBalance = wallet.getBalance().subtract(request.getAmount());
        wallet.setBalance(newBalance);
        walletRepository.save(wallet);

        // Create transaction record
        String description = "Money withdrawn via " + request.getWithdrawalMethod();
        if ("BANK".equals(request.getWithdrawalMethod())) {
            description += " to account " + request.getAccountNumber();
        } else if ("UPI".equals(request.getWithdrawalMethod())) {
            description += " to UPI " + request.getUpiId();
        }

        WalletTransaction transaction = WalletTransaction.builder()
                .wallet(wallet)
                .type(TransactionType.DEBIT)
                .amount(request.getAmount())
                .description(description)
                .referenceId("WTH_" + System.currentTimeMillis()) // Generate withdrawal reference
                .balanceAfter(newBalance)
                .build();

        transaction = transactionRepository.save(transaction);

        return mapToTransactionResponse(transaction);
    }

    public boolean hasInsufficientBalance(Long userId, BigDecimal amount) {
        return walletRepository.findByUserId(userId)
                .map(wallet -> wallet.getBalance().compareTo(amount) < 0)
                .orElse(true);
    }

    public BigDecimal getRequiredAmountToAdd(Long userId, BigDecimal requiredAmount) {
        return walletRepository.findByUserId(userId)
                .map(wallet -> {
                    BigDecimal currentBalance = wallet.getBalance();
                    if (currentBalance.compareTo(requiredAmount) < 0) {
                        return requiredAmount.subtract(currentBalance);
                    }
                    return BigDecimal.ZERO;
                })
                .orElse(requiredAmount); // If no wallet exists, need full amount
    }

    public BigDecimal getCurrentBalance(Long userId) {
        return walletRepository.findByUserId(userId)
                .map(Wallet::getBalance)
                .orElse(BigDecimal.ZERO);
    }

    @Transactional
    public void deductForTournament(Long userId, BigDecimal amount, String description) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Wallet not found"));

        // Check if sufficient balance
        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Insufficient balance");
        }

        // Deduct amount from wallet
        BigDecimal newBalance = wallet.getBalance().subtract(amount);
        wallet.setBalance(newBalance);
        wallet.setUpdatedAt(LocalDateTime.now());
        walletRepository.save(wallet);

        // Create transaction record
        WalletTransaction transaction = WalletTransaction.builder()
                .wallet(wallet)
                .type(TransactionType.DEBIT)
                .amount(amount)
                .description(description)
                .referenceId("TRN_" + System.currentTimeMillis())
                .balanceAfter(newBalance)
                .build();

        transactionRepository.save(transaction);
    }

    @Transactional
    public void refundForTournament(Long userId, BigDecimal amount, String description) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Wallet not found"));

        // Add amount back to wallet
        BigDecimal newBalance = wallet.getBalance().add(amount);
        wallet.setBalance(newBalance);
        wallet.setUpdatedAt(LocalDateTime.now());
        walletRepository.save(wallet);

        // Create refund transaction record
        WalletTransaction transaction = WalletTransaction.builder()
                .wallet(wallet)
                .type(TransactionType.CREDIT)
                .amount(amount)
                .description(description)
                .referenceId("REF_" + System.currentTimeMillis())
                .balanceAfter(newBalance)
                .build();

        transactionRepository.save(transaction);
    }

    @Transactional
    public void creditPrize(Long userId, BigDecimal amount, String description) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseGet(() -> createWalletForUser(user));

        // Add prize amount to wallet
        BigDecimal newBalance = wallet.getBalance().add(amount);
        wallet.setBalance(newBalance);
        wallet.setUpdatedAt(LocalDateTime.now());
        walletRepository.save(wallet);

        // Create prize credit transaction record
        WalletTransaction transaction = WalletTransaction.builder()
                .wallet(wallet)
                .type(TransactionType.CREDIT)
                .amount(amount)
                .description(description)
                .referenceId("PRIZE_" + System.currentTimeMillis())
                .balanceAfter(newBalance)
                .build();

        transactionRepository.save(transaction);
    }

    private WalletTransactionResponse mapToTransactionResponse(WalletTransaction transaction) {
        return WalletTransactionResponse.builder()
                .id(transaction.getId())
                .type(transaction.getType())
                .amount(transaction.getAmount())
                .description(transaction.getDescription())
                .referenceId(transaction.getReferenceId())
                .balanceAfter(transaction.getBalanceAfter())
                .createdAt(transaction.getCreatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public List<com.example.demo.dto.WithdrawalRequestResponse> listMyWithdrawals(Long userId) {
        return withdrawalRequestRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(wr -> com.example.demo.dto.WithdrawalRequestResponse.builder()
                .id(wr.getId())
                .userId(wr.getUser().getId())
                .userEmail(wr.getUser().getEmail())
                .userName(wr.getUser().getName())
                .amount(wr.getAmount())
                .status(wr.getStatus())
                .method(wr.getMethod())
                .upiId(wr.getUpiId())
                .accountNumber(wr.getAccountNumber())
                .ifscCode(wr.getIfscCode())
                .accountHolderName(wr.getAccountHolderName())
                .adminNotes(wr.getAdminNotes())
                .referenceId(wr.getReferenceId())
                .createdAt(wr.getCreatedAt())
                .updatedAt(wr.getUpdatedAt())
                .build())
                .collect(java.util.stream.Collectors.toList());
    }
}
