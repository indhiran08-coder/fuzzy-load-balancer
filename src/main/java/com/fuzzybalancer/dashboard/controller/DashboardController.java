package com.fuzzybalancer.dashboard.controller;

import com.fuzzybalancer.common.response.ApiResponse;
import com.fuzzybalancer.dashboard.dto.DashboardSummary;
import com.fuzzybalancer.monitoring.repository.DecisionLogRepository;
import com.fuzzybalancer.monitoring.repository.RequestLogRepository;
import com.fuzzybalancer.server.entity.Server;
import com.fuzzybalancer.server.repository.ServerRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DashboardController — Analytics APIs powering the monitoring dashboard.
 *
 * Base URL: /api/dashboard
 *
 * Endpoints:
 *   GET /api/dashboard/summary     — Full KPI summary
 *   GET /api/dashboard/distribution — Load distribution per server
 *   GET /api/dashboard/health      — Health status of all servers
 *   GET /api/dashboard/utilization — Current CPU/RAM per server
 *
 * All endpoints are read-only (GET) and use readOnly transactions.
 * Results are computed in real-time from the DB — no caching for educational clarity.
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Real-time analytics and monitoring APIs")
@SecurityRequirement(name = "bearerAuth")
public class DashboardController {

    private final ServerRepository serverRepository;
    private final DecisionLogRepository decisionLogRepository;
    private final RequestLogRepository requestLogRepository;

    /**
     * getSummary() — Returns the full dashboard KPI summary.
     *
     * Aggregates data from multiple tables:
     *   - request_logs → total requests, success rate, avg response time
     *   - decision_logs → avg fuzzy score, most selected server
     *   - servers → health breakdown, server count
     */
    @GetMapping("/summary")
    @Operation(summary = "Get dashboard summary", description = "Returns aggregated KPIs for the main dashboard")
    public ResponseEntity<ApiResponse<DashboardSummary>> getSummary() {

        // ----- Request stats -----
        long totalRequests = requestLogRepository.count();
        long successfulRequests = requestLogRepository.countSuccessfulRequests();
        long failedRequests = requestLogRepository.countFailedRequests();
        double successRate = totalRequests > 0 ? (successfulRequests * 100.0 / totalRequests) : 0.0;
        long requestsLastHour = requestLogRepository.countRequestsSince(LocalDateTime.now().minusHours(1));
        Double avgResponseTime = requestLogRepository.findAverageResponseTime();
        Double avgFuzzyScore = decisionLogRepository.findAverageWinningScore();

        // ----- Server health stats -----
        long totalServers = serverRepository.count();
        long healthyServers = serverRepository.countByHealthStatus(Server.HealthStatus.HEALTHY);
        long degradedServers = serverRepository.countByHealthStatus(Server.HealthStatus.DEGRADED);
        long unhealthyServers = serverRepository.countByHealthStatus(Server.HealthStatus.UNHEALTHY);
        long offlineServers = serverRepository.countByHealthStatus(Server.HealthStatus.OFFLINE);

        // ----- Load distribution -----
        List<Object[]> selectionData = decisionLogRepository.findSelectionCountPerServer();

        List<DashboardSummary.ServerSelectionCount> selectionCounts = new ArrayList<>();
        Map<String, Double> loadDistribution = new HashMap<>();
        String mostSelectedServer = null;
        long mostSelectedCount = 0;

        for (Object[] row : selectionData) {
            String serverName = (String) row[0];
            Long count = (Long) row[1];
            double percentage = totalRequests > 0 ? (count * 100.0 / totalRequests) : 0.0;

            selectionCounts.add(DashboardSummary.ServerSelectionCount.builder()
                .serverName(serverName)
                .selectionCount(count)
                .percentage(Math.round(percentage * 10.0) / 10.0)
                .build());

            loadDistribution.put(serverName, Math.round(percentage * 10.0) / 10.0);

            if (count > mostSelectedCount) {
                mostSelectedCount = count;
                mostSelectedServer = serverName;
            }
        }

        DashboardSummary summary = DashboardSummary.builder()
            .totalRequests(totalRequests)
            .requestsLastHour(requestsLastHour)
            .successfulRequests(successfulRequests)
            .failedRequests(failedRequests)
            .successRate(Math.round(successRate * 10.0) / 10.0)
            .averageResponseTimeMs(avgResponseTime != null ? Math.round(avgResponseTime * 10.0) / 10.0 : 0.0)
            .averageFuzzyScore(avgFuzzyScore != null ? Math.round(avgFuzzyScore * 100.0) / 100.0 : 0.0)
            .totalServers(totalServers)
            .healthyServers(healthyServers)
            .degradedServers(degradedServers)
            .unhealthyServers(unhealthyServers)
            .offlineServers(offlineServers)
            .availableServers(healthyServers + degradedServers)
            .loadDistribution(loadDistribution)
            .mostSelectedServer(mostSelectedServer)
            .mostSelectedCount(mostSelectedCount)
            .selectionCounts(selectionCounts)
            .build();

        return ResponseEntity.ok(ApiResponse.success(summary, "Dashboard summary generated"));
    }

    /**
     * getDistribution() — Returns load distribution as name → percentage map.
     * Compact version of the summary for quick charts.
     */
    @GetMapping("/distribution")
    @Operation(summary = "Get load distribution", description = "Returns request routing percentage per server")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDistribution() {
        List<Object[]> data = decisionLogRepository.findSelectionCountPerServer();
        long total = decisionLogRepository.count();

        Map<String, Object> result = new HashMap<>();
        for (Object[] row : data) {
            String name = (String) row[0];
            long count = (Long) row[1];
            double pct = total > 0 ? Math.round((count * 100.0 / total) * 10.0) / 10.0 : 0.0;
            result.put(name, Map.of("count", count, "percentage", pct));
        }

        return ResponseEntity.ok(ApiResponse.success(result, "Load distribution retrieved"));
    }

    /**
     * getServerHealth() — Returns current health status of all servers.
     */
    @GetMapping("/health")
    @Operation(summary = "Get server health status")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getServerHealth() {
        List<Map<String, Object>> healthData = serverRepository.findAll().stream()
            .map(server -> {
                Map<String, Object> entry = new HashMap<>();
                entry.put("id", server.getId());
                entry.put("name", server.getName());
                entry.put("healthStatus", server.getHealthStatus());
                entry.put("available", server.isAvailable());
                entry.put("lastUpdated", server.getLastUpdated());
                return entry;
            })
            .toList();

        return ResponseEntity.ok(ApiResponse.success(healthData, "Server health retrieved"));
    }

    /**
     * getUtilization() — Returns current CPU and RAM usage per server.
     * Powers the resource utilization chart on the dashboard.
     */
    @GetMapping("/utilization")
    @Operation(summary = "Get server resource utilization")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getUtilization() {
        List<Map<String, Object>> utilData = serverRepository.findAll().stream()
            .map(server -> {
                Map<String, Object> entry = new HashMap<>();
                entry.put("id", server.getId());
                entry.put("name", server.getName());
                entry.put("cpuUsage", server.getCpuUsage());
                entry.put("ramUsage", server.getRamUsage());
                entry.put("activeRequests", server.getActiveRequests());
                entry.put("responseTime", server.getResponseTime());
                entry.put("healthStatus", server.getHealthStatus());
                entry.put("totalRequestsServed", server.getTotalRequestsServed());
                return entry;
            })
            .toList();

        return ResponseEntity.ok(ApiResponse.success(utilData, "Server utilization retrieved"));
    }
}
