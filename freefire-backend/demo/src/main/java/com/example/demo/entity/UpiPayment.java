package com.example.demo.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "upi_payments", indexes = {
    @Index(name = "idx_upi_user", columnList = "user_id"),
    @Index(name = "idx_upi_status", columnList = "status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpiPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "upi_id")
    private String upiId; // optional: user's upi id (for record)

    @Column(name = "payee_vpa")
    private String payeeVpa; // your business VPA e.g., gamearena@okaxis

    @Column(name = "payee_name")
    private String payeeName;

    @Column(name = "note")
    private String note; // remark shown in UPI apps

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UpiPaymentStatus status;

    @Column(name = "utr", length = 64)
    private String utr; // user submitted reference number

    @Column(name = "qr_code_url")
    private String qrCodeUrl; // optional, if you generate and host a QR image

    @Column(name = "payment_app")
    private String paymentApp; // PhonePe / GooglePay / Paytm / QRCode

    @Column(name = "reference_id", nullable = false)
    private String referenceId; // e.g., UPI_1755263096694_d279100f

    @Column(name = "admin_notes")
    private String adminNotes;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "utr_number")
    private String utrNumber; // legacy/alternate column name; mirror of "utr"

    @Column(name = "utr_submitted_at")
    private LocalDateTime utrSubmittedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
        if (this.status == null) {
            this.status = UpiPaymentStatus.INITIATED;
        }
        if (this.upiId == null) {
            this.upiId = ""; // DB may require non-null; keep empty if not provided
        }
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
