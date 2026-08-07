package com.fuzzybalancer.server.dto;

import com.fuzzybalancer.server.entity.Server;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * ServerResponse — DTO returned by all server-related API endpoints.
 *
 * Contains all safe-to-expose server information:
 *   - Identity fields (id, name, address, port)
 *   - Current metrics (cpu, ram, requests, responseTime)
 *   - Health status
 *   - Statistics (totalRequestsServed)
 *   - Timestamps
 *
 * Why not expose the Server entity directly?
 *   1. Jackson would try to serialize JPA proxies → LazyInitializationException
 *   2. Future entity changes (e.g., adding a 'secretKey' field) wouldn't
 *      accidentally leak into the API
 *   3. Response shape is decoupled from DB schema
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServerResponse {

    private Long id;
    private String name;
    private String address;
    private Integer port;
    private String description;

    // Current metrics
    private Double cpuUsage;
    private Double ramUsage;
    private Integer activeRequests;
    private Double responseTime;

    // Health
    private Server.HealthStatus healthStatus;

    /** Human-readable "available" flag based on health status. */
    private boolean available;

    // Statistics
    private Long totalRequestsServed;

    // Timestamps
    private LocalDateTime lastUpdated;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Static factory — constructs ServerResponse from a Server entity.
     * Used by the mapper class and in tests.
     */
    public static ServerResponse from(Server server) {
        return ServerResponse.builder()
            .id(server.getId())
            .name(server.getName())
            .address(server.getAddress())
            .port(server.getPort())
            .description(server.getDescription())
            .cpuUsage(server.getCpuUsage())
            .ramUsage(server.getRamUsage())
            .activeRequests(server.getActiveRequests())
            .responseTime(server.getResponseTime())
            .healthStatus(server.getHealthStatus())
            .available(server.isAvailable())
            .totalRequestsServed(server.getTotalRequestsServed())
            .lastUpdated(server.getLastUpdated())
            .createdAt(server.getCreatedAt())
            .updatedAt(server.getUpdatedAt())
            .build();
    }
}
