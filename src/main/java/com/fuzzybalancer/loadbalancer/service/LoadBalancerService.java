package com.fuzzybalancer.loadbalancer.service;

import com.fuzzybalancer.common.exception.ApiException;
import com.fuzzybalancer.fuzzy.engine.FuzzyEvaluationResult;
import com.fuzzybalancer.fuzzy.engine.FuzzyRuleEngine;
import com.fuzzybalancer.loadbalancer.dto.RouteResponse;
import com.fuzzybalancer.monitoring.entity.DecisionLog;
import com.fuzzybalancer.monitoring.entity.RequestLog;
import com.fuzzybalancer.monitoring.repository.DecisionLogRepository;
import com.fuzzybalancer.monitoring.repository.RequestLogRepository;
import com.fuzzybalancer.server.dto.ServerResponse;
import com.fuzzybalancer.server.entity.Server;
import com.fuzzybalancer.server.repository.ServerRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * LoadBalancerService — The brain of the system.
 *
 * Orchestrates the full request routing pipeline:
 *
 * 1. Fetch all AVAILABLE servers (HEALTHY or DEGRADED)
 * 2. For each server, run the Fuzzy Logic Engine to compute a priority score
 * 3. Select the server with the HIGHEST fuzzy score
 * 4. Increment that server's request counter
 * 5. Log the decision (DecisionLog) and the request (RequestLog)
 * 6. Return a RouteResponse with the decision and all scores
 *
 * This service answers the central question:
 * "Given the current state of all backend servers,
 *  which one should handle the next incoming request?"
 *
 * Comparison with Traditional Algorithms:
 * ┌─────────────────┬─────────────────────────────────────────────────┐
 * │ Algorithm       │ How it picks the server                         │
 * ├─────────────────┼─────────────────────────────────────────────────┤
 * │ Round Robin     │ Takes turns in order — ignores actual load       │
 * │ Least Conn.     │ Picks server with fewest active requests only    │
 * │ Random          │ Picks randomly — no intelligence                 │
 * │ Fuzzy Logic     │ Considers CPU + RAM + Requests + Response Time   │
 * │                 │ simultaneously using expert rules                │
 * └─────────────────┴─────────────────────────────────────────────────┘
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoadBalancerService {

    private final ServerRepository serverRepository;
    private final FuzzyRuleEngine fuzzyRuleEngine;
    private final DecisionLogRepository decisionLogRepository;
    private final RequestLogRepository requestLogRepository;

    // =========================================================================
    // MAIN ROUTING METHOD
    // =========================================================================

    /**
     * routeRequest() — Main entry point for request routing.
     *
     * This is called on every incoming request to determine which backend
     * server should handle it. The entire fuzzy evaluation pipeline runs
     * synchronously within this method.
     *
     * Performance note:
     *   With N servers and M=200 defuzzification points:
     *   Evaluation cost ≈ O(N × rules × M) per request.
     *   For 3 servers, 25 rules, 200 points: ~15,000 operations.
     *   This takes ~1-5ms on modern hardware — negligible routing overhead.
     *
     * @param httpRequest The incoming HTTP request (for logging IP/path)
     * @param requestPath The logical path being routed (e.g., "/api/data")
     * @return RouteResponse with the selected server and full score breakdown
     */
    @Transactional
    public RouteResponse routeRequest(HttpServletRequest httpRequest, String requestPath) {
        long startTime = System.currentTimeMillis();

        log.info("Load balancer routing request: {}", requestPath);

        // Step 1: Get all available servers
        List<Server> availableServers = serverRepository.findAvailableServers();

        if (availableServers.isEmpty()) {
            throw new ApiException(
                "No available servers. All servers are UNHEALTHY or OFFLINE.",
                HttpStatus.SERVICE_UNAVAILABLE,
                "NO_AVAILABLE_SERVERS"
            );
        }

        log.debug("Evaluating {} available servers", availableServers.size());

        // Step 2: Run Fuzzy Logic on each server
        List<RouteResponse.ServerEvaluationResult> evaluations = new ArrayList<>();
        for (Server server : availableServers) {
            FuzzyEvaluationResult fuzzyResult = fuzzyRuleEngine.evaluate(
                server.getCpuUsage(),
                server.getRamUsage(),
                server.getActiveRequests().doubleValue(),
                server.getResponseTime()
            );

            RouteResponse.ServerEvaluationResult eval = RouteResponse.ServerEvaluationResult.builder()
                .serverId(server.getId())
                .serverName(server.getName())
                .fuzzyScore(fuzzyResult.getCrispScore())
                .priorityLabel(fuzzyResult.getPriorityLabel())
                .selected(false)
                .fuzzyDetail(fuzzyResult)
                .cpuUsage(server.getCpuUsage())
                .ramUsage(server.getRamUsage())
                .activeRequests(server.getActiveRequests())
                .responseTime(server.getResponseTime())
                .healthStatus(server.getHealthStatus().name())
                .build();

            evaluations.add(eval);
            log.debug("Server {} → fuzzy score: {:.2f}", server.getName(), fuzzyResult.getCrispScore());
        }

        // Step 3: Select the server with the highest fuzzy score
        RouteResponse.ServerEvaluationResult winner = evaluations.stream()
            .max(Comparator.comparingDouble(RouteResponse.ServerEvaluationResult::getFuzzyScore))
            .orElseThrow(() -> new ApiException("Evaluation failed", HttpStatus.INTERNAL_SERVER_ERROR));

        winner.setSelected(true);

        // Step 4: Increment the selected server's request count
        serverRepository.incrementRequestCount(winner.getServerId());

        long decisionTime = System.currentTimeMillis() - startTime;

        // Step 5: Build all-scores summary string for the decision log
        String allScoresSummary = evaluations.stream()
            .map(e -> String.format("%s:%.2f", e.getServerName(), e.getFuzzyScore()))
            .collect(Collectors.joining(", "));

        // Step 6: Fetch the selected server entity for logging
        Server selectedServer = serverRepository.findById(winner.getServerId())
            .orElseThrow(() -> new ApiException("Server not found after selection", HttpStatus.INTERNAL_SERVER_ERROR));

        // Step 7: Persist the Decision Log
        DecisionLog decisionLog = DecisionLog.builder()
            .selectedServer(selectedServer)
            .winningScore(winner.getFuzzyScore())
            .allScores(allScoresSummary)
            .algorithm("FUZZY_LOGIC")
            .clientIp(getClientIp(httpRequest))
            .requestPath(requestPath)
            .evaluationTimeMs(decisionTime)
            .build();
        decisionLogRepository.save(decisionLog);

        // Step 8: Persist the Request Log
        RequestLog requestLog = RequestLog.builder()
            .handledByServer(selectedServer)
            .httpMethod(httpRequest != null ? httpRequest.getMethod() : "SIMULATED")
            .requestPath(requestPath)
            .clientIp(getClientIp(httpRequest))
            .statusCode(200)
            .success(true)
            .serverFuzzyScore(winner.getFuzzyScore())
            .build();
        requestLogRepository.save(requestLog);

        log.info("Routing decision: {} selected (score={:.2f}) in {}ms",
            winner.getServerName(), winner.getFuzzyScore(), decisionTime);

        // Step 9: Build and return RouteResponse
        return RouteResponse.builder()
            .selectedServer(ServerResponse.from(selectedServer))
            .winningScore(winner.getFuzzyScore())
            .priorityLabel(winner.getPriorityLabel())
            .allEvaluations(evaluations)
            .decisionTimeMs(decisionTime)
            .algorithm("FUZZY_LOGIC")
            .timestamp(LocalDateTime.now())
            .serversEvaluated(evaluations.size())
            .build();
    }

    // =========================================================================
    // EVALUATE ONLY (no actual routing)
    // =========================================================================

    /**
     * evaluateAllServers() — Runs fuzzy evaluation without routing.
     *
     * Returns scores for all servers without:
     *   - Incrementing request counters
     *   - Persisting logs
     *
     * Used for:
     *   - Dashboard "dry-run" view
     *   - Debugging fuzzy scores
     *   - Testing the engine
     *
     * @return List of ServerEvaluationResult sorted by score descending
     */
    public List<RouteResponse.ServerEvaluationResult> evaluateAllServers() {
        List<Server> availableServers = serverRepository.findAvailableServers();

        if (availableServers.isEmpty()) {
            return List.of();
        }

        List<RouteResponse.ServerEvaluationResult> results = availableServers.stream()
            .map(server -> {
                FuzzyEvaluationResult result = fuzzyRuleEngine.evaluate(
                    server.getCpuUsage(),
                    server.getRamUsage(),
                    server.getActiveRequests().doubleValue(),
                    server.getResponseTime()
                );

                return RouteResponse.ServerEvaluationResult.builder()
                    .serverId(server.getId())
                    .serverName(server.getName())
                    .fuzzyScore(result.getCrispScore())
                    .priorityLabel(result.getPriorityLabel())
                    .selected(false)
                    .fuzzyDetail(result)
                    .cpuUsage(server.getCpuUsage())
                    .ramUsage(server.getRamUsage())
                    .activeRequests(server.getActiveRequests())
                    .responseTime(server.getResponseTime())
                    .healthStatus(server.getHealthStatus().name())
                    .build();
            })
            .sorted(Comparator.comparingDouble(RouteResponse.ServerEvaluationResult::getFuzzyScore).reversed())
            .toList();

        // Mark the best server
        if (!results.isEmpty()) {
            results.getFirst().setSelected(true);
        }

        return results;
    }

    /**
     * getBestServer() — Returns the current best server without routing.
     * Quick lookup — only returns the winner, not all scores.
     */
    public RouteResponse.ServerEvaluationResult getBestServer() {
        List<RouteResponse.ServerEvaluationResult> evaluations = evaluateAllServers();

        if (evaluations.isEmpty()) {
            throw new ApiException(
                "No available servers to evaluate",
                HttpStatus.SERVICE_UNAVAILABLE
            );
        }

        return evaluations.getFirst(); // Already sorted DESC
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    /**
     * getClientIp() — Extracts the real client IP from the request.
     *
     * Checks X-Forwarded-For header first (set by proxies/load balancers
     * sitting in front of this app). Falls back to getRemoteAddr() if absent.
     *
     * @param request HTTP request (may be null in simulation mode)
     * @return Client IP string or "SIMULATED"
     */
    private String getClientIp(HttpServletRequest request) {
        if (request == null) return "SIMULATED";

        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
