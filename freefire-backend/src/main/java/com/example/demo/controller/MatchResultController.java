package com.example.demo.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.dto.MatchResultRequest;
import com.example.demo.dto.MatchResultResponse;
import com.example.demo.dto.PrizeDistributionResponse;
import com.example.demo.service.MatchResultService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/match-results")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class MatchResultController {

    private final MatchResultService matchResultService;

    @GetMapping("/{matchId}/participants")
    public ResponseEntity<?> getMatchParticipants(@PathVariable Long matchId) {
        try {
            List<MatchResultResponse> participants = matchResultService.getParticipantsForMatch(matchId);
            return ResponseEntity.ok(participants);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to load participants: " + e.getMessage());
        }
    }

    @GetMapping("/{matchId}/results")
    public ResponseEntity<?> getMatchResults(@PathVariable Long matchId) {
        try {
            List<MatchResultResponse> results = matchResultService.getMatchResults(matchId);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to load results: " + e.getMessage());
        }
    }

    @PutMapping("/{matchId}/update-result")
    public ResponseEntity<?> updateMatchResult(
            @PathVariable Long matchId,
            @RequestBody MatchResultRequest request) {
        try {
            MatchResultResponse response = matchResultService.updateMatchResult(matchId, request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to update result: " + e.getMessage());
        }
    }

    @GetMapping("/{matchId}/prize-distribution")
    public ResponseEntity<?> getPrizeDistribution(@PathVariable Long matchId) {
        try {
            PrizeDistributionResponse response = matchResultService.getPrizeDistribution(matchId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to get prize distribution: " + e.getMessage());
        }
    }

    @PostMapping("/{matchId}/credit-all-prizes")
    public ResponseEntity<String> creditAllPrizes(@PathVariable Long matchId) {
        try {
            String result = matchResultService.creditAllPrizes(matchId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to credit prizes: " + e.getMessage());
        }
    }
}
