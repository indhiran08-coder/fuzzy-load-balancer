package com.fuzzybalancer.auth.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * RegisterRequest — Data Transfer Object (DTO) for user registration.
 *
 * Why DTOs instead of entities directly?
 *   1. Security: Prevents mass-assignment attacks (e.g., users setting their own role)
 *   2. Decoupling: API contract is independent of DB schema changes
 *   3. Validation: Bean validation annotations apply at the API layer
 *   4. Serialization: No JPA proxies or circular references in JSON
 *
 * @Data       — Lombok: getter/setter/equals/hashCode/toString
 * @NotBlank   — Field must not be null, empty, or whitespace-only
 * @Size       — Length constraint (min/max characters)
 * @Email      — Must match a valid email format
 * @Pattern    — Custom regex validation
 */
@Data
public class RegisterRequest {

    /**
     * username — Must be 3–50 characters, alphanumeric with underscores.
     * @Pattern enforces no special characters that could cause injection issues.
     */
    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    @Pattern(
        regexp = "^[a-zA-Z0-9_]+$",
        message = "Username can only contain letters, numbers, and underscores"
    )
    private String username;

    /** email — Standard email format validation via @Email annotation. */
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    @Size(max = 100, message = "Email must not exceed 100 characters")
    private String email;

    /**
     * password — Must be at least 8 characters.
     * In production, @Pattern would enforce complexity rules
     * (uppercase, digit, special character).
     */
    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    private String password;

    /**
     * confirmPassword — Must match password.
     * Cross-field validation is done in the service layer
     * (PasswordEncoder makes it impossible at the DTO layer).
     */
    @NotBlank(message = "Confirm password is required")
    private String confirmPassword;
}
