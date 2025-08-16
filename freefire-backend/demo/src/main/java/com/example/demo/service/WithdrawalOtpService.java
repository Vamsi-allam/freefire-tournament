package com.example.demo.service;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.entity.User;
import com.example.demo.entity.WithdrawalOtp;
import com.example.demo.repository.WithdrawalOtpRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class WithdrawalOtpService {

    private final WithdrawalOtpRepository otpRepository;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generate and send OTP for withdrawal
     */
    @Transactional
    public WithdrawalOtp generateAndSendOtp(User user, BigDecimal amount, String withdrawalMethod,
            String accountNumber, String ifscCode, String accountHolderName,
            String upiId) {

        // Check if user already has an active OTP
        Optional<WithdrawalOtp> existingOtp = otpRepository.findActiveOtpByUserId(user.getId(), LocalDateTime.now());
        if (existingOtp.isPresent()) {
            throw new RuntimeException("An OTP has already been sent. Please wait for it to expire before requesting a new one.");
        }

        // Generate 6-digit OTP
        String otpCode = generateOtpCode();

        // Create OTP entity
        WithdrawalOtp otp = WithdrawalOtp.builder()
                .user(user)
                .otpCode(otpCode)
                .amount(amount)
                .withdrawalMethod(withdrawalMethod)
                .accountNumber(accountNumber)
                .ifscCode(ifscCode)
                .accountHolderName(accountHolderName)
                .upiId(upiId)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(5)) // 5 minutes expiry
                .isVerified(false)
                .build();

        // Save OTP
        otp = otpRepository.save(otp);

        // Trigger async email (fire-and-forget). Any exception is logged inside the async method.
        sendOtpEmailAsync(user.getEmail(), otpCode, user.getName(), amount.toString());

        log.info("OTP generated for user {} for withdrawal amount {}", user.getId(), amount);

        return otp;
    }

    /**
     * Verify OTP and return the withdrawal details if valid
     */
    @Transactional
    public WithdrawalOtp verifyOtp(User user, String otpCode) {
        Optional<WithdrawalOtp> otpOpt = otpRepository.findValidOtpByUserIdAndCode(
                user.getId(), otpCode, LocalDateTime.now());

        if (otpOpt.isEmpty()) {
            throw new RuntimeException("Invalid or expired OTP. Please request a new one.");
        }

        WithdrawalOtp otp = otpOpt.get();

        // Mark as verified
        otp.setIsVerified(true);
        otp.setVerifiedAt(LocalDateTime.now());

        // Save the verification
        otp = otpRepository.save(otp);

        log.info("OTP verified successfully for user {} withdrawal amount {}", user.getId(), otp.getAmount());

        return otp;
    }

    /**
     * Check if user has an active (pending) OTP
     */
    public boolean hasActiveOtp(Long userId) {
        return otpRepository.findActiveOtpByUserId(userId, LocalDateTime.now()).isPresent();
    }

    /**
     * Get active OTP details for user
     */
    public Optional<WithdrawalOtp> getActiveOtp(Long userId) {
        return otpRepository.findActiveOtpByUserId(userId, LocalDateTime.now());
    }

    /**
     * Generate secure 6-digit OTP
     */
    private String generateOtpCode() {
        int otp = 100000 + secureRandom.nextInt(900000); // Generate 6-digit number
        return String.valueOf(otp);
    }

    /**
     * Send OTP email asynchronously
     */
    @Async
    private void sendOtpEmailAsync(String email, String otpCode, String userName, String amount) {
        log.info("Starting async email send for OTP: {} to email: {}", otpCode, email);
        try {
            emailService.sendWithdrawalOtp(email, otpCode, userName, amount);
            log.info("Async email send completed successfully for: {}", email);
        } catch (Exception e) {
            log.error("Failed to send OTP email to {} - Error: {}", email, e.getMessage(), e);
            // We don't throw exception here to avoid rolling back the OTP creation
        }
    }

    /**
     * Clean up expired OTPs (scheduled task) Runs every 10 minutes
     */
    @Scheduled(fixedRate = 600000) // 10 minutes
    @Transactional
    public void cleanupExpiredOtps() {
        try {
            LocalDateTime now = LocalDateTime.now();

            // Delete expired unverified OTPs
            otpRepository.deleteExpiredOtps(now);

            // Delete old verified OTPs (older than 24 hours)
            LocalDateTime cutoff = now.minusHours(24);
            otpRepository.deleteOldVerifiedOtps(cutoff);

            log.debug("Cleaned up expired and old OTPs");
        } catch (Exception e) {
            log.error("Error during OTP cleanup", e);
        }
    }

    /**
     * Cancel active OTP for user (if they want to change withdrawal details)
     */
    @Transactional
    public void cancelActiveOtp(Long userId) {
        Optional<WithdrawalOtp> activeOtp = otpRepository.findActiveOtpByUserId(userId, LocalDateTime.now());
        if (activeOtp.isPresent()) {
            WithdrawalOtp otp = activeOtp.get();
            // Set expiry to now to make it inactive
            otp.setExpiresAt(LocalDateTime.now().minusMinutes(1));
            otpRepository.save(otp);
            log.info("Cancelled active OTP for user {}", userId);
        }
    }
}
