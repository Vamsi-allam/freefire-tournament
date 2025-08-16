package com.example.demo.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.dto.AddMoneyRequest;
import com.example.demo.dto.WalletResponse;
import com.example.demo.dto.WalletTransactionResponse;
import com.example.demo.dto.WithdrawMoneyRequest;
import com.example.demo.dto.WithdrawalOtpRequest;
import com.example.demo.dto.WithdrawalOtpResponse;
import com.example.demo.dto.WithdrawalRequestResponse;
import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.EmailService;
import com.example.demo.service.WalletService;

import lombok.RequiredArgsConstructor;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @GetMapping("/test")
    public ResponseEntity<String> testEndpoint() {
        return ResponseEntity.ok("Wallet endpoint is working!");
    }

    @PostMapping("/test-email")
    public ResponseEntity<?> testEmail(Authentication authentication) {
        try {
            if (authentication == null || authentication.getPrincipal() == null) {
                return ResponseEntity.status(401).body("Unauthorized");
            }

            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            User user = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // Send test email
            emailService.sendWithdrawalOtp(user.getEmail(), "123456", user.getName(), "100.00");

            return ResponseEntity.ok(java.util.Map.of(
                    "message", "Test email sent successfully",
                    "email", user.getEmail()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(java.util.Map.of(
                    "error", "Failed to send test email: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/balance")
    public ResponseEntity<WalletResponse> getWalletBalance(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return ResponseEntity.status(401).body(null);
        }

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        WalletResponse wallet = walletService.getWalletBalance(user.getId());
        return ResponseEntity.ok(wallet);
    }

    @PostMapping("/add")
    public ResponseEntity<WalletTransactionResponse> addMoney(
            @RequestBody AddMoneyRequest request,
            Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return ResponseEntity.status(401).body(null);
        }

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        WalletTransactionResponse transaction = walletService.addMoney(user.getId(), request);
        return ResponseEntity.ok(transaction);
    }

    @PostMapping("/withdraw")
    public ResponseEntity<WalletTransactionResponse> withdrawMoney(
            @RequestBody WithdrawMoneyRequest request,
            Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return ResponseEntity.status(401).body(null);
        }

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        WalletTransactionResponse transaction = walletService.withdrawMoney(user.getId(), request);
        return ResponseEntity.ok(transaction);
    }

    @PostMapping("/withdraw/initiate")
    public ResponseEntity<?> initiateWithdrawal(
            @RequestBody WithdrawMoneyRequest request,
            Authentication authentication) {
        try {
            if (authentication == null || authentication.getPrincipal() == null) {
                return ResponseEntity.status(401).body("Unauthorized");
            }

            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            User user = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            WithdrawalOtpResponse response = walletService.initiateWithdrawal(user.getId(), request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    java.util.Map.of("error", e.getMessage())
            );
        }
    }

    @PostMapping("/withdraw/verify")
    public ResponseEntity<?> verifyWithdrawalOtp(
            @RequestBody WithdrawalOtpRequest request,
            Authentication authentication) {
        try {
            if (authentication == null || authentication.getPrincipal() == null) {
                return ResponseEntity.status(401).body("Unauthorized");
            }

            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            User user = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            WalletTransactionResponse transaction = walletService.completeWithdrawal(user.getId(), request.getOtpCode());
            return ResponseEntity.ok(transaction);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    java.util.Map.of("error", e.getMessage())
            );
        }
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<WalletTransactionResponse>> getTransactionHistory(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return ResponseEntity.status(401).body(null);
        }

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<WalletTransactionResponse> transactions = walletService.getTransactionHistory(user.getId());
        return ResponseEntity.ok(transactions);
    }

    @GetMapping("/withdrawals")
    public ResponseEntity<List<WithdrawalRequestResponse>> listMyWithdrawals(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return ResponseEntity.status(401).body(null);
        }

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        return ResponseEntity.ok(walletService.listMyWithdrawals(user.getId()));
    }
}
