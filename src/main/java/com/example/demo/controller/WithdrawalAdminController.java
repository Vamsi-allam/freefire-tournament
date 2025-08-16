package com.example.demo.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.dto.WithdrawalAdminActionRequest;
import com.example.demo.dto.WithdrawalRequestResponse;
import com.example.demo.service.WalletService;

import lombok.RequiredArgsConstructor;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/withdrawals/admin")
@RequiredArgsConstructor
public class WithdrawalAdminController {

    private final WalletService walletService;

    @GetMapping("/pending")
    public ResponseEntity<List<WithdrawalRequestResponse>> listPending() {
        return ResponseEntity.ok(walletService.listPendingWithdrawals());
    }

    @PostMapping("/action")
    public ResponseEntity<?> act(@RequestBody WithdrawalAdminActionRequest request) {
        try {
            String action = request.getAction() == null ? "" : request.getAction().toUpperCase();
            if ("APPROVE".equals(action)) {
                String msg = walletService.adminApproveWithdrawal(request.getRequestId(), request.getNotes());
                return ResponseEntity.ok(java.util.Map.of("message", msg));
            } else if ("REJECT".equals(action)) {
                String msg = walletService.adminRejectWithdrawal(request.getRequestId(), request.getNotes());
                return ResponseEntity.ok(java.util.Map.of("message", msg));
            } else {
                return ResponseEntity.badRequest().body(java.util.Map.of("error", "Unknown action"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", e.getMessage()));
        }
    }
}
