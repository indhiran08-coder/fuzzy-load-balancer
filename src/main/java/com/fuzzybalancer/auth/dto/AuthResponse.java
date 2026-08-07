package com.fuzzybalancer.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * AuthResponse — DTO returned by both /register and /login endpoints.
 *
 * Contains:
 *   - The JWT access token (client stores this in localStorage/sessionStorage)
 *   - Token type (always "Bearer" per RFC 6750)
 *   - Token expiration time (ms from now) so client can schedule refresh
 *   - Basic user info (no sensitive data like password hash)
 *   - User's roles for client-side UI rendering (show/hide admin features)
 *
 * Security note: We never return the password or password hash in any response.
 * Roles are included for UI purposes, but authorization is always re-validated
 * server-side on every request via JWT claims.
 *
 * @Builder — Enables fluent construction:
 *   AuthResponse.builder()
 *       .accessToken(token)
 *       .username(user.getUsername())
 *       .build();
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    /**
     * accessToken — The JWT token the client must include in the
     * Authorization header of every subsequent request:
     *   Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
     */
    private String accessToken;

    /**
     * tokenType — Always "Bearer" per the OAuth 2.0 Bearer Token standard.
     * Tells the client how to format the Authorization header.
     */
    @Builder.Default
    private String tokenType = "Bearer";

    /**
     * expiresIn — Token lifetime in milliseconds.
     * Client can use this to schedule a re-login before expiry,
     * avoiding 401 errors during active user sessions.
     */
    private Long expiresIn;

    private Long userId;
    private String username;
    private String email;
    private Set<String> roles;

    /** Timestamp of authentication (ISO-8601 string). */
    private String authenticatedAt;

    /** Human-readable welcome message. */
    private String message;
}
