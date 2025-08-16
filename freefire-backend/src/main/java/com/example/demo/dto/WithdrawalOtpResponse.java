package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WithdrawalOtpResponse {

    private String message;
    private String status; // "OTP_SENT", "OTP_VERIFIED", "WITHDRAWAL_COMPLETED"
    private String otpId;
    private LocalDateTime expiresAt;
    private BigDecimal amount;
    private String withdrawalMethod;
    private Long remainingTimeSeconds;
}
