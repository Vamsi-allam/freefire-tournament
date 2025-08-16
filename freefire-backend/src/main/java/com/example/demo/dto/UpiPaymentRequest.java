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
public class UpiPaymentRequest {

    private BigDecimal amount;
    private String payerUpiId; // optional, user's UPI id (for record)
    private String paymentApp; // optional, PhonePe / GooglePay / Paytm / QRCode
}
