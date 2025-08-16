package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.example.demo.entity.Match;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchWithRegistrationStatus {

    private Match match;
    private boolean isRegistered;
    private boolean canViewRoomCredentials;
    private long minutesUntilMatch;
}
