package com.fuzzybalancer.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * ApiException — Custom runtime exception for business logic errors.
 *
 * Instead of throwing generic RuntimeException throughout the codebase,
 * we throw ApiException with a specific HTTP status code. The
 * GlobalExceptionHandler catches this and converts it to a proper
 * HTTP error response.
 *
 * Benefits:
 *   1. Decouples HTTP status decisions from business logic
 *   2. Provides a single exception type for the handler to catch
 *   3. Carries meaningful error messages for API consumers
 *
 * Usage:
 *   throw new ApiException("Server not found", HttpStatus.NOT_FOUND);
 *   throw new ApiException("Username taken", HttpStatus.CONFLICT);
 *   throw new ApiException("Invalid input", HttpStatus.BAD_REQUEST);
 */
@Getter
public class ApiException extends RuntimeException {

    /** HTTP status code to return in the error response. */
    private final HttpStatus status;

    /** Optional machine-readable error code for client-side handling. */
    private final String errorCode;

    public ApiException(String message, HttpStatus status) {
        super(message);
        this.status = status;
        this.errorCode = status.name();
    }

    public ApiException(String message, HttpStatus status, String errorCode) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public ApiException(String message, HttpStatus status, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.errorCode = status.name();
    }
}
