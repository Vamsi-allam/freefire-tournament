package com.example.demo.service;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.dto.UpiAdminActionRequest;
import com.example.demo.dto.UpiPaymentRequest;
import com.example.demo.dto.UpiPaymentResponse;
import com.example.demo.dto.UpiUtrSubmissionRequest;
import com.example.demo.entity.TransactionType;
import com.example.demo.entity.UpiPayment;
import com.example.demo.entity.UpiPaymentStatus;
import com.example.demo.entity.User;
import com.example.demo.entity.Wallet;
import com.example.demo.entity.WalletTransaction;
import com.example.demo.repository.UpiPaymentRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.WalletRepository;
import com.example.demo.repository.WalletTransactionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UpiPaymentService {

    private final UpiPaymentRepository upiPaymentRepository;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;
    private final EmailService emailService;

    // Receiving UPI ID and name (must be provided via env/properties)
    @Value("${business.upi.vpa:${BUSINESS_UPI_VPA:}}")
    private String businessVpa;

    @Value("${business.upi.name:${BUSINESS_UPI_NAME:}}")
    private String businessName;

    private String buildUpiDeepLink(BigDecimal amount, String note) {
        String upi = "upi://pay?pa=" + url(businessVpa)
                + "&pn=" + url(businessName)
                + "&am=" + url(amount.toPlainString())
                + "&cu=INR"
                + (note != null && !note.isBlank() ? ("&tn=" + url(note)) : "");
        return upi;
    }

    private String url(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    @Transactional(readOnly = true)
    public UpiPaymentResponse initiatePayment(Long userId, UpiPaymentRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.valueOf(10)) < 0) {
            throw new RuntimeException("Minimum add amount is ₹10");
        }

        if (businessVpa == null || businessVpa.isBlank() || businessName == null || businessName.isBlank()) {
            throw new RuntimeException("Business UPI configuration missing. Set BUSINESS_UPI_VPA and BUSINESS_UPI_NAME");
        }
        String note = "Add Money - " + user.getName();

        // Generate a unique reference id like UPI_<timestamp>_<8-hex>
        String referenceId = generateReferenceId();

        // Do NOT persist on initiate; only return details for client-side payment
        return UpiPaymentResponse.builder()
                .id(null)
                .amount(request.getAmount())
                .deeplink(buildUpiDeepLink(request.getAmount(), note))
                .qrCodeUrl(null) // frontend can generate QR from deeplink
                .payeeVpa(businessVpa)
                .payeeName(businessName)
                .note(note)
                .utr(null)
                .referenceId(referenceId)
                .paymentApp(request.getPaymentApp())
                .status(UpiPaymentStatus.INITIATED)
                .createdAt(null)
                .build();
    }

    @Transactional
    public UpiPaymentResponse submitUtr(Long userId, UpiUtrSubmissionRequest request) {
        UpiPayment payment;
        if (request.getPaymentId() != null) {
            payment = upiPaymentRepository.findById(request.getPaymentId())
                    .orElseThrow(() -> new RuntimeException("Payment not found"));

            if (!payment.getUser().getId().equals(userId)) {
                throw new RuntimeException("Unauthorized");
            }
            if (payment.getStatus() != UpiPaymentStatus.INITIATED) {
                throw new RuntimeException("UTR already submitted or processed");
            }
        } else {
            // Create a new UPI record now (store only when UTR is provided)
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            if (request.getAmount() == null) {
                throw new RuntimeException("Amount is required when paymentId is not provided");
            }
            if (businessVpa == null || businessVpa.isBlank() || businessName == null || businessName.isBlank()) {
                throw new RuntimeException("Business UPI configuration missing. Set BUSINESS_UPI_VPA and BUSINESS_UPI_NAME");
            }
            String note = "Add Money - " + user.getName();
            payment = UpiPayment.builder()
                    .user(user)
                    .amount(request.getAmount())
                    .upiId(request.getPayerUpiId())
                    .payeeVpa(businessVpa)
                    .payeeName(businessName)
                    .note(note)
                    .referenceId(request.getReferenceId() != null ? request.getReferenceId() : generateReferenceId())
                    .paymentApp(request.getPaymentApp())
                    .status(UpiPaymentStatus.INITIATED)
                    .build();
        }

        payment.setUtr(request.getUtr());
        payment.setUtrNumber(request.getUtr());
        payment.setUtrSubmittedAt(LocalDateTime.now());
        payment.setStatus(UpiPaymentStatus.UTR_SUBMITTED);
        upiPaymentRepository.save(payment);

        // Notify admins that a credit request (UTR submitted) is pending review
        try {
            emailService.notifyAdminsCreditRequest(
                    payment.getUser().getEmail(),
                    payment.getUser().getName(),
                    payment.getAmount() != null ? payment.getAmount().toPlainString() : (request.getAmount() != null ? request.getAmount().toPlainString() : null),
                    payment.getPaymentApp(),
                    payment.getUpiId(),
                    payment.getUtr(),
                    payment.getReferenceId()
            );
        } catch (Exception e) {
            // Do not block on email failures
        }

        return UpiPaymentResponse.builder()
                .id(payment.getId())
                .amount(payment.getAmount())
                .deeplink(null)
                .qrCodeUrl(payment.getQrCodeUrl())
                .payeeVpa(payment.getPayeeVpa())
                .payeeName(payment.getPayeeName())
                .note(payment.getNote())
                .utr(payment.getUtr())
                .referenceId(payment.getReferenceId())
                .paymentApp(payment.getPaymentApp())
                .status(payment.getStatus())
                .createdAt(payment.getCreatedAt())
                .build();
    }

    @Transactional
    public String adminAction(UpiAdminActionRequest request) {
        UpiPayment payment = upiPaymentRepository.findById(request.getPaymentId())
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        if ("APPROVE".equalsIgnoreCase(request.getAction())) {
            // Credit wallet
            User user = payment.getUser();
            Wallet wallet = walletRepository.findByUserId(user.getId())
                    .orElseGet(() -> walletRepository.save(Wallet.builder().user(user).balance(BigDecimal.ZERO).build()));

            BigDecimal newBalance = wallet.getBalance().add(payment.getAmount());
            wallet.setBalance(newBalance);
            wallet.setUpdatedAt(LocalDateTime.now());
            walletRepository.save(wallet);

            // Record transaction
            WalletTransaction txn = WalletTransaction.builder()
                    .wallet(wallet)
                    .type(TransactionType.CREDIT)
                    .amount(payment.getAmount())
                    .description("UPI Add Money" + (payment.getUtr() != null ? (" (UTR: " + payment.getUtr() + ")") : ""))
                    .referenceId(payment.getReferenceId())
                    .balanceAfter(newBalance)
                    .build();
            transactionRepository.save(txn);

            payment.setStatus(UpiPaymentStatus.APPROVED);
            payment.setApprovedAt(LocalDateTime.now());
            payment.setApprovedBy("ADMIN"); // optionally set from auth principal later
            upiPaymentRepository.save(payment);
            return "Payment approved and wallet credited";
        } else if ("REJECT".equalsIgnoreCase(request.getAction())) {
            payment.setStatus(UpiPaymentStatus.REJECTED);
            upiPaymentRepository.save(payment);
            return "Payment rejected";
        } else {
            throw new RuntimeException("Unknown action");
        }
    }

    public List<UpiPaymentResponse> listPending() {
        return upiPaymentRepository.findByStatus(UpiPaymentStatus.UTR_SUBMITTED)
                .stream()
                .map(p -> UpiPaymentResponse.builder()
                .id(p.getId())
                .amount(p.getAmount())
                .deeplink(null)
                .qrCodeUrl(p.getQrCodeUrl())
                .payeeVpa(p.getPayeeVpa())
                .payeeName(p.getPayeeName())
                .note(p.getNote())
                .utr(p.getUtr())
                .referenceId(p.getReferenceId())
                .paymentApp(p.getPaymentApp())
                .status(p.getStatus())
                .createdAt(p.getCreatedAt())
                .build())
                .collect(Collectors.toList());
    }

    public List<UpiPaymentResponse> listMy(Long userId) {
        return upiPaymentRepository.findByUserIdAndStatusNotOrderByCreatedAtDesc(userId, UpiPaymentStatus.INITIATED)
                .stream()
                .map(p -> UpiPaymentResponse.builder()
                .id(p.getId())
                .amount(p.getAmount())
                .deeplink(null)
                .qrCodeUrl(p.getQrCodeUrl())
                .payeeVpa(p.getPayeeVpa())
                .payeeName(p.getPayeeName())
                .note(p.getNote())
                .utr(p.getUtr())
                .referenceId(p.getReferenceId())
                .paymentApp(p.getPaymentApp())
                .status(p.getStatus())
                .createdAt(p.getCreatedAt())
                .build())
                .collect(Collectors.toList());
    }

    private String generateReferenceId() {
        long ts = System.currentTimeMillis();
        String hex = java.util.UUID.randomUUID().toString().replace("-", "");
        String suffix = hex.substring(0, 8);
        return "UPI_" + ts + "_" + suffix;
    }
}
