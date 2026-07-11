package com.fuzzybalancer.common.exception;

import com.fuzzybalancer.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * GlobalExceptionHandler — Centralized error handling for all REST APIs.
 *
 * Without this class, unhandled exceptions would return Spring Boot's default
 * white-label error page (HTML) or a stack trace, which is unacceptable for
 * a JSON API.
 *
 * @RestControllerAdvice — Combines @ControllerAdvice + @ResponseBody.
 *   Applies to all @RestController classes in the application.
 *   @ExceptionHandler methods within this class catch exceptions globally.
 *
 * Why centralized handling?
 *   1. DRY — No try-catch blocks in every controller
 *   2. Consistent — All errors follow the ApiResponse structure
 *   3. Security — Stack traces never leak to the client
 *   4. Logging — All errors are logged in one place
 *
 * Exception handling priority:
 *   Spring evaluates @ExceptionHandler methods from most-specific to least-specific.
 *   ApiException is caught before RuntimeException.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * handleApiException() — Catches our custom business logic exceptions.
     *
     * This is the most common error handler — called when services throw
     * ApiException with a specific HTTP status.
     *
     * @param ex The ApiException thrown by a service
     * @return Error response with the status from the exception
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException ex) {
        log.warn("API Exception: {} [{}]", ex.getMessage(), ex.getStatus());
        return ResponseEntity
            .status(ex.getStatus())
            .body(ApiResponse.error(ex.getMessage(), ex.getErrorCode()));
    }

    /**
     * handleValidationException() — Catches Bean Validation failures.
     *
     * Triggered when @Valid on a @RequestBody fails validation constraints
     * like @NotBlank, @Size, @Email, @Pattern.
     *
     * Returns 400 BAD REQUEST with a map of field → error message pairs:
     * {
     *   "success": false,
     *   "message": "Validation failed",
     *   "data": {
     *     "username": "Username must be between 3 and 50 characters",
     *     "email": "Email must be a valid email address"
     *   }
     * }
     *
     * @param ex The validation exception containing all field errors
     * @return 400 response with field-level error details
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationException(
        MethodArgumentNotValidException ex
    ) {
        Map<String, String> errors = new HashMap<>();

        // Collect all field validation errors
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        log.warn("Validation failed: {}", errors);
        ApiResponse<Map<String, String>> response = ApiResponse.<Map<String, String>>builder()
            .success(false)
            .message("Validation failed. Please check the provided data.")
            .data(errors)
            .errorCode("VALIDATION_ERROR")
            .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * handleBadCredentials() — Catches invalid username/password.
     *
     * Spring Security throws BadCredentialsException when:
     *   - Username not found, OR
     *   - Password doesn't match
     *
     * We return a generic "Invalid credentials" message to prevent
     * user enumeration (revealing which field was wrong).
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
        log.warn("Bad credentials attempt: {}", ex.getMessage());
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(ApiResponse.error("Invalid username or password", "INVALID_CREDENTIALS"));
    }

    /**
     * handleAccessDenied() — Catches 403 Forbidden (wrong role).
     *
     * Thrown by Spring Security when a user is authenticated but doesn't
     * have the required role for the endpoint (e.g., USER tries to access
     * an ADMIN-only endpoint).
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(ApiResponse.error("You don't have permission to access this resource", "ACCESS_DENIED"));
    }

    /**
     * handleDisabledException() — Catches disabled account login attempts.
     */
    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiResponse<Void>> handleDisabled(DisabledException ex) {
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(ApiResponse.error("Account is disabled. Contact administrator.", "ACCOUNT_DISABLED"));
    }

    /**
     * handleLockedException() — Catches locked account login attempts.
     */
    @ExceptionHandler(LockedException.class)
    public ResponseEntity<ApiResponse<Void>> handleLocked(LockedException ex) {
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(ApiResponse.error("Account is locked. Contact administrator.", "ACCOUNT_LOCKED"));
    }

    /**
     * handleGeneralException() — Catch-all for unexpected exceptions.
     *
     * Logs the full stack trace (for debugging) but returns a generic
     * 500 message to the client (never exposing internals).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneralException(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error("An unexpected error occurred. Please try again later.", "INTERNAL_ERROR"));
    }
}
