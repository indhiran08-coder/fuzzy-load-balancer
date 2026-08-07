package com.fuzzybalancer.auth.service;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * JwtService — Core JWT utility service.
 *
 * Responsibilities:
 *   1. Generate JWT tokens from UserDetails
 *   2. Extract claims (subject, expiration, custom claims) from tokens
 *   3. Validate tokens (signature + expiration)
 *
 * JWT Structure:
 *   header.payload.signature
 *   - Header: algorithm (HS256) and token type (JWT)
 *   - Payload: claims (username, roles, issuedAt, expiration)
 *   - Signature: HMAC-SHA256(base64(header) + "." + base64(payload), secret)
 *
 * Why HS256 (HMAC-SHA256)?
 *   - Symmetric: same key signs and verifies. Simpler for single-service apps.
 *   - Alternatives: RS256 (asymmetric RSA) is better for microservices where
 *     different services need to verify tokens without the signing key.
 *     For this project, HS256 is appropriate.
 *
 * @Slf4j — Lombok: injects a Logger (SLF4J) as private static final log field.
 */
@Service
@Slf4j
public class JwtService {

    /**
     * @Value — Injects values from application.properties.
     * The ${...} syntax reads the property at bean creation time.
     * If the property is missing, Spring throws a startup error.
     */
    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration}")
    private Long jwtExpiration;

    // =========================================================================
    // TOKEN GENERATION
    // =========================================================================

    /**
     * generateToken() — Creates a JWT for the given UserDetails.
     *
     * Delegates to the overloaded version with an empty extra claims map.
     * This is the method called during login.
     *
     * @param userDetails The authenticated user
     * @return Signed JWT token string
     */
    public String generateToken(UserDetails userDetails) {
        return generateToken(new HashMap<>(), userDetails);
    }

    /**
     * generateToken() — Creates a JWT with additional custom claims.
     *
     * Custom claims are embedded in the JWT payload and can be read
     * without hitting the database. We include:
     *   - "roles": user's roles (for client-side routing)
     *
     * @param extraClaims Additional claims to embed in the JWT
     * @param userDetails The authenticated user
     * @return Signed JWT token string
     */
    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        // Add roles as a JWT claim for client-side use
        extraClaims.put("roles", userDetails.getAuthorities().stream()
            .map(Object::toString)
            .toList());

        return Jwts.builder()
            .claims(extraClaims)         // Custom claims (roles, etc.)
            .subject(userDetails.getUsername())  // Standard "sub" claim = username
            .issuedAt(new Date(System.currentTimeMillis()))  // "iat" claim
            .expiration(new Date(System.currentTimeMillis() + jwtExpiration))  // "exp" claim
            .signWith(getSigningKey())    // Sign with HMAC-SHA256 key
            .compact();                  // Build the final token string
    }

    // =========================================================================
    // TOKEN VALIDATION
    // =========================================================================

    /**
     * isTokenValid() — Validates that:
     *   1. The token's subject matches the expected username
     *   2. The token is not expired
     *
     * Called by JwtAuthFilter on every protected request.
     *
     * @param token       The JWT from the Authorization header
     * @param userDetails The user loaded from the database
     * @return true if the token is valid for this user
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    /**
     * isTokenExpired() — Checks the "exp" claim against the current time.
     *
     * @param token JWT token string
     * @return true if the token has expired
     */
    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    // =========================================================================
    // CLAIM EXTRACTION
    // =========================================================================

    /**
     * extractUsername() — Reads the "sub" (subject) claim from the token.
     * The subject is the username set during token generation.
     *
     * @param token JWT token string
     * @return The username embedded in the token
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * extractExpiration() — Reads the "exp" claim from the token.
     *
     * @param token JWT token string
     * @return The expiration date of the token
     */
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * extractClaim() — Generic claim extractor using a function reference.
     *
     * This design allows type-safe extraction of any claim without
     * writing a separate method for each claim type. The caller
     * provides a Function<Claims, T> that extracts the desired field.
     *
     * Example:
     *   extractClaim(token, claims -> claims.get("roles", List.class))
     *
     * @param token          JWT token string
     * @param claimsResolver Function to extract a specific claim
     * @param <T>            Return type of the claim
     * @return The extracted claim value
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * extractAllClaims() — Parses the JWT and returns all claims.
     *
     * This method also VERIFIES the token signature. If the signature
     * is invalid (tampered token), JJWT throws JwtException, which
     * our JwtAuthFilter catches and rejects the request.
     *
     * @param token JWT token string
     * @return All claims from the token payload
     * @throws JwtException If the token is invalid or tampered
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
            .verifyWith(getSigningKey())  // Provide the verification key
            .build()
            .parseSignedClaims(token)    // Parse and verify signature
            .getPayload();               // Return the claims payload
    }

    // =========================================================================
    // KEY MANAGEMENT
    // =========================================================================

    /**
     * getSigningKey() — Derives a SecretKey from the base64-encoded secret.
     *
     * Why not use a plain String as the key?
     *   JJWT 0.12+ requires a SecretKey object. The Keys.hmacShaKeyFor()
     *   method ensures the key meets the minimum length requirements for
     *   HMAC-SHA256 (at least 256 bits = 32 bytes).
     *
     * The secret is base64-decoded first:
     *   - application.properties stores the secret as a readable string
     *   - Base64 decoding gives us the raw bytes for the key
     *
     * @return HMAC-SHA256 SecretKey for signing/verifying JWTs
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(
            java.util.Base64.getEncoder().encodeToString(jwtSecret.getBytes())
        );
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * getExpirationMs() — Returns the configured expiration in milliseconds.
     * Used by AuthResponse to tell the client when the token expires.
     */
    public Long getExpirationMs() {
        return jwtExpiration;
    }
}
