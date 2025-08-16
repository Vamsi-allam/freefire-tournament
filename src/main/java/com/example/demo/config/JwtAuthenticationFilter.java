package com.example.demo.config;

import java.io.IOException;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.demo.service.JwtService;
import com.example.demo.service.Userservice;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final Userservice userService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        final String requestURI = request.getRequestURI();

        System.out.println("DEBUG JWT Filter - Request URI: " + requestURI);
        System.out.println("DEBUG JWT Filter - Auth Header: " + (authHeader != null ? "Present" : "Missing"));

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            System.out.println("DEBUG JWT Filter - No Bearer token found in header");
            filterChain.doFilter(request, response);
            return;
        }

        try {
            final String jwt = authHeader.substring(7);
            final String userEmail = jwtService.extractemail(jwt);

            System.out.println("DEBUG JWT Filter - Extracted email: " + (userEmail != null ? userEmail : "null"));

            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userService.loadUserByUsername(userEmail);

                System.out.println("DEBUG JWT Filter - User found: " + (userDetails != null ? "Yes" : "No"));

                if (jwtService.isTokenValid(jwt, userDetails)) {
                    System.out.println("DEBUG JWT Filter - Token is valid");
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    System.out.println("DEBUG JWT Filter - Authentication set in context");
                } else {
                    System.out.println("DEBUG JWT Filter - Token is NOT valid");
                }
            }
        } catch (Exception e) {
            logger.error("Could not set user authentication: {}", e);
            System.out.println("DEBUG JWT Filter - Error: " + e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
