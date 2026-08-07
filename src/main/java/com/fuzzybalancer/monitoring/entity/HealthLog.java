package com.fuzzybalancer.monitoring.entity;

import com.fuzzybalancer.server.entity.Server;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * HealthLog — JPA Entity mapped to 'health_logs' table.
 *
 * Records every health status change for a server.
 * Provides an audit trail of server availability over time.
 *
 * Example timeline:
 *   2024-01-01 10:00 — Server-A: HEALTHY
 *   2024-01-01 14:30 — Server-A: DEGRADED  (CPU spiked to 90%)
 *   2024-01-01 14:45 — Server-A: UNHEALTHY (response time > 5000ms)
 *   2024-01-01 15:10 — Server-A: HEALTHY   (load reduced)
 *
 * Used for:
 *   - SLA reporting (uptime percentage)
 *   - Incident investigation
 *   - Trend analysis
 */
@Entity
@Table(name = "health_logs", indexes = {
    @Index(name = "idx_health_timestamp", columnList = "log_timestamp"),
    @Index(name = "idx_health_server", columnList = "server_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HealthLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "server_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_health_server"))
    private Server server;

    /**
     * The previous health status before this change.
     * Null if this is the first recorded status for the server.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 20)
    private Server.HealthStatus previousStatus;

    /** The new health status after this change. */
    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 20)
    private Server.HealthStatus newStatus;

    /**
     * Reason for the status change.
     * Examples:
     *   "CPU usage exceeded 90% for 3 consecutive checks"
     *   "Response time dropped below 200ms"
     *   "Manual status update by admin"
     */
    @Column(name = "reason", length = 500)
    private String reason;

    /** CPU usage at the time of this health event. */
    @Column(name = "cpu_at_event")
    private Double cpuAtEvent;

    /** RAM usage at the time of this health event. */
    @Column(name = "ram_at_event")
    private Double ramAtEvent;

    /** Response time at the time of this health event. */
    @Column(name = "response_time_at_event")
    private Double responseTimeAtEvent;

    /** Who triggered this change: "SYSTEM" (auto) or a username (manual). */
    @Column(name = "triggered_by", length = 100)
    @Builder.Default
    private String triggeredBy = "SYSTEM";

    @Column(name = "log_timestamp", nullable = false)
    private LocalDateTime logTimestamp;

    @PrePersist
    protected void onCreate() {
        logTimestamp = LocalDateTime.now();
    }
}
