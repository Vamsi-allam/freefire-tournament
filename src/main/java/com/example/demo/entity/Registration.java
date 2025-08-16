package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "registrations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Registration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @Enumerated(EnumType.STRING)
    private RegistrationStatus status; // PENDING, CONFIRMED, CANCELLED

    private int slotNumber; // Random slot allocated (1-48 for solo, 1-24 for duo, 1-12 for squad)

    private double amountPaid; // Entry fee paid

    @Enumerated(EnumType.STRING)
    private PaymentStatus paymentStatus; // PENDING, COMPLETED, FAILED, REFUNDED

    private String transactionId; // For payment tracking

    @OneToMany(mappedBy = "registration", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<RegistrationPlayer> players; // List of players in this registration

    private LocalDateTime registeredAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        registeredAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
