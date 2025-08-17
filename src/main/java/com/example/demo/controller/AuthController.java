package com.example.demo.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.dto.RegisterRequest;
import com.example.demo.entity.Role;
import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.JwtService;
import com.example.demo.service.WalletService;

import lombok.RequiredArgsConstructor;

@RestController
@CrossOrigin("*")
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final WalletService walletService;
    private final JwtService jwtService;

    // Comma-separated list of admin emails (configure via application.properties or env ADMIN_EMAILS)
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        try {
            if (userRepository.existsByEmail(request.getEmail())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Email ID already exists!"));
            }
            if (userRepository.existsByPhonenumber(request.getPhonenumber())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Phone number already exists!"));
            }
            User user = User.builder()
                    .name(request.getName())
                    .email(request.getEmail())
                    .phonenumber(request.getPhonenumber())
                    .role(request.getRole())
                    .build();
            User savedUser = userRepository.save(user);

            // Create wallet for the new user
            walletService.createWalletForUser(savedUser);

            return ResponseEntity.ok(Map.of("message", "User registered successfully!"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "An error occurred during registration"));
        }
    }

    @PostMapping("/google-login")
    public ResponseEntity<?> googleLogin(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");
            String name = request.get("name");
            String avatar = request.get("avatar");

            if (email == null || email.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Email is required"));
            }

            // Check if user already exists
            User user = userRepository.findByEmail(email).orElse(null);

            if (user == null) {
                // Create new user for Google OAuth
                user = User.builder()
                        .name(name != null ? name : email.split("@")[0])
                        .email(email)
                        .phonenumber(null) // Will be updated later if needed
                        .role(Role.USER) // Default role; may promote to ADMIN based on configured emails
                        .build();

                User savedUser = userRepository.save(user);

                // Create wallet for the new user
                walletService.createWalletForUser(savedUser);

                user = savedUser;
            }

            // Promote/demote user role based on configured admin emails (idempotent)
            

            // Generate JWT token for the user
            String token = jwtService.generateToken(user.getEmail());

            return ResponseEntity.ok(Map.of(
                    "token", token,
                    "name", user.getName(),
                    "email", user.getEmail(),
                    "phone", user.getPhonenumber() != null ? user.getPhonenumber() : "",
                    "gameId", user.getGameId() != null ? user.getGameId() : "",
                    "role", user.getRole().toString(),
                    "avatar", avatar != null ? avatar : "",
                    "message", "Login successful"
            ));

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "An error occurred during Google login: " + e.getMessage()));
        }
    }

    // Note: Password-based login is removed as we're using Google OAuth
    
}