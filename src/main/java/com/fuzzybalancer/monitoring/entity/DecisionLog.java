package com.fuzzybalancer.monitoring.entity;

import com.fuzzybalancer.server.entity.Server;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * DecisionLog — JPA Entity mapped to 'decision_logs' table.
 *
 * Records every load balancer routing decision for auditing and analytics.
 *
 * Each row captures:
 *   - Which server was selected
 *   - The fuzzy score that led to the decision
 *   - The scores of all other candidate servers
 *   - The timestamp of the decision
 *
 * This data powers the Dashboard Module's analytics:
 *   - Most selected server (COUNT by server)
 *   - Average fuzzy score over time
 *   - Load distribution per server
 */
@Entity
@Table(name = "decision_logs", indexes = {
    @Index(name = "idx_decision_timestamp", columnList = "decision_timestamp"),
    @Index(name = "idx_decision_server", columnList = "selected_server_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DecisionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * @ManyToOne — Many decisions can reference the same server.
     * LAZY loading — server details only fetched when explicitly accessed.
     * ForeignKey constraint ensures referential integrity in MySQL.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_server_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_decision_server"))
    private Server selectedServer;

    /**
     * The fuzzy priority score of the winning server (0–100).
     * Higher = better. This is the defuzzified output of the fuzzy engine.
     */
    @Column(name = "winning_score", nullable = false)
    private Double winningScore;

    /**
     * JSON-like string storing scores of all evaluated servers.
     * Format: "Server-A:87.3, Server-B:45.1, Server-C:62.8"
     * Stored as TEXT to accommodate variable number of servers.
     */
    @Column(name = "all_scores", length = 1000)
    private String allScores;

    /**
     * The algorithm used to make the decision.
     * Always "FUZZY_LOGIC" in this system, but extensible for future
     * comparison modes (ROUND_ROBIN, LEAST_CONNECTION, etc.)
     */
    @Column(name = "algorithm", nullable = false, length = 50)
    @Builder.Default
    private String algorithm = "FUZZY_LOGIC";

    /** Client IP address of the incoming request (for audit trails). */
    @Column(name = "client_ip", length = 50)
    private String clientIp;

    /** The endpoint/path that was being routed (e.g., "/api/data"). */
    @Column(name = "request_path", length = 500)
    private String requestPath;

    /** Total time taken by the fuzzy engine to make a decision (ms). */
    @Column(name = "evaluation_time_ms")
    private Long evaluationTimeMs;

    @Column(name = "decision_timestamp", nullable = false)
    private LocalDateTime decisionTimestamp;

    @PrePersist
    protected void onCreate() {
        decisionTimestamp = LocalDateTime.now();
    }
}
