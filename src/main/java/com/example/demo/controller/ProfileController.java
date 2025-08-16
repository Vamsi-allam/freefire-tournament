package com.example.demo.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@RestController
@CrossOrigin("*")
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final UserRepository userRepository;

    @PostMapping("/complete")
    public ResponseEntity<?> completeProfile(
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            String userEmail = userDetails.getUsername();
            User user = userRepository.findByEmail(userEmail)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // Update user profile with the provided information
            String displayName = request.get("displayName");
            String phoneNumber = request.get("phoneNumber");
            String gameId = request.get("gameId");

            if (displayName != null && !displayName.trim().isEmpty()) {
                user.setName(displayName.trim());
            }

            if (phoneNumber != null && !phoneNumber.trim().isEmpty()) {
                user.setPhonenumber(phoneNumber.trim());
            }

            if (gameId != null && !gameId.trim().isEmpty()) {
                user.setGameId(gameId.trim());
            }

            User savedUser = userRepository.save(user);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Profile updated successfully",
                    "user", Map.of(
                            "id", savedUser.getId(),
                            "name", savedUser.getName(),
                            "email", savedUser.getEmail(),
                            "phone", savedUser.getPhonenumber() != null ? savedUser.getPhonenumber() : "",
                            "gameId", savedUser.getGameId() != null ? savedUser.getGameId() : "",
                            "role", savedUser.getRole().name()
                    )
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Failed to update profile: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/check-completion")
    public ResponseEntity<?> checkProfileCompletion(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            String userEmail = userDetails.getUsername();
            User user = userRepository.findByEmail(userEmail)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            boolean isComplete = user.getName() != null && !user.getName().trim().isEmpty()
                    && user.getPhonenumber() != null && !user.getPhonenumber().trim().isEmpty()
                    && user.getGameId() != null && !user.getGameId().trim().isEmpty();

            return ResponseEntity.ok(Map.of(
                    "isComplete", isComplete,
                    "user", Map.of(
                            "id", user.getId(),
                            "name", user.getName() != null ? user.getName() : "",
                            "email", user.getEmail(),
                            "phone", user.getPhonenumber() != null ? user.getPhonenumber() : "",
                            "gameId", user.getGameId() != null ? user.getGameId() : "",
                            "role", user.getRole().name()
                    )
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Failed to check profile: " + e.getMessage()
            ));
        }
    }
}
