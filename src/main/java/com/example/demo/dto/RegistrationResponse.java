package com.example.demo.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RegistrationResponse {

    private Long id;
    private Long matchId;
    private String matchTitle;
    private String status;
    private int slotNumber;
    private double amountPaid;
    private String paymentStatus;
    private LocalDateTime registeredAt;
    private List<PlayerInfo> players;
    private MatchInfo match;
    private ResultInfo result; // optional, present when results are published

    @Data
    @Builder
    public static class PlayerInfo {

        private String playerName;
        private String gameName;
        private String gameId;
        private String role;
        private int position;
    }

    @Data
    @Builder
    public static class MatchInfo {

        private Long id;
        private String title;
        private String matchType;
        private String status;
        private LocalDateTime scheduledAt;
        private int entryFee;
        private int prizePool;
        private String roomId;
        private String roomPassword;
    }

    @Data
    @Builder
    public static class ResultInfo {

        private Integer position;
        private Integer kills;
        private BigDecimal prize;        // alias for prizeAmount for frontend convenience
        private Boolean prizeCredited;
        private LocalDateTime updatedAt;
    }
}
