package com.example.demo.dto;

import lombok.Data;

@Data
public class MatchCreateRequest {

    private String title; // determines type
    private String game; // Free Fire
    private String matchType; // SOLO/DUO/SQUAD optional derived
    private Integer entryFee; // per slot
    private String scheduleDateTime; // ISO string
    private String mapName;
    private String gameMode; // SOLO/DUO/SQUAD
    private String rules; // shared rules
}
