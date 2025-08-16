package com.example.demo.dto;

import lombok.Data;
import java.util.List;

@Data
public class RegistrationRequest {

    private Long matchId;
    private List<PlayerDetails> players;
    private String paymentMethod; // "wallet", "card", etc.
    private String transactionReference; // For payment tracking

    @Data
    public static class PlayerDetails {

        private String playerName;
        private String gameName;
        private String gameId;
        private String role; // "LEADER" or "MEMBER"
    }
}
