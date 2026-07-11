package com.fuzzybalancer.server.dto;

import com.fuzzybalancer.server.entity.Server;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ServerRequest — DTO for creating and updating Server entities.
 *
 * Used for:
 *   POST /api/servers          — Create a new server
 *   PUT  /api/servers/{id}     — Update an existing server
 *
 * Validation constraints ensure data quality at the API boundary.
 * Server entity is never exposed directly to prevent over-posting.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServerRequest {

    /**
     * name — Unique server identifier.
     * Examples: "Server-A", "eu-west-1-node-1", "prod-api-001"
     */
    @NotBlank(message = "Server name is required")
    @Size(min = 2, max = 100, message = "Server name must be between 2 and 100 characters")
    private String name;

    /**
     * address — Network address (IP or hostname).
     * Examples: "192.168.1.10", "api-server-a.internal.example.com"
     */
    @NotBlank(message = "Server address is required")
    @Size(max = 255, message = "Server address must not exceed 255 characters")
    private String address;

    /**
     * port — Port number (1024–65535 for non-privileged ports).
     */
    @NotNull(message = "Port is required")
    @Min(value = 1, message = "Port must be at least 1")
    @Max(value = 65535, message = "Port must not exceed 65535")
    private Integer port;

    /** Optional description for human identification. */
    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    /**
     * Initial metrics — optional. If not provided, default to 0.
     * These can be set at creation time for pre-populated simulations.
     */
    @DecimalMin(value = "0.0", message = "CPU usage must be >= 0")
    @DecimalMax(value = "100.0", message = "CPU usage must be <= 100")
    @Builder.Default
    private Double cpuUsage = 0.0;

    @DecimalMin(value = "0.0", message = "RAM usage must be >= 0")
    @DecimalMax(value = "100.0", message = "RAM usage must be <= 100")
    @Builder.Default
    private Double ramUsage = 0.0;

    @Min(value = 0, message = "Active requests must be >= 0")
    @Builder.Default
    private Integer activeRequests = 0;

    @DecimalMin(value = "0.0", message = "Response time must be >= 0")
    @Builder.Default
    private Double responseTime = 0.0;

    /** Initial health status. Defaults to HEALTHY. */
    @Builder.Default
    private Server.HealthStatus healthStatus = Server.HealthStatus.HEALTHY;
}
