package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchResultRequest {

    private Long registrationId;
    private Long userId;
    private String playerName;
    private String teamName;
    private Integer position;
    private Integer kills;
}
