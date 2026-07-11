package com.fuzzybalancer.server.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Server — JPA Entity mapped to the 'servers' table.
 *
 * Represents a backend server in the simulated cloud environment.
 * Each server has a unique name, address, and real-time metrics.
 *
 * The server's metrics (CPU, RAM, etc.) are stored directly on this entity
 * for simplicity. A separate ServerMetrics entity tracks historical snapshots.
 *
 * Design Decision: Inline metrics vs. separate table
 *   We store current metrics inline (directly on Server) for fast reads
 *   during load balancer decisions, while ServerMetricsLog stores history.
 *   This avoids JOINs on the hot path (fuzzy evaluation).
 */
@Entity
@Table(name = "servers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Server {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Human-readable name, e.g., "Server-A", "Server-B", "Server-C".
     * Unique — prevents duplicate server registrations.
     */
    @Column(nullable = false, unique = true, length = 100)
    private String name;

    /**
     * Network address of the server — could be IP or hostname.
     * Example: "192.168.1.10", "server-a.internal.cloud.com"
     */
    @Column(nullable = false, length = 255)
    private String address;

    /**
     * Port on which the server is listening for requests.
     * Default is 8080 for most microservices.
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer port = 8080;

    /**
     * Optional description — e.g., "Primary EU region server" or
     * "High-memory GPU node".
     */
    @Column(length = 500)
    private String description;

    // -------------------------------------------------------------------------
    // Real-time Metrics (updated by Simulation Module or manual API calls)
    // These are the INPUT variables for the Fuzzy Logic Engine.
    // -------------------------------------------------------------------------

    /**
     * cpuUsage — CPU utilization percentage (0–100).
     * Fuzzy sets: Low (0–30), Medium (20–70), High (60–100)
     */
    @Column(name = "cpu_usage", nullable = false)
    @Builder.Default
    private Double cpuUsage = 0.0;

    /**
     * ramUsage — RAM utilization percentage (0–100).
     * Fuzzy sets: Low (0–40), Medium (30–75), High (65–100)
     */
    @Column(name = "ram_usage", nullable = false)
    @Builder.Default
    private Double ramUsage = 0.0;

    /**
     * activeRequests — Number of requests currently being processed.
     * Fuzzy sets: Low (0–20), Medium (15–60), High (50–200)
     */
    @Column(name = "active_requests", nullable = false)
    @Builder.Default
    private Integer activeRequests = 0;

    /**
     * responseTime — Average response time in milliseconds.
     * Fuzzy sets: Fast (0–200ms), Normal (150–800ms), Slow (700–5000ms)
     */
    @Column(name = "response_time", nullable = false)
    @Builder.Default
    private Double responseTime = 0.0;

    /**
     * HealthStatus — Enum representing the server's operational state.
     *
     * HEALTHY    — Server is fully operational
     * DEGRADED   — Server is running but under stress
     * UNHEALTHY  — Server has failures; should not receive traffic
     * OFFLINE    — Server is completely unreachable
     *
     * The load balancer skips UNHEALTHY and OFFLINE servers entirely,
     * regardless of their fuzzy score.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "health_status", nullable = false, length = 20)
    @Builder.Default
    private HealthStatus healthStatus = HealthStatus.HEALTHY;

    /**
     * lastUpdated — Timestamp of the most recent metric update.
     * Used to detect stale data (e.g., server hasn't reported in 60 seconds).
     */
    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    /** Total requests served by this server (cumulative counter). */
    @Column(name = "total_requests_served", nullable = false)
    @Builder.Default
    private Long totalRequestsServed = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        lastUpdated = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * HealthStatus — Defines valid health states of a server.
     *
     * HEALTHY   : All systems normal. Fuzzy logic evaluates normally.
     * DEGRADED  : Partially functional. Fuzzy logic evaluates normally but
     *             metrics will naturally reflect degradation.
     * UNHEALTHY : Server is failing. Load balancer EXCLUDES this server.
     * OFFLINE   : No connectivity. Load balancer EXCLUDES this server.
     */
    public enum HealthStatus {
        HEALTHY,
        DEGRADED,
        UNHEALTHY,
        OFFLINE
    }

    /**
     * isAvailable() — Convenience method used by the load balancer to
     * quickly filter out servers that should not receive traffic.
     *
     * @return true if the server can accept new requests
     */
    public boolean isAvailable() {
        return healthStatus == HealthStatus.HEALTHY
            || healthStatus == HealthStatus.DEGRADED;
    }

    /**
     * incrementRequestCount() — Atomically increment the served counter.
     * Called when this server is selected by the load balancer.
     */
    public void incrementRequestCount() {
        this.totalRequestsServed++;
        this.activeRequests++;
        this.lastUpdated = LocalDateTime.now();
    }
}
