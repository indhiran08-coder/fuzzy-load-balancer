package com.fuzzybalancer.loadbalancer.controller;

import com.fuzzybalancer.common.response.ApiResponse;
import com.fuzzybalancer.loadbalancer.dto.RouteResponse;
import com.fuzzybalancer.loadbalancer.service.LoadBalancerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * LoadBalancerController — REST API for the Fuzzy Logic Load Balancer.
 *
 * Base URL: /api/loadbalancer
 *
 * Endpoints:
 *   POST /api/loadbalancer/route    — Route a request (logs + increments counter)
 *   GET  /api/loadbalancer/evaluate — Dry-run evaluation of all servers
 *   GET  /api/loadbalancer/best     — Get the current best server
 *
 * The /route endpoint is the primary integration point.
 * In a real system, an API Gateway would call this endpoint before proxying
 * the request to the returned server address.
 */
@RestController
@RequestMapping("/api/loadbalancer")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Load Balancer", description = "Fuzzy Logic intelligent request routing")
@SecurityRequirement(name = "bearerAuth")
public class LoadBalancerController {

    private final LoadBalancerService loadBalancerService;

    /**
     * routeRequest() — The primary routing endpoint.
     *
     * The client specifies the path they want to route, and the load balancer
     * returns the best available server to handle that request.
     *
     * In production integration:
     *   1. API Gateway calls: POST /api/loadbalancer/route?path=/users/42
     *   2. Gets back: { "selectedServer": { "address": "192.168.1.10", "port": 8081 } }
     *   3. Gateway proxies the request to 192.168.1.10:8081/users/42
     *
     * Request is also logged to request_logs and decision_logs tables.
     *
     * @param path       The logical path being routed (e.g., "/api/data/users")
     * @param httpRequest Injected by Spring — carries client IP, method, etc.
     * @return RouteResponse with selected server + all fuzzy scores
     */
    @PostMapping("/route")
    @Operation(
        summary = "Route an incoming request",
        description = """
            Runs the Fuzzy Logic engine across all available servers and returns
            the optimal server to handle the request. The decision is logged to
            the database. The selected server's request counter is incremented.
            """
    )
    public ResponseEntity<ApiResponse<RouteResponse>> routeRequest(
        @RequestParam(defaultValue = "/api/request") String path,
        HttpServletRequest httpRequest
    ) {
        RouteResponse response = loadBalancerService.routeRequest(httpRequest, path);
        return ResponseEntity.ok(ApiResponse.success(response,
            "Request routed to " + response.getSelectedServer().getName()
                + " (score: " + String.format("%.2f", response.getWinningScore()) + ")"));
    }

    /**
     * evaluateAllServers() — Dry-run evaluation without side effects.
     *
     * Does NOT log the decision, does NOT increment request counters.
     * Returns fuzzy scores for all available servers sorted by score descending.
     *
     * Use cases:
     *   - Dashboard: "What would happen if a request came in right now?"
     *   - Debugging: "Why is Server-A being chosen over Server-B?"
     *   - Testing: Verify fuzzy scores match expected values
     */
    @GetMapping("/evaluate")
    @Operation(
        summary = "Evaluate all servers (dry-run)",
        description = "Returns fuzzy scores for all available servers without routing or logging"
    )
    public ResponseEntity<ApiResponse<List<RouteResponse.ServerEvaluationResult>>> evaluateAllServers() {
        List<RouteResponse.ServerEvaluationResult> results = loadBalancerService.evaluateAllServers();
        return ResponseEntity.ok(ApiResponse.success(results,
            "Evaluated " + results.size() + " server(s)"));
    }

    /**
     * getBestServer() — Returns the current best server without side effects.
     *
     * Useful for health-check dashboards that want to know the current
     * "recommended" server without triggering a routing decision.
     */
    @GetMapping("/best")
    @Operation(
        summary = "Get current best server",
        description = "Returns the server with the highest fuzzy priority score right now"
    )
    public ResponseEntity<ApiResponse<RouteResponse.ServerEvaluationResult>> getBestServer() {
        RouteResponse.ServerEvaluationResult best = loadBalancerService.getBestServer();
        return ResponseEntity.ok(ApiResponse.success(best,
            "Current best server: " + best.getServerName()
                + " (score: " + String.format("%.2f", best.getFuzzyScore()) + ")"));
    }
}
