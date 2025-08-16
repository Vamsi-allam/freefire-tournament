package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "registration_players")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegistrationPlayer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registration_id", nullable = false)
    private Registration registration;

    @Column(nullable = false)
    private String playerName; // Player's real name

    @Column(nullable = false)
    private String gameName; // In-game name

    @Column(nullable = false)
    private String gameId; // In-game ID

    @Enumerated(EnumType.STRING)
    private PlayerRole role; // LEADER, MEMBER (for team identification)

    private int playerPosition; // 1, 2, 3, 4 (for squad ordering)
}
