package com.example.demo.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchResultResponse {

    private Long id;
    private Long registrationId;
    private Long userId;
    private Long matchId;
    private String playerName;
    private String playerGameName;
    private String playerGameId;
    private String teamName;
    private Integer position;
    private Integer kills;
    private BigDecimal prizeAmount;
    private Boolean prizeCredited;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
