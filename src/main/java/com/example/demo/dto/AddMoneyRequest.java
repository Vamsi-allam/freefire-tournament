package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddMoneyRequest {

    private BigDecimal amount;
    private String paymentMethod; // "UPI", "CARD", "NETBANKING", etc.
    private String paymentReference; // Payment gateway reference
}
