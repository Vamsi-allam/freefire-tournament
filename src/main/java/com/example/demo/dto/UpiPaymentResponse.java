package com.example.demo.dto;

import com.example.demo.entity.UpiPaymentStatus;
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
public class UpiPaymentResponse {

    private Long id;
    private BigDecimal amount;
    private String deeplink;
    private String qrCodeUrl;
    private String payeeVpa;
    private String payeeName;
    private String note;
    private String utr;
    private String referenceId;
    private String paymentApp;
    private UpiPaymentStatus status;
    private LocalDateTime createdAt;
}
