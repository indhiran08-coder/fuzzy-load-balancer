package com.fuzzybalancer.auth.controller;

import com.fuzzybalancer.auth.dto.AuthResponse;
import com.fuzzybalancer.auth.dto.LoginRequest;
import com.fuzzybalancer.auth.dto.RegisterRequest;
import com.fuzzybalancer.auth.service.AuthService;
import com.fuzzybalancer.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * AuthController — REST Controller for authentication endpoints.
 *
 * Exposes:
 *   POST /api/auth/register — Create a new user account
 *   POST /api/auth/login    — Authenticate and receive JWT token
 *
 * These endpoints are PUBLIC (no JWT required), as configured in SecurityConfig.
 *
 * @RestController — Combines @Controller + @ResponseBody.
 *   Every method return value is serialized directly to JSON (via Jackson),
 *   not rendered as a view.
 *
 * @RequestMapping("/api/auth") — All methods in this class have the
 *   "/api/auth" prefix added to their @PostMapping paths.
 *
 * @Tag (Swagger) — Groups these endpoints under "Authentication" in Swagger UI,
 *   making the API docs more organized.
 *
 * @Valid — Triggers Bean Validation on the request body DTO.
 *   If validation fails (e.g., blank username), Spring returns 400 with
 *   validation error details before the method even executes.
 *
 * ResponseEntity<ApiResponse<T>> — Our standard response wrapper:
 *   {
 *     "success": true,
 *     "message": "Login successful",
 *     "data": { ... },
 *     "timestamp": "2024-01-01T10:00:00"
 *   }
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "User registration and login endpoints")
public class AuthController {

    private final AuthService authService;

    /**
     * register() — Handles user registration.
     *
     * @Operation (Swagger) — Documents the endpoint in Swagger UI with
     *   a summary and description.
     *
     * @Valid — Validates RegisterRequest fields before entering the method.
     * @RequestBody — Deserializes the JSON request body into RegisterRequest.
     *
     * Returns HTTP 201 CREATED on success (resource was created).
     *
     * @param request Registration data (username, email, password, confirmPassword)
     * @return AuthResponse with JWT token and user info
     */
    @PostMapping("/register")
    @Operation(
        summary = "Register a new user",
        description = "Creates a new user account and returns a JWT token for immediate use"
    )
    public ResponseEntity<ApiResponse<AuthResponse>> register(
        @Valid @RequestBody RegisterRequest request
    ) {
        log.info("POST /api/auth/register — username: {}", request.getUsername());
        AuthResponse authResponse = authService.register(request);
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(authResponse, "User registered successfully"));
    }

    /**
     * login() — Handles user authentication.
     *
     * Returns HTTP 200 OK on success.
     * Spring Security throws AuthenticationException on failure,
     * which our GlobalExceptionHandler converts to 401 Unauthorized.
     *
     * @param request Login credentials (usernameOrEmail, password)
     * @return AuthResponse with JWT token
     */
    @PostMapping("/login")
    @Operation(
        summary = "Login user",
        description = "Authenticates user credentials and returns a JWT Bearer token"
    )
    public ResponseEntity<ApiResponse<AuthResponse>> login(
        @Valid @RequestBody LoginRequest request
    ) {
        log.info("POST /api/auth/login — identifier: {}", request.getUsernameOrEmail());
        AuthResponse authResponse = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(authResponse, "Login successful"));
    }
}
