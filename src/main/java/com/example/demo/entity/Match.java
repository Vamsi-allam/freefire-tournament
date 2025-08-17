package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "matches")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title; // e.g. Free Fire Solo Battle
    private String game;  // Free Fire

    @Enumerated(EnumType.STRING)
    private MatchType matchType; // SOLO, DUO, SQUAD

    @Enumerated(EnumType.STRING)
    private MatchStatus status; // OPEN, UPCOMING, LIVE, COMPLETED

    private int slots; // 48 solo, 24 duo, 12 squad
    private int entryFee; // per slot/team or per player depending on type

    private int prizePool; // total computed
    private int prizeFirst;
    private int prizeSecond;
    private int prizeThird;

    private LocalDateTime scheduledAt;

    private String mapName; // Bermuda etc.
    private String gameMode; // SOLO / DUO / SQUAD

    // Clash Squad specific fields
    // Number of rounds for Clash Squad (usually 7 or 13). Null for BR modes.
    private Integer rounds;
    // For Clash Squad, number of team slots (always 2 for 4v4). Null for BR modes.
    private Integer teamSlots;

    @Column(length = 2000)
    private String rules; // shared tournament rules

    // Room credentials
    private String roomId;
    private String roomPassword;
    private boolean credentialsSent; // track if credentials sent to players

    // Registration tracking
    private int registeredTeams; // current registrations
}
