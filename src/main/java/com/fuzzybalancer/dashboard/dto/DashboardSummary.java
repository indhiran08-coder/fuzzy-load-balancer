package com.fuzzybalancer.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * DashboardSummary — Aggregated metrics for the main dashboard view.
 *
 * Returned by GET /api/dashboard/summary
 *
 * Contains all the numbers needed for the dashboard KPI cards:
 *   - Total requests processed
 *   - Average fuzzy score
 *   - Server health breakdown
 *   - Load distribution
 *   - Most selected server
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummary {

    // Request statistics
    private Long totalRequests;
    private Long requestsLastHour;
    private Long successfulRequests;
    private Long failedRequests;
    private Double successRate;

    // Performance
    private Double averageResponseTimeMs;
    private Double averageFuzzyScore;
    private Long averageDecisionTimeMs;

    // Server health breakdown
    private Long totalServers;
    private Long healthyServers;
    private Long degradedServers;
    private Long unhealthyServers;
    private Long offlineServers;
    private Long availableServers;

    // Load distribution (server name → % of total requests)
    private Map<String, Double> loadDistribution;

    // Most selected server
    private String mostSelectedServer;
    private Long mostSelectedCount;

    // Server selection counts (server name → count)
    private List<ServerSelectionCount> selectionCounts;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServerSelectionCount {
        private String serverName;
        private Long selectionCount;
        private Double percentage;
    }
}
