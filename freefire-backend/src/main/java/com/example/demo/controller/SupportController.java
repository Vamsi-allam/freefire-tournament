package com.example.demo.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.service.EmailService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/support")
@RequiredArgsConstructor
@Slf4j
public class SupportController {

    private final EmailService emailService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> submit(@RequestBody SupportRequest req) {
        String email = req.getEmail();
        String phone = req.getPhone();
        String message = req.getMessage();

        log.info("/api/support received: email={}, phone={}, msgLen={}",
                email, phone, message != null ? message.length() : 0);

        try {
            emailService.notifyAdminsSupport(email, phone, message, java.util.Collections.emptyList());
        } catch (Exception e) {
            log.warn("Failed to trigger support notification: {}", e.getMessage(), e);
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("message", "Support request submitted");
        return ResponseEntity.ok(resp);
    }

    @lombok.Data
    public static class SupportRequest {

        private String email;
        private String phone;
        private String message;
    }
}
