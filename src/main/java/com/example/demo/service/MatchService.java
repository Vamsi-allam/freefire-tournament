package com.example.demo.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.dto.MatchCreateRequest;
import com.example.demo.entity.Match;
import com.example.demo.entity.MatchStatus;
import com.example.demo.entity.MatchType;
import com.example.demo.entity.PaymentStatus;
import com.example.demo.entity.Registration;
import com.example.demo.entity.RegistrationStatus;
import com.example.demo.repository.MatchRepository;
import com.example.demo.repository.RegistrationRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MatchService {

    private final MatchRepository matchRepository;
    private final RegistrationRepository registrationRepository;
    private final WalletService walletService;
    private static final Logger log = LoggerFactory.getLogger(MatchService.class);

    public Match create(MatchCreateRequest req) {
        MatchType type = deriveType(req.getTitle(), req.getMatchType());
        int slots = switch (type) {
            case SOLO ->
                48;
            case DUO ->
                24;
            case SQUAD ->
                12;
        };
        int entryFee = req.getEntryFee() != null ? req.getEntryFee().intValue() : 0;
        int totalPool = entryFee * slots;
        int prize1 = (int) Math.round(totalPool * 0.40);
        int prize2 = (int) Math.round(totalPool * 0.30);
        int prize3 = (int) Math.round(totalPool * 0.20);
        LocalDateTime when;
        try {
            when = LocalDateTime.parse(req.getScheduleDateTime());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid scheduleDateTime ISO format");
        }

        Match match = Match.builder()
                .title(req.getTitle())
                .game(req.getGame())
                .matchType(type)
                .status(MatchStatus.OPEN)
                .slots(slots)
                .entryFee(entryFee)
                .prizePool(totalPool)
                .prizeFirst(prize1)
                .prizeSecond(prize2)
                .prizeThird(prize3)
                .scheduledAt(when)
                .mapName(req.getMapName())
                .gameMode(req.getGameMode())
                .rules(req.getRules())
                .build();
        return matchRepository.save(match);
    }

    private MatchType deriveType(String title, String provided) {
        if (provided != null && !provided.isBlank()) {
            return MatchType.valueOf(provided.toUpperCase());
        }
        if (title == null) {
            return MatchType.SOLO; // default

        }
        String lower = title.toLowerCase();
        if (lower.contains("duo")) {
            return MatchType.DUO;
        }
        if (lower.contains("squad")) {
            return MatchType.SQUAD;
        }
        return MatchType.SOLO;
    }

    public List<Match> listAll() {
        log.debug("listAll invoked");
        List<Match> matches = matchRepository.findAll();
        log.debug("fetched matches count={}", matches.size());
        updateRegisteredCountsInMemory(matches);
        return matches;
    }

    public List<Match> upcoming() {
        log.debug("upcoming invoked");
        List<Match> matches = matchRepository.findByScheduledAtAfterOrderByScheduledAtAsc(LocalDateTime.now());
        log.debug("upcoming matches count={}", matches.size());
        updateRegisteredCountsInMemory(matches);
        return matches;
    }

    private void updateRegisteredCountsInMemory(List<Match> matches) {
        try {
            if (matches == null || matches.isEmpty()) {
                return;
            }
            List<Long> ids = matches.stream()
                    .map(Match::getId)
                    .filter(java.util.Objects::nonNull)
                    .toList();
            if (ids.isEmpty()) {
                return;
            }
            var rows = registrationRepository.countConfirmedByMatchIds(ids);
            java.util.Map<Long, Integer> counts = new java.util.HashMap<>();
            for (Object[] row : rows) {
                Long matchId = (Long) row[0];
                Number cnt = (Number) row[1];
                counts.put(matchId, cnt != null ? cnt.intValue() : 0);
            }
            for (Match m : matches) {
                Integer c = counts.get(m.getId());
                if (c != null) {
                    m.setRegisteredTeams(c);
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to batch update registered counts: {}", ex.getMessage());
        }
    }

    @Transactional
    public Match updateMatch(Long id, Match updatedMatch) {
        Match existingMatch = matchRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Match not found with id: " + id));

        MatchStatus previousStatus = existingMatch.getStatus();

        // Update fields
        existingMatch.setTitle(updatedMatch.getTitle());
        existingMatch.setGame(updatedMatch.getGame());
        existingMatch.setMatchType(updatedMatch.getMatchType());
        existingMatch.setStatus(updatedMatch.getStatus());
        existingMatch.setSlots(updatedMatch.getSlots());
        existingMatch.setEntryFee(updatedMatch.getEntryFee());
        existingMatch.setPrizePool(updatedMatch.getPrizePool());
        existingMatch.setScheduledAt(updatedMatch.getScheduledAt());
        existingMatch.setMapName(updatedMatch.getMapName());
        existingMatch.setGameMode(updatedMatch.getGameMode());
        existingMatch.setRules(updatedMatch.getRules());
        existingMatch.setRoomId(updatedMatch.getRoomId());
        existingMatch.setRoomPassword(updatedMatch.getRoomPassword());

        Match saved = matchRepository.save(existingMatch);

        // If admin changed status to CANCELLED, trigger refunds similar to scheduler
        if (previousStatus != MatchStatus.CANCELLED && saved.getStatus() == MatchStatus.CANCELLED) {
            try {
                List<Registration> regs = registrationRepository.findByMatchIdAndStatus(saved.getId(), RegistrationStatus.CONFIRMED);
                for (Registration reg : regs) {
                    try {
                        BigDecimal amount = BigDecimal.valueOf(reg.getAmountPaid());
                        if (amount.compareTo(BigDecimal.ZERO) > 0 && reg.getPaymentStatus() != PaymentStatus.REFUNDED) {
                            walletService.refundForTournament(
                                    reg.getUser().getId(),
                                    amount,
                                    "Refund: Match cancelled by admin - " + saved.getTitle()
                            );
                            reg.setPaymentStatus(PaymentStatus.REFUNDED);
                        }
                        reg.setStatus(RegistrationStatus.CANCELLED);
                        registrationRepository.save(reg);
                    } catch (Exception ex) {
                        log.error("Refund failed for registration {}: {}", reg.getId(), ex.getMessage(), ex);
                    }
                }
            } catch (Exception e) {
                log.error("Failed processing refunds on admin cancellation for match id={}: {}", saved.getId(), e.getMessage(), e);
            }
        }

        return saved;
    }

    public Match saveCredentials(Long id, String roomId, String roomPassword) {
        Match match = matchRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Match not found with id: " + id));

        match.setRoomId(roomId);
        match.setRoomPassword(roomPassword);
        // Don't set credentialsSent to true here - only save to database

        return matchRepository.save(match);
    }

    public void sendCredentialsToPlayers(Long id) {
        Match match = matchRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Match not found with id: " + id));

        if (match.getRoomId() == null || match.getRoomPassword() == null) {
            throw new IllegalArgumentException("Room credentials not set for this match");
        }

        // TODO: Implement actual sending logic (email, SMS, notification service)
        // For now, just mark as sent
        match.setCredentialsSent(true);
        matchRepository.save(match);

        System.out.println("Sending credentials to registered players for match: " + match.getTitle());
        System.out.println("Room ID: " + match.getRoomId());
        System.out.println("Room Password: " + match.getRoomPassword());
    }
}
