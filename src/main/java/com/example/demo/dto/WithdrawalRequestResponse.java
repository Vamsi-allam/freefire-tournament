package com.example.demo.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.example.demo.entity.WithdrawalStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WithdrawalRequestResponse {

    private Long id;
    private Long userId;
    private String userEmail;
    private String userName;
    private BigDecimal amount;
    private WithdrawalStatus status;
    private String method; // UPI or BANK
    private String upiId;
    private String accountNumber;
    private String ifscCode;
    private String accountHolderName;
    private String adminNotes;
    private String referenceId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
