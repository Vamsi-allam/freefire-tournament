package com.example.demo.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.demo.dto.RegistrationRequest;
import com.example.demo.dto.RegistrationResponse;
import com.example.demo.entity.Match;
import com.example.demo.entity.MatchType;
import com.example.demo.entity.MatchStatus;
import com.example.demo.entity.PaymentStatus;
import com.example.demo.entity.PlayerRole;
import com.example.demo.entity.Registration;
import com.example.demo.entity.RegistrationPlayer;
import com.example.demo.entity.RegistrationStatus;
import com.example.demo.entity.User;
import com.example.demo.repository.MatchRepository;
import com.example.demo.repository.MatchResultRepository;
import com.example.demo.repository.RegistrationPlayerRepository;
import com.example.demo.repository.RegistrationRepository;
import com.example.demo.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RegistrationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

    private final RegistrationRepository registrationRepository;
    private final RegistrationPlayerRepository playerRepository;
    private final MatchRepository matchRepository;
    private final UserRepository userRepository;
    private final WalletService walletService;
    private final Random random = new Random();
    private final MatchResultRepository matchResultRepository;

    @Transactional
    public RegistrationResponse registerForMatch(Long userId, RegistrationRequest request) {
        // Validate user
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Validate match
        Match match = matchRepository.findById(request.getMatchId())
                .orElseThrow(() -> new RuntimeException("Match not found"));

        // Check if user already registered
        if (registrationRepository.existsByUserIdAndMatchIdAndStatus(userId, request.getMatchId(), RegistrationStatus.CONFIRMED)) {
            throw new RuntimeException("Already registered for this match");
        }

        // Block registration if match is not OPEN
        if (match.getStatus() != MatchStatus.OPEN) {
            throw new RuntimeException("Registration is closed for this match");
        }

        // Enforce server-side registration cutoff: disallow new registrations within 7 minutes of start
        java.time.LocalDateTime scheduledAt = match.getScheduledAt();
        if (scheduledAt != null) {
            long minutesUntil = java.time.Duration.between(java.time.LocalDateTime.now(), scheduledAt).toMinutes();
            if (minutesUntil <= 7) {
                throw new RuntimeException("Registration is closed. Please register at least 7 minutes before the match starts.");
            }
        }

        // Check if match is full
        int confirmedRegistrations = registrationRepository.countConfirmedRegistrationsByMatchId(request.getMatchId());
        if (confirmedRegistrations >= match.getSlots()) {
            throw new RuntimeException("Match is full");
        }

        // Check wallet balance and deduct entry fee
        BigDecimal entryFeeDecimal = BigDecimal.valueOf(match.getEntryFee());
        if (walletService.hasInsufficientBalance(userId, entryFeeDecimal)) {
            BigDecimal currentBalance = walletService.getCurrentBalance(userId);
            BigDecimal requiredAmount = walletService.getRequiredAmountToAdd(userId, entryFeeDecimal);

            throw new RuntimeException(String.format(
                    "Insufficient wallet balance to register for this match. "
                    + "Entry fee: ₹%.2f | Your current balance: ₹%.2f | "
                    + "You need to add ₹%.2f more to register for this match",
                    (double) match.getEntryFee(), currentBalance.doubleValue(), requiredAmount.doubleValue()
            ));
        }

        // Deduct entry fee from wallet
        walletService.deductForTournament(userId, entryFeeDecimal, "Tournament Registration - " + match.getTitle());

        try {
            // Validate player count based on match type
            validatePlayerCount(match.getMatchType(), request.getPlayers());

            // Allocate random slot
            int slotNumber = allocateRandomSlot(request.getMatchId(), match.getSlots());

            // Create registration
            Registration registration = Registration.builder()
                    .user(user)
                    .match(match)
                    .status(RegistrationStatus.CONFIRMED)
                    .slotNumber(slotNumber)
                    .amountPaid(match.getEntryFee())
                    .paymentStatus(PaymentStatus.COMPLETED)
                    .transactionId("TXN_" + System.currentTimeMillis())
                    .build();

            registration = registrationRepository.save(registration);

            // Create player records
            List<RegistrationPlayer> players = new ArrayList<>();
            for (int i = 0; i < request.getPlayers().size(); i++) {
                RegistrationRequest.PlayerDetails playerDetail = request.getPlayers().get(i);
                RegistrationPlayer player = RegistrationPlayer.builder()
                        .registration(registration)
                        .playerName(playerDetail.getPlayerName())
                        .gameName(playerDetail.getGameName())
                        .gameId(playerDetail.getGameId())
                        .role(PlayerRole.valueOf(playerDetail.getRole()))
                        .playerPosition(i + 1)
                        .build();
                players.add(player);
            }
            playerRepository.saveAll(players);

            // Update match registered teams count
            match.setRegisteredTeams(confirmedRegistrations + 1);
            matchRepository.save(match);

            return buildRegistrationResponse(registration, players);

        } catch (Exception e) {
            // If registration fails, refund the entry fee
            walletService.refundForTournament(userId, entryFeeDecimal, "Refund for failed registration - " + match.getTitle());
            throw e;
        }
    }

    private void validatePlayerCount(MatchType matchType, List<RegistrationRequest.PlayerDetails> players) {
        int expectedPlayerCount = switch (matchType) {
            case SOLO ->
                1;
            case DUO ->
                2;
            case SQUAD ->
                4;
        };

        if (players.size() != expectedPlayerCount) {
            throw new RuntimeException("Invalid player count. Expected " + expectedPlayerCount + " for " + matchType);
        }
    }

    private int allocateRandomSlot(Long matchId, int totalSlots) {
        List<Integer> allocatedSlots = registrationRepository.findAllocatedSlotsByMatchId(matchId);
        List<Integer> availableSlots = new ArrayList<>();

        for (int i = 1; i <= totalSlots; i++) {
            if (!allocatedSlots.contains(i)) {
                availableSlots.add(i);
            }
        }

        if (availableSlots.isEmpty()) {
            throw new RuntimeException("No slots available");
        }

        return availableSlots.get(random.nextInt(availableSlots.size()));
    }

    @Transactional(readOnly = true)
    public List<RegistrationResponse> getUserRegistrations(Long userId) {
        try {
            log.debug("Fetching registrations for user ID: {}", userId);
            // Use the new method that eagerly fetches the match
            List<Registration> registrations = registrationRepository.findByUserIdWithMatch(userId);
            log.debug("Found {} registrations", registrations.size());

            return registrations.stream()
                    .map(this::convertToResponse)
                    .collect(Collectors.<RegistrationResponse>toList());
        } catch (Exception e) {
            log.error("Error in getUserRegistrations: {}", e.getMessage());
            // Return empty list instead of throwing exception
            return Collections.emptyList();
        }
    }

    @Transactional(readOnly = true)
    private RegistrationResponse convertToResponse(Registration registration) {
        try {
            List<RegistrationPlayer> players = playerRepository.findByRegistrationIdOrderByPlayerPosition(registration.getId());
            return buildRegistrationResponse(registration, players);
        } catch (Exception e) {
            log.error("Error in convertToResponse for registration {}: {}", registration.getId(), e.getMessage());
            // Return a minimal response with basic info
            return RegistrationResponse.builder()
                    .id(registration.getId())
                    .status(registration.getStatus().name())
                    .slotNumber(registration.getSlotNumber())
                    .amountPaid(registration.getAmountPaid())
                    .paymentStatus(registration.getPaymentStatus().name())
                    .registeredAt(registration.getRegisteredAt())
                    .build();
        }
    }

    @Transactional(readOnly = true)
    private RegistrationResponse buildRegistrationResponse(Registration registration, List<RegistrationPlayer> players) {
        List<RegistrationResponse.PlayerInfo> playerInfos = players.stream()
                .map(p -> RegistrationResponse.PlayerInfo.builder()
                .playerName(p.getPlayerName())
                .gameName(p.getGameName())
                .gameId(p.getGameId())
                .role(p.getRole().name())
                .position(p.getPlayerPosition())
                .build())
                .collect(Collectors.toList());

        RegistrationResponse.MatchInfo matchInfo = RegistrationResponse.MatchInfo.builder()
                .id(registration.getMatch().getId())
                .title(registration.getMatch().getTitle())
                .matchType(registration.getMatch().getMatchType().name())
                .status(registration.getMatch().getStatus().name())
                .scheduledAt(registration.getMatch().getScheduledAt())
                .entryFee(registration.getMatch().getEntryFee())
                .prizePool(registration.getMatch().getPrizePool())
                .roomId(registration.getMatch().getRoomId())
                .roomPassword(registration.getMatch().getRoomPassword())
                .build();

        // Attach match result if exists
        RegistrationResponse.ResultInfo resultInfo = matchResultRepository.findByRegistrationId(registration.getId())
                .map(mr -> RegistrationResponse.ResultInfo.builder()
                .position(mr.getPosition())
                .kills(mr.getKills())
                .prize(mr.getPrizeAmount())
                .prizeCredited(mr.getPrizeCredited())
                .updatedAt(mr.getUpdatedAt())
                .build())
                .orElse(null);

        return RegistrationResponse.builder()
                .id(registration.getId())
                .matchId(registration.getMatch().getId())
                .matchTitle(registration.getMatch().getTitle())
                .status(registration.getStatus().name())
                .slotNumber(registration.getSlotNumber())
                .amountPaid(registration.getAmountPaid())
                .paymentStatus(registration.getPaymentStatus().name())
                .registeredAt(registration.getRegisteredAt())
                .players(playerInfos)
                .match(matchInfo)
                .result(resultInfo)
                .build();
    }

    @Transactional(readOnly = true)
    public boolean isUserRegisteredForMatch(Long userId, Long matchId) {
        return registrationRepository.existsByUserIdAndMatchIdAndStatus(userId, matchId, RegistrationStatus.CONFIRMED);
    }

    @Transactional(readOnly = true)
    public List<RegistrationResponse> getMatchRegistrations(Long matchId) {
        try {
            List<Registration> regs = registrationRepository.findByMatchIdAndStatusWithMatch(matchId, RegistrationStatus.CONFIRMED);
            return regs.stream()
                    .map(this::convertToResponse)
                    .collect(Collectors.<RegistrationResponse>toList());
        } catch (Exception e) {
            log.error("Error in getMatchRegistrations: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
