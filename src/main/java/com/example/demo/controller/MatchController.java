package com.example.demo.controller;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.dto.MatchCreateRequest;
import com.example.demo.dto.MatchWithRegistrationStatus;
import com.example.demo.entity.Match;
import com.example.demo.entity.MatchStatus;
import com.example.demo.entity.MatchType;
import com.example.demo.entity.User;
import com.example.demo.repository.RegistrationRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.MatchService;

import lombok.RequiredArgsConstructor;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/matches")
@RequiredArgsConstructor
public class MatchController {

    private final MatchService matchService;
    private final UserRepository userRepository;
    private final RegistrationRepository registrationRepository;
    private static final Logger log = LoggerFactory.getLogger(MatchController.class);

    @PostMapping
    public ResponseEntity<Match> create(@RequestBody MatchCreateRequest request) {
        return ResponseEntity.ok(matchService.create(request));
    }

    @GetMapping
    public ResponseEntity<?> list() {
        try {
            List<Match> out = matchService.listAll();
            return ResponseEntity.ok(out);
        } catch (Exception e) {
            log.error("Error listing matches", e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", "Failed to list matches",
                    "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/upcoming")
    public ResponseEntity<?> upcoming() {
        try {
            List<Match> out = matchService.upcoming();
            return ResponseEntity.ok(out);
        } catch (Exception e) {
            log.error("Error listing upcoming matches", e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", "Failed to list upcoming matches",
                    "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/with-status")
    public ResponseEntity<List<MatchWithRegistrationStatus>> getMatchesWithRegistrationStatus(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return ResponseEntity.status(401).body(null);
        }

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Fetch matches once
        List<Match> allMatches = matchService.listAll();

        // Fetch user's registered match IDs in one query
        List<Long> regMatchIds = registrationRepository.findRegisteredMatchIdsForUser(user.getId());
        java.util.Set<Long> registeredSet = new java.util.HashSet<>(regMatchIds);

        List<MatchWithRegistrationStatus> matchesWithStatus = allMatches.stream()
                .map(match -> {
                    boolean isRegistered = match.getId() != null && registeredSet.contains(match.getId());

                    long minutesUntilMatch = 0L;
                    if (match.getScheduledAt() != null) {
                        try {
                            minutesUntilMatch = java.time.Duration.between(
                                    java.time.LocalDateTime.now(),
                                    match.getScheduledAt()
                            ).toMinutes();
                        } catch (Exception ex) {
                            log.warn("Failed to compute minutesUntilMatch for match id={}: {}", match.getId(), ex.getMessage());
                        }
                    }

                    boolean hasEnoughRegistrations = false;
                    try {
                        int required = requiredTeams(match.getMatchType());
                        int teams = match.getRegisteredTeams();
                        hasEnoughRegistrations = teams >= required;
                    } catch (Exception ignore) {
                    }

                    boolean canViewRoomCredentials = isRegistered
                            && minutesUntilMatch <= 5
                            && minutesUntilMatch >= 0
                            && match.getRoomId() != null
                            && match.getRoomPassword() != null
                            && match.getStatus() != MatchStatus.CANCELLED
                            && hasEnoughRegistrations;

                    return MatchWithRegistrationStatus.builder()
                            .match(match)
                            .isRegistered(isRegistered)
                            .canViewRoomCredentials(canViewRoomCredentials)
                            .minutesUntilMatch(minutesUntilMatch)
                            .build();
                })
                .collect(java.util.stream.Collectors.toList());

        return ResponseEntity.ok(matchesWithStatus);
    }

    private int requiredTeams(MatchType type) {
        if (type == null) {
            return 0;
        }
        return switch (type) {
            case SOLO ->
                25;   // minimum solo players
            case DUO ->
                13;     // minimum duo teams
            case SQUAD ->
                7;   // minimum squad teams
        };
    }

    @PutMapping("/{id}")

    public ResponseEntity<Match> updateMatch(@PathVariable Long id, @RequestBody Match match) {
        return ResponseEntity.ok(matchService.updateMatch(id, match));
    }

    @PostMapping("/{id}/credentials")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<Match> saveCredentials(@PathVariable Long id, @RequestBody Map<String, String> request) {
        String roomId = request.get("roomId");
        String roomPassword = request.get("roomPassword");
        return ResponseEntity.ok(matchService.saveCredentials(id, roomId, roomPassword));
    }

    @PostMapping("/{id}/send-credentials")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<String> sendCredentials(@PathVariable Long id) {
        matchService.sendCredentialsToPlayers(id);
        return ResponseEntity.ok("Credentials sent to registered players");
    }

    // Test endpoint to add room credentials without authentication (for testing)
    @PostMapping("/{id}/test-credentials")
    public ResponseEntity<Match> addTestCredentials(@PathVariable Long id) {
        // Add test room credentials for testing
        String testRoomId = "TEST123456";
        String testPassword = "pass123";
        return ResponseEntity.ok(matchService.saveCredentials(id, testRoomId, testPassword));
    }

    // Test endpoint to create a match with immediate credentials (for testing)
    @PostMapping("/create-test-match")
    public ResponseEntity<Match> createTestMatch() {
        MatchCreateRequest request = new MatchCreateRequest();
        request.setTitle("Test Tournament with Room");
        request.setGame("Free Fire");
        request.setMatchType("SQUAD");
        request.setEntryFee(100);
        request.setScheduleDateTime(java.time.LocalDateTime.now().plusMinutes(2).toString()); // 2 minutes from now for testing
        request.setMapName("Bermuda");
        request.setGameMode("SQUAD");
        request.setRules("Test match with room credentials for testing");

        Match createdMatch = matchService.create(request);

        // Add room credentials immediately
        String testRoomId = "ROOM" + createdMatch.getId();
        String testPassword = "pass" + createdMatch.getId();
        Match updatedMatch = matchService.saveCredentials(createdMatch.getId(), testRoomId, testPassword);

        return ResponseEntity.ok(updatedMatch);
    }
}
