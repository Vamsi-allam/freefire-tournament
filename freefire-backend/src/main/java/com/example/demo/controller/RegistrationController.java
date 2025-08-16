package com.example.demo.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.dto.RegistrationRequest;
import com.example.demo.dto.RegistrationResponse;
import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.RegistrationService;

import lombok.RequiredArgsConstructor;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/registrations")
@RequiredArgsConstructor
public class RegistrationController {

    private final RegistrationService registrationService;
    private final UserRepository userRepository;

    @PostMapping
    public ResponseEntity<RegistrationResponse> registerForMatch(
            @RequestBody RegistrationRequest request,
            Authentication authentication) {

        if (authentication == null || authentication.getPrincipal() == null) {
            return ResponseEntity.status(401).body(null);
        }

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        RegistrationResponse response = registrationService.registerForMatch(user.getId(), request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my-registrations")
    public ResponseEntity<List<RegistrationResponse>> getMyRegistrations(Authentication authentication) {
        System.out.println("DEBUG: /my-registrations endpoint called");

        if (authentication == null) {
            System.out.println("DEBUG: authentication is null");
            return ResponseEntity.status(403).body(null); // Return 403 instead of 401 for better debugging
        }

        if (authentication.getPrincipal() == null) {
            System.out.println("DEBUG: authentication.getPrincipal() is null");
            return ResponseEntity.status(403).body(null);
        }

        System.out.println("DEBUG: Authentication is valid: " + authentication.getName());
        System.out.println("DEBUG: Authentication principal type: " + authentication.getPrincipal().getClass().getName());

        try {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            System.out.println("DEBUG: UserDetails username: " + userDetails.getUsername());

            User user = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            System.out.println("DEBUG: Found user with ID: " + user.getId());

            List<RegistrationResponse> registrations = registrationService.getUserRegistrations(user.getId());
            System.out.println("DEBUG: Found " + registrations.size() + " registrations for user");

            return ResponseEntity.ok(registrations);
        } catch (Exception e) {
            System.out.println("DEBUG: Exception in getMyRegistrations: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body(null);
        }
    }

    @GetMapping("/check/{matchId}")
    public ResponseEntity<Boolean> checkRegistration(
            @PathVariable Long matchId,
            Authentication authentication) {

        if (authentication == null || authentication.getPrincipal() == null) {
            return ResponseEntity.status(401).body(null);
        }

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        boolean isRegistered = registrationService.isUserRegisteredForMatch(user.getId(), matchId);
        return ResponseEntity.ok(isRegistered);
    }

    @GetMapping("/match/{matchId}")
    public ResponseEntity<List<RegistrationResponse>> getRegistrationsForMatch(@PathVariable Long matchId, Authentication authentication) {
        // Allow both authenticated users and admins to view registered players; require auth
        if (authentication == null || authentication.getPrincipal() == null) {
            return ResponseEntity.status(401).body(null);
        }
        List<RegistrationResponse> regs = registrationService.getMatchRegistrations(matchId);
        return ResponseEntity.ok(regs);
    }

    // Simple test endpoint
    @GetMapping("/test")
    public ResponseEntity<String> test() {
        return ResponseEntity.ok("Registration controller is working!");
    }

    // Auth test endpoint to check if authentication is working
    @GetMapping("/auth-test")
    public ResponseEntity<?> authTest(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.ok(Map.of(
                    "authenticated", false,
                    "message", "No authentication present"
            ));
        }

        return ResponseEntity.ok(Map.of(
                "authenticated", true,
                "principal", authentication.getPrincipal() != null ? authentication.getPrincipal().toString() : "null",
                "name", authentication.getName(),
                "authorities", authentication.getAuthorities().toString()
        ));
    }

    // Test endpoint to test insufficient balance without authentication
    @PostMapping("/test-insufficient-balance")
    public ResponseEntity<?> testInsufficientBalance(@RequestBody RegistrationRequest request) {
        try {
            // Use a test user ID (assuming user with ID 1 exists and has no balance)
            Long testUserId = 1L;
            RegistrationResponse response = registrationService.registerForMatch(testUserId, request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            // Return the error message which should contain our improved insufficient balance message
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
