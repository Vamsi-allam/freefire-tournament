package com.example.demo.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpiUtrSubmissionRequest {

    // If present, update existing INITIATED record
    private Long paymentId;
    // Always required
    private String utr;
    // New optional fields to allow creation without an INITIATED record
    private BigDecimal amount;      // amount paid by user
    private String paymentApp;      // PhonePe / GooglePay / Paytm / QRCode
    private String referenceId;     // client reference, if generated during initiate
    private String payerUpiId;      // optional: user's UPI id
}
