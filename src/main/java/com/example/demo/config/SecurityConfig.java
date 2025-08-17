package com.example.demo.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;

    @Value("${app.cors.allowed-origins:${APP_CORS_ALLOWED_ORIGINS:*}}")
    private String allowedOriginsCsv;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(auth -> auth
                // CORS preflight
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // Public auth endpoints
                .requestMatchers("/auth/**").permitAll()
                // Public read-only matches
                .requestMatchers(HttpMethod.GET, "/api/matches/**").permitAll()
                // Support ticket submission (public contact)
                .requestMatchers(HttpMethod.POST, "/api/support/**").permitAll()
                // Admin-only endpoints
                .requestMatchers("/api/upi/admin/**").hasAuthority("ADMIN")
                .requestMatchers("/api/withdrawals/admin/**").hasAuthority("ADMIN")
                .requestMatchers("/api/match-results/**").hasAuthority("ADMIN")
                // Non-GET on matches require ADMIN (create/update/credentials)
                .requestMatchers(HttpMethod.POST, "/api/matches/**").hasAuthority("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/matches/**").hasAuthority("ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/matches/**").hasAuthority("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/matches/**").hasAuthority("ADMIN")
                // Authenticated user endpoints
                .requestMatchers("/api/wallet/**").authenticated()
                .requestMatchers("/api/registrations/**").authenticated()
                .requestMatchers("/api/profile/**").authenticated()
                .requestMatchers("/api/upi/**").authenticated()
                // Everything else requires authentication
                .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                );
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins;
        if (allowedOriginsCsv != null && !allowedOriginsCsv.isBlank()) {
            origins = Arrays.stream(allowedOriginsCsv.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        } else {
            origins = List.of("*");
        }
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Accept", "Origin", "X-Requested-With"));
        configuration.setExposedHeaders(Arrays.asList("Authorization"));
        // Allow credentials only if not using wildcard
        configuration.setAllowCredentials(!origins.contains("*"));
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
