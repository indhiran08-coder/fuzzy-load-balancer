package com.fuzzybalancer.monitoring.entity;

import com.fuzzybalancer.server.entity.Server;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * RequestLog — JPA Entity mapped to 'request_logs' table.
 *
 * Records every incoming request that passed through the load balancer.
 * Captures metadata about the request (source, path, method) and the
 * outcome (which server handled it, response time, status code).
 *
 * Used for:
 *   - Monitoring dashboards (total requests, success rate)
 *   - Performance analysis (avg response time)
 *   - Debugging (trace which server handled a specific request)
 */
@Entity
@Table(name = "request_logs", indexes = {
    @Index(name = "idx_request_timestamp", columnList = "request_timestamp"),
    @Index(name = "idx_request_server", columnList = "handled_by_server_id"),
    @Index(name = "idx_request_status", columnList = "status_code")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RequestLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The server that actually handled this request. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "handled_by_server_id",
        foreignKey = @ForeignKey(name = "fk_request_server"))
    private Server handledByServer;

    /** HTTP method: GET, POST, PUT, DELETE, etc. */
    @Column(name = "http_method", length = 10)
    private String httpMethod;

    /** Request path, e.g., "/api/data/users", "/health". */
    @Column(name = "request_path", length = 500)
    private String requestPath;

    /** Client IP address. */
    @Column(name = "client_ip", length = 50)
    private String clientIp;

    /** HTTP status code returned (200, 404, 500, etc.). */
    @Column(name = "status_code")
    private Integer statusCode;

    /** How long the server took to process and respond (ms). */
    @Column(name = "response_time_ms")
    private Long responseTimeMs;

    /** Whether the request completed successfully (2xx status). */
    @Column(name = "success")
    private Boolean success;

    /** Request payload size in bytes. */
    @Column(name = "request_size_bytes")
    private Long requestSizeBytes;

    /** Response payload size in bytes. */
    @Column(name = "response_size_bytes")
    private Long responseSizeBytes;

    /** The fuzzy score of the selected server at the time of routing. */
    @Column(name = "server_fuzzy_score")
    private Double serverFuzzyScore;

    @Column(name = "request_timestamp", nullable = false)
    private LocalDateTime requestTimestamp;

    @PrePersist
    protected void onCreate() {
        requestTimestamp = LocalDateTime.now();
    }
}
