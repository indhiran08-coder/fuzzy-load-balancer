package com.fuzzybalancer.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * LoginRequest — DTO for the POST /api/auth/login endpoint.
 *
 * Accepts either username or email for flexible login.
 * The service layer determines which field was provided.
 *
 * Kept simple intentionally — no complex validation here
 * because invalid credentials are caught by Spring Security's
 * AuthenticationManager, which throws AuthenticationException.
 */
@Data
public class LoginRequest {

    /**
     * usernameOrEmail — Allows users to log in with either their username
     * or email address. The AuthService will attempt username lookup first,
     * then fall back to email lookup.
     */
    @NotBlank(message = "Username or email is required")
    private String usernameOrEmail;

    @NotBlank(message = "Password is required")
    private String password;
}
