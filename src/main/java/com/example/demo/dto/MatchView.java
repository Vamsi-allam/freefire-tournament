package com.example.demo.dto;

import java.time.LocalDateTime;

import com.example.demo.entity.MatchStatus;
import com.example.demo.entity.MatchType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchView {
    private Long id;

    private String title;
    private String game;
    private MatchType matchType;
    private MatchStatus status;
    private int slots;
    private int entryFee;
    private int prizePool;
    private int prizeFirst;
    private int prizeSecond;
    private int prizeThird;
    private LocalDateTime scheduledAt;
    private String mapName;
    private String gameMode;
    private String rules;
    private String roomId;
    private String roomPassword;
    private boolean credentialsSent;
    private int registeredTeams;
}
