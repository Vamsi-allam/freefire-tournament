package com.example.demo.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
public class MatchSchedulingService {

    private final MatchRepository matchRepository;
    private final RegistrationRepository registrationRepository;
    private final WalletService walletService;

    // Runs every minute
    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void enforceMinimumsAndRefunds() {
        LocalDateTime now = LocalDateTime.now();
        List<Match> upcoming = matchRepository.findAll();
        for (Match match : upcoming) {
            if (match.getStatus() == MatchStatus.CANCELLED || match.getStatus() == MatchStatus.COMPLETED) {
                continue;
            }

            long minutesUntil = Duration.between(now, match.getScheduledAt()).toMinutes();
            if (minutesUntil > 5) {
                continue; // Only act within 5 minutes window

            }
            if (minutesUntil < -10) {
                continue; // Skip long past matches
            }
            // Count confirmed registrations
            int confirmed = registrationRepository.countConfirmedRegistrationsByMatchId(match.getId());
            int required = requiredTeams(match.getMatchType());

            if (confirmed < required) {
                // Cancel and refund
                match.setStatus(MatchStatus.CANCELLED);
                matchRepository.save(match);

                List<Registration> regs = registrationRepository.findByMatchIdAndStatus(match.getId(), RegistrationStatus.CONFIRMED);
                for (Registration reg : regs) {
                    try {
                        BigDecimal amount = BigDecimal.valueOf(reg.getAmountPaid());
                        if (amount.compareTo(BigDecimal.ZERO) > 0 && reg.getPaymentStatus() != PaymentStatus.REFUNDED) {
                            walletService.refundForTournament(
                                    reg.getUser().getId(),
                                    amount,
                                    "Refund: Match cancelled due to low registrations - " + match.getTitle()
                            );
                            reg.setPaymentStatus(PaymentStatus.REFUNDED);
                        }
                        reg.setStatus(RegistrationStatus.CANCELLED);
                        registrationRepository.save(reg);
                    } catch (Exception ex) {
                        // log and continue
                        System.err.println("Refund failed for registration " + reg.getId() + ": " + ex.getMessage());
                    }
                }
            }
        }
    }

    // Minimum confirmed registrations required for the match to proceed
    // Keep in sync with MatchController.requiredTeams
    private int requiredTeams(MatchType type) {
        return switch (type) {
            case SOLO ->
                25;   // minimum solo players
            case DUO ->
                13;    // minimum duo teams
            case SQUAD ->
                7;   // minimum squad teams
        };
    }
}
