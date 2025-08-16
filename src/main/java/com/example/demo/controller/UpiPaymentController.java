package com.example.demo.controller;

import com.example.demo.dto.UpiAdminActionRequest;
import com.example.demo.dto.UpiPaymentRequest;
import com.example.demo.dto.UpiPaymentResponse;
import com.example.demo.dto.UpiUtrSubmissionRequest;
import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.UpiPaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/upi")
@RequiredArgsConstructor
public class UpiPaymentController {

    private final UpiPaymentService upiPaymentService;
    private final UserRepository userRepository;

    @PostMapping("/initiate")
    public ResponseEntity<?> initiate(@RequestBody UpiPaymentRequest request, Authentication auth) {
        try {
            if (auth == null || auth.getPrincipal() == null) {
                return ResponseEntity.status(401).body("Unauthorized");
            }
            UserDetails ud = (UserDetails) auth.getPrincipal();
            User user = userRepository.findByEmail(ud.getUsername()).orElseThrow();
            UpiPaymentResponse resp = upiPaymentService.initiatePayment(user.getId(), request);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/submit-utr")
    public ResponseEntity<?> submitUtr(@RequestBody UpiUtrSubmissionRequest request, Authentication auth) {
        try {
            if (auth == null || auth.getPrincipal() == null) {
                return ResponseEntity.status(401).body("Unauthorized");
            }
            UserDetails ud = (UserDetails) auth.getPrincipal();
            User user = userRepository.findByEmail(ud.getUsername()).orElseThrow();
            UpiPaymentResponse resp = upiPaymentService.submitUtr(user.getId(), request);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/my")
    public ResponseEntity<?> myPayments(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        UserDetails ud = (UserDetails) auth.getPrincipal();
        User user = userRepository.findByEmail(ud.getUsername()).orElseThrow();
        return ResponseEntity.ok(upiPaymentService.listMy(user.getId()));
    }

    @GetMapping("/admin/pending")
    public ResponseEntity<List<UpiPaymentResponse>> pending(Authentication auth) {
        // In real app restrict to ADMIN via Spring Security
        List<UpiPaymentResponse> list = upiPaymentService.listPending();
        return ResponseEntity.ok(list);
    }

    @PostMapping("/admin/action")
    public ResponseEntity<?> action(@RequestBody UpiAdminActionRequest request, Authentication auth) {
        try {
            String msg = upiPaymentService.adminAction(request);
            return ResponseEntity.ok(java.util.Map.of("message", msg));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", e.getMessage()));
        }
    }
}
