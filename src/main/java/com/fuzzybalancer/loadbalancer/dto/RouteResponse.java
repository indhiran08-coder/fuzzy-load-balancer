package com.fuzzybalancer.loadbalancer.dto;

import com.fuzzybalancer.fuzzy.engine.FuzzyEvaluationResult;
import com.fuzzybalancer.server.dto.ServerResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * RouteResponse — The result of a load balancer routing decision.
 *
 * Returned by POST /api/loadbalancer/route
 *
 * Contains:
 *   - selectedServer   : The server chosen by fuzzy logic
 *   - winningScore     : The fuzzy priority score of the selected server
 *   - allEvaluations   : Scores for ALL evaluated servers (transparency)
 *   - decisionTimeMs   : How long the fuzzy engine took to decide
 *   - algorithm        : Always "FUZZY_LOGIC" in this system
 *   - timestamp        : When the decision was made
 *
 * The allEvaluations list is key for educational purposes:
 * It shows WHY Server-A won over Server-B with full fuzzy breakdown.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteResponse {

    /** The server selected to handle the request. */
    private ServerResponse selectedServer;

    /** The fuzzy priority score of the winning server (0–100). */
    private double winningScore;

    /** Human-readable priority label: VERY_HIGH, HIGH, MEDIUM, LOW, VERY_LOW. */
    private String priorityLabel;

    /** Fuzzy evaluation results for every available server. */
    private List<ServerEvaluationResult> allEvaluations;

    /** Time taken by the fuzzy engine to make the decision (ms). */
    private long decisionTimeMs;

    /** Algorithm used — always FUZZY_LOGIC. */
    @Builder.Default
    private String algorithm = "FUZZY_LOGIC";

    private LocalDateTime timestamp;

    /** Total number of available servers evaluated. */
    private int serversEvaluated;

    /**
     * ServerEvaluationResult — Per-server fuzzy evaluation detail.
     *
     * Nested inside RouteResponse so clients can see the full scoring breakdown.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServerEvaluationResult {
        private Long serverId;
        private String serverName;
        private double fuzzyScore;
        private String priorityLabel;
        private boolean selected;

        // Fuzzy membership details for this server
        private FuzzyEvaluationResult fuzzyDetail;

        // Current metrics at time of evaluation
        private Double cpuUsage;
        private Double ramUsage;
        private Integer activeRequests;
        private Double responseTime;
        private String healthStatus;
    }
}
