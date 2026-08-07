package com.fuzzybalancer.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * ApiResponse<T> — Generic wrapper for all REST API responses.
 *
 * Every endpoint in this application returns this wrapper to ensure
 * a consistent response structure regardless of the data type.
 *
 * Response structure:
 * {
 *   "success": true,
 *   "message": "Operation completed",
 *   "data": { ... },           // Generic payload
 *   "timestamp": "2024-01-01T10:00:00",
 *   "errorCode": null           // Present only on errors
 * }
 *
 * Why a generic wrapper?
 *   1. Consistency — All clients (web, mobile, Postman) parse one format
 *   2. Error handling — Error responses have the same structure as success
 *   3. Metadata — Timestamp allows debugging and log correlation
 *
 * @JsonInclude(NON_NULL) — Jackson omits null fields from JSON output.
 *   On success: "errorCode" is null → not included in response.
 *   On error: "data" may be null → not included.
 *   This keeps responses clean.
 *
 * @param <T> The type of the data payload (AuthResponse, ServerDTO, etc.)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    /** Whether the operation completed successfully. */
    private boolean success;

    /** Human-readable result message. */
    private String message;

    /** The actual response payload. Null on error responses. */
    private T data;

    /** ISO-8601 timestamp of the response. */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /** Application-specific error code. Null on success responses. */
    private String errorCode;

    // =========================================================================
    // Factory Methods — cleaner than calling the builder everywhere
    // =========================================================================

    /**
     * success() — Creates a successful response with data and message.
     *
     * Usage:
     *   return ResponseEntity.ok(ApiResponse.success(serverDTO, "Server retrieved"));
     */
    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
            .success(true)
            .message(message)
            .data(data)
            .timestamp(LocalDateTime.now())
            .build();
    }

    /**
     * success() — Creates a successful response with data only.
     */
    public static <T> ApiResponse<T> success(T data) {
        return success(data, "Success");
    }

    /**
     * error() — Creates an error response with message and error code.
     *
     * Usage:
     *   return ResponseEntity.status(404)
     *       .body(ApiResponse.error("Server not found", "SERVER_NOT_FOUND"));
     */
    public static <T> ApiResponse<T> error(String message, String errorCode) {
        return ApiResponse.<T>builder()
            .success(false)
            .message(message)
            .errorCode(errorCode)
            .timestamp(LocalDateTime.now())
            .build();
    }

    /**
     * error() — Creates an error response with message only.
     */
    public static <T> ApiResponse<T> error(String message) {
        return error(message, "ERROR");
    }
}
