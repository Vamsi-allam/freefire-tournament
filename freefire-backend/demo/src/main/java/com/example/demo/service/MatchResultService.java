package com.example.demo.service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.dto.MatchResultRequest;
import com.example.demo.dto.MatchResultResponse;
import com.example.demo.dto.PrizeDistributionResponse;
import com.example.demo.entity.Match;
import com.example.demo.entity.MatchResult;
import com.example.demo.entity.Registration;
import com.example.demo.entity.RegistrationPlayer;
import com.example.demo.entity.RegistrationStatus;
import com.example.demo.repository.MatchRepository;
import com.example.demo.repository.MatchResultRepository;
import com.example.demo.repository.RegistrationRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MatchResultService {

    private final MatchResultRepository matchResultRepository;
    private final RegistrationRepository registrationRepository;
    private final MatchRepository matchRepository;
    private final WalletService walletService;

    @Transactional(readOnly = true)
    public List<MatchResultResponse> getMatchResults(Long matchId) {
        List<MatchResult> results = matchResultRepository.findByMatchIdOrderByPositionAsc(matchId);
        return results.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MatchResultResponse> getParticipantsForMatch(Long matchId) {
        // Get all registrations for this match with eager user & match to avoid lazy init issues
        List<Registration> registrations = registrationRepository.findByMatchIdAndStatusWithMatchAndUser(matchId, RegistrationStatus.CONFIRMED);

        return registrations.stream()
                .map(registration -> {
                    // Check if result already exists
                    MatchResult existingResult = matchResultRepository.findByRegistrationId(registration.getId()).orElse(null);

                    if (existingResult != null) {
                        return mapToResponse(existingResult);
                    } else {
                        // Return participant without result data
                        // Choose primary player data if present
                        String gName = null;
                        String gId = null;
                        try {
                            if (registration.getPlayers() != null && !registration.getPlayers().isEmpty()) {
                                var primary = registration.getPlayers().stream()
                                        .sorted((a, b) -> Integer.compare(a.getPlayerPosition(), b.getPlayerPosition()))
                                        .findFirst().orElse(null);
                                if (primary != null) {
                                    gName = primary.getGameName();
                                    gId = primary.getGameId();
                                }
                            }
                        } catch (Exception ignored) {
                        }
                        return MatchResultResponse.builder()
                                .registrationId(registration.getId())
                                .userId(registration.getUser().getId())
                                .matchId(matchId)
                                .playerName(registration.getUser().getName())
                                .playerGameName(gName)
                                .playerGameId(gId)
                                .teamName("Team " + registration.getSlotNumber())
                                .position(null)
                                .kills(null)
                                .prizeAmount(BigDecimal.ZERO)
                                .prizeCredited(false)
                                .build();
                    }
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public MatchResultResponse updateMatchResult(Long matchId, MatchResultRequest request) {
        Registration registration = registrationRepository.findById(request.getRegistrationId())
                .orElseThrow(() -> new RuntimeException("Registration not found"));

        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new RuntimeException("Match not found"));

        // Check if result already exists
        MatchResult result = matchResultRepository.findByRegistrationId(request.getRegistrationId())
                .orElse(MatchResult.builder()
                        .registration(registration)
                        .match(match)
                        .user(registration.getUser())
                        .prizeCredited(false)
                        .build());

        // Update result data
        result.setPosition(request.getPosition());
        result.setKills(request.getKills());

        // Calculate prize amount based on match type (SOLO kills-based, DUO/SQUAD position-based)
        BigDecimal prizeAmount = calculatePrizeAmount(match, request.getPosition(), request.getKills());
        result.setPrizeAmount(prizeAmount);

        result = matchResultRepository.save(result);
        return mapToResponse(result);
    }

    @Transactional(readOnly = true)
    public PrizeDistributionResponse getPrizeDistribution(Long matchId) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new RuntimeException("Match not found"));

        List<MatchResult> results = matchResultRepository.findByMatchId(matchId);

        // Dynamic pool based on actual confirmed registrations
        int confirmed = registrationRepository.countConfirmedRegistrationsByMatchId(matchId);
        BigDecimal totalPrizePool = BigDecimal.valueOf((long) match.getEntryFee() * confirmed);

        // Build distributions based on match type
        List<PrizeDistributionResponse.PrizeDistributionDetail> distributions;
        switch (match.getMatchType()) {
            case SOLO:
                // SOLO: per-kill payout at 80% of entry fee per kill
                BigDecimal perKill = BigDecimal.valueOf(match.getEntryFee())
                        .multiply(new BigDecimal("0.80"))
                        .setScale(0, java.math.RoundingMode.HALF_UP);
                distributions = results.stream()
                        .filter(r -> (r.getKills() != null) && (r.getKills() > 0))
                        .sorted(Comparator.comparing(MatchResult::getKills, Comparator.nullsFirst(Integer::compareTo)).reversed())
                        .map(r -> PrizeDistributionResponse.PrizeDistributionDetail.builder()
                        .userId(r.getUser().getId())
                        .playerName(r.getUser().getName())
                        .teamName("Team " + r.getRegistration().getSlotNumber())
                        .position(null)
                        .kills(r.getKills())
                        .prizeAmount(perKill.multiply(BigDecimal.valueOf(r.getKills() == null ? 0L : r.getKills().longValue())))
                        .alreadyCredited(r.getPrizeCredited())
                        .build())
                        .collect(Collectors.toList());
                break;
            case DUO:
                // DUO: Top 5 by position with 40%, 30%, 20%, 5%, 5%
                BigDecimal[] duoPercents = new BigDecimal[]{
                    new BigDecimal("0.40"), new BigDecimal("0.30"), new BigDecimal("0.20"),
                    new BigDecimal("0.05"), new BigDecimal("0.05")
                };
                distributions = results.stream()
                        .filter(r -> r.getPosition() != null && r.getPosition() >= 1 && r.getPosition() <= 5)
                        .sorted((a, b) -> Integer.compare(a.getPosition(), b.getPosition()))
                        .map(r -> PrizeDistributionResponse.PrizeDistributionDetail.builder()
                        .userId(r.getUser().getId())
                        .playerName(r.getUser().getName())
                        .teamName("Team " + r.getRegistration().getSlotNumber())
                        .position(r.getPosition())
                        .kills(r.getKills())
                        .prizeAmount(totalPrizePool.multiply(duoPercents[r.getPosition() - 1]).setScale(0, java.math.RoundingMode.HALF_UP))
                        .alreadyCredited(r.getPrizeCredited())
                        .build())
                        .collect(Collectors.toList());
                break;
            case SQUAD:
            default:
                // SQUAD and fallback: Top 3 by 40/30/20
                BigDecimal[] trioPercents = new BigDecimal[]{
                    new BigDecimal("0.40"), new BigDecimal("0.30"), new BigDecimal("0.20")
                };
                distributions = results.stream()
                        .filter(r -> r.getPosition() != null && r.getPosition() >= 1 && r.getPosition() <= 3)
                        .sorted((a, b) -> Integer.compare(a.getPosition(), b.getPosition()))
                        .map(r -> PrizeDistributionResponse.PrizeDistributionDetail.builder()
                        .userId(r.getUser().getId())
                        .playerName(r.getUser().getName())
                        .teamName("Team " + r.getRegistration().getSlotNumber())
                        .position(r.getPosition())
                        .kills(r.getKills())
                        .prizeAmount(totalPrizePool.multiply(trioPercents[r.getPosition() - 1]).setScale(0, java.math.RoundingMode.HALF_UP))
                        .alreadyCredited(r.getPrizeCredited())
                        .build())
                        .collect(Collectors.toList());
                break;
        }

        BigDecimal toBeDistributed = distributions.stream()
                .map(PrizeDistributionResponse.PrizeDistributionDetail::getPrizeAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return PrizeDistributionResponse.builder()
                .totalPrizePool(totalPrizePool)
                .toBeDistributed(toBeDistributed)
                .undistributedRemainder(totalPrizePool.subtract(toBeDistributed))
                .winnersCount(distributions.size())
                .distributions(distributions)
                .build();
    }

    @Transactional
    public String creditAllPrizes(Long matchId) {
        List<MatchResult> uncreditedResults = matchResultRepository.findUncreditedResultsByMatchId(matchId);

        int creditedCount = 0;
        BigDecimal totalCredited = BigDecimal.ZERO;

        for (MatchResult result : uncreditedResults) {
            // Recompute prize under current rules
            BigDecimal dynamicPrize = calculatePrizeAmount(result.getMatch(), result.getPosition(), result.getKills());
            result.setPrizeAmount(dynamicPrize);
            matchResultRepository.save(result);

            if (dynamicPrize != null && dynamicPrize.compareTo(BigDecimal.ZERO) > 0) {
                try {
                    // Credit the prize to user's wallet
                    String desc;
                    if (result.getMatch().getMatchType() == com.example.demo.entity.MatchType.SOLO) {
                        String kStr = String.valueOf(Optional.ofNullable(result.getKills()).orElse(0));
                        desc = "Tournament Prize - " + kStr + " Kills - " + result.getMatch().getTitle();
                    } else {
                        desc = "Tournament Prize - Position " + result.getPosition() + " - " + result.getMatch().getTitle();
                    }
                    walletService.creditPrize(result.getUser().getId(), dynamicPrize, desc);

                    // Mark as credited
                    result.setPrizeCredited(true);
                    matchResultRepository.save(result);

                    creditedCount++;
                    totalCredited = totalCredited.add(dynamicPrize);
                } catch (Exception e) {
                    // Log error but continue with other prizes
                    System.err.println("Failed to credit prize for user " + result.getUser().getId() + ": " + e.getMessage());
                }
            }
        }

        return String.format("Successfully credited ₹%.2f to %d winners", totalCredited, creditedCount);
    }

    private BigDecimal calculatePrizeAmount(Match match, Integer position, Integer kills) {
        // SOLO: per-kill payout of 80% of entry fee
        if (match.getMatchType() == com.example.demo.entity.MatchType.SOLO) {
            int k = kills == null ? 0 : kills;
            if (k <= 0) {
                return BigDecimal.ZERO;
            }
            BigDecimal perKill = BigDecimal.valueOf(match.getEntryFee())
                    .multiply(new BigDecimal("0.80"))
                    .setScale(0, java.math.RoundingMode.HALF_UP);
            return perKill.multiply(BigDecimal.valueOf(k));
        }

        // DUO: Top 5 (40%, 30%, 20%, 5%, 5%)
        // SQUAD: Top 3 (40%, 30%, 20%)
        if (position == null || position <= 0) {
            return BigDecimal.ZERO;
        }

        int confirmed = registrationRepository.countConfirmedRegistrationsByMatchId(match.getId());
        BigDecimal pool = BigDecimal.valueOf((long) match.getEntryFee() * confirmed);

        BigDecimal[] percents;
        if (match.getMatchType() == com.example.demo.entity.MatchType.DUO) {
            percents = new BigDecimal[]{new BigDecimal("0.40"), new BigDecimal("0.30"), new BigDecimal("0.20"), new BigDecimal("0.05"), new BigDecimal("0.05")};
            if (position > 5) {
                return BigDecimal.ZERO;
            }
        } else { // SQUAD or default
            percents = new BigDecimal[]{new BigDecimal("0.40"), new BigDecimal("0.30"), new BigDecimal("0.20")};
            if (position > 3) {
                return BigDecimal.ZERO;
            }
        }

        BigDecimal share = pool.multiply(percents[position - 1]);
        return share.setScale(0, java.math.RoundingMode.HALF_UP);
    }

    private MatchResultResponse mapToResponse(MatchResult result) {
        // Derive primary player details (leader if available else first by position) from the registration
        String playerGameName = null;
        String playerGameId = null;
        try {
            Registration reg = result.getRegistration();
            if (reg != null && reg.getPlayers() != null && !reg.getPlayers().isEmpty()) {
                RegistrationPlayer primary = reg.getPlayers().stream()
                        .sorted((a, b) -> Integer.compare(a.getPlayerPosition(), b.getPlayerPosition()))
                        .findFirst().orElse(null);
                if (primary != null) {
                    playerGameName = primary.getGameName();
                    playerGameId = primary.getGameId();
                }
            }
        } catch (Exception ignored) {
            /* swallow */ }
        return MatchResultResponse.builder()
                .id(result.getId())
                .registrationId(result.getRegistration().getId())
                .userId(result.getUser().getId())
                .matchId(result.getMatch().getId())
                .playerName(result.getUser().getName())
                .playerGameName(playerGameName)
                .playerGameId(playerGameId)
                .teamName("Team " + result.getRegistration().getSlotNumber())
                .position(result.getPosition())
                .kills(result.getKills())
                .prizeAmount(result.getPrizeAmount())
                .prizeCredited(result.getPrizeCredited())
                .createdAt(result.getCreatedAt())
                .updatedAt(result.getUpdatedAt())
                .build();
    }
}
