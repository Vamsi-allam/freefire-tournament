package com.example.demo.dto;

import com.example.demo.entity.TransactionType;
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
public class WalletTransactionResponse {

    private Long id;
    private TransactionType type;
    private BigDecimal amount;
    private String description;
    private String referenceId;
    private BigDecimal balanceAfter;
    private LocalDateTime createdAt;
}
