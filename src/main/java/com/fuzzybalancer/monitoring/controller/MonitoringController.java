package com.fuzzybalancer.monitoring.controller;

import com.fuzzybalancer.common.response.ApiResponse;
import com.fuzzybalancer.monitoring.entity.DecisionLog;
import com.fuzzybalancer.monitoring.entity.HealthLog;
import com.fuzzybalancer.monitoring.entity.RequestLog;
import com.fuzzybalancer.monitoring.repository.DecisionLogRepository;
import com.fuzzybalancer.monitoring.repository.HealthLogRepository;
import com.fuzzybalancer.monitoring.repository.RequestLogRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * MonitoringController — REST API for viewing logs and monitoring data.
 *
 * Base URL: /api/monitoring
 *
 * Endpoints:
 *   GET /api/monitoring/requests           — All request logs (paginated)
 *   GET /api/monitoring/requests/server/{id} — Requests by server
 *   GET /api/monitoring/decisions          — All decision logs (paginated)
 *   GET /api/monitoring/decisions/server/{id} — Decisions by server
 *   GET /api/monitoring/decisions/timerange — Decisions filtered by time
 *   GET /api/monitoring/health             — All health change logs
 *   GET /api/monitoring/health/server/{id} — Health logs for a server
 *
 * All endpoints are GET (read-only). No write operations in monitoring.
 */
@RestController
@RequestMapping("/api/monitoring")
@RequiredArgsConstructor
@Tag(name = "Monitoring", description = "Request logs, decision logs, and health change history")
@SecurityRequirement(name = "bearerAuth")
public class MonitoringController {

    private final RequestLogRepository requestLogRepository;
    private final DecisionLogRepository decisionLogRepository;
    private final HealthLogRepository healthLogRepository;

    // =========================================================================
    // REQUEST LOGS
    // =========================================================================

    /**
     * getAllRequests() — Paginated, sortable list of all request logs.
     *
     * Example: GET /api/monitoring/requests?page=0&size=20&sort=requestTimestamp,desc
     */
    @GetMapping("/requests")
    @Operation(
        summary = "Get all request logs",
        description = "Returns paginated request history with server, timing, and status details"
    )
    public ResponseEntity<ApiResponse<Page<RequestLog>>> getAllRequests(
        @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
        @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
        @Parameter(description = "Sort field") @RequestParam(defaultValue = "requestTimestamp") String sortBy,
        @Parameter(description = "Sort direction") @RequestParam(defaultValue = "desc") String direction
    ) {
        Sort sort = direction.equalsIgnoreCase("asc")
            ? Sort.by(sortBy).ascending()
            : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), sort);
        Page<RequestLog> logs = requestLogRepository.findAll(pageable);
        return ResponseEntity.ok(ApiResponse.success(logs, "Request logs retrieved"));
    }

    /**
     * getRequestsByServer() — Filter request logs by the handling server.
     */
    @GetMapping("/requests/server/{serverId}")
    @Operation(summary = "Get request logs for a specific server")
    public ResponseEntity<ApiResponse<Page<RequestLog>>> getRequestsByServer(
        @PathVariable Long serverId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("requestTimestamp").descending());
        Page<RequestLog> logs = requestLogRepository.findByHandledByServerId(serverId, pageable);
        return ResponseEntity.ok(ApiResponse.success(logs));
    }

    // =========================================================================
    // DECISION LOGS
    // =========================================================================

    /**
     * getAllDecisions() — Paginated list of all fuzzy routing decisions.
     *
     * Each entry shows which server was selected, its fuzzy score,
     * all competing scores, and the evaluation time.
     */
    @GetMapping("/decisions")
    @Operation(
        summary = "Get all decision logs",
        description = "Returns the complete history of load balancer routing decisions with fuzzy scores"
    )
    public ResponseEntity<ApiResponse<Page<DecisionLog>>> getAllDecisions(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "decisionTimestamp") String sortBy,
        @RequestParam(defaultValue = "desc") String direction
    ) {
        Sort sort = direction.equalsIgnoreCase("asc")
            ? Sort.by(sortBy).ascending()
            : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), sort);
        Page<DecisionLog> logs = decisionLogRepository.findAll(pageable);
        return ResponseEntity.ok(ApiResponse.success(logs, "Decision logs retrieved"));
    }

    /**
     * getDecisionsByServer() — Filter decisions by the winning server.
     * Useful for: "How often was Server-A the best choice?"
     */
    @GetMapping("/decisions/server/{serverId}")
    @Operation(summary = "Get decision logs for a specific server")
    public ResponseEntity<ApiResponse<Page<DecisionLog>>> getDecisionsByServer(
        @PathVariable Long serverId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("decisionTimestamp").descending());
        Page<DecisionLog> logs = decisionLogRepository.findBySelectedServerId(serverId, pageable);
        return ResponseEntity.ok(ApiResponse.success(logs));
    }

    /**
     * getDecisionsByTimeRange() — Filter decisions within a time window.
     *
     * Example:
     *   GET /api/monitoring/decisions/timerange
     *       ?from=2024-01-01T00:00:00&to=2024-01-01T23:59:59
     *
     * @DateTimeFormat(iso = ISO.DATE_TIME) — Parses the ISO-8601 query
     *   parameter string into a LocalDateTime object automatically.
     */
    @GetMapping("/decisions/timerange")
    @Operation(
        summary = "Get decisions within a time range",
        description = "Filter routing decisions between 'from' and 'to' timestamps (ISO-8601 format)"
    )
    public ResponseEntity<ApiResponse<Page<DecisionLog>>> getDecisionsByTimeRange(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("decisionTimestamp").descending());
        Page<DecisionLog> logs = decisionLogRepository.findByTimeRange(from, to, pageable);
        return ResponseEntity.ok(ApiResponse.success(logs,
            String.format("Decisions between %s and %s", from, to)));
    }

    // =========================================================================
    // HEALTH LOGS
    // =========================================================================

    /**
     * getAllHealthLogs() — Paginated list of all server health change events.
     *
     * Every status transition (e.g., HEALTHY → DEGRADED → UNHEALTHY → HEALTHY)
     * is recorded with timestamp, reason, and metrics at the time of the event.
     */
    @GetMapping("/health")
    @Operation(
        summary = "Get all health change logs",
        description = "Returns the complete history of server health status transitions"
    )
    public ResponseEntity<ApiResponse<Page<HealthLog>>> getAllHealthLogs(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100),
            Sort.by("logTimestamp").descending());
        Page<HealthLog> logs = healthLogRepository.findAll(pageable);
        return ResponseEntity.ok(ApiResponse.success(logs, "Health logs retrieved"));
    }

    /**
     * getHealthLogsByServer() — Health history for a specific server.
     * Useful for: "Why did Server-B become UNHEALTHY yesterday?"
     */
    @GetMapping("/health/server/{serverId}")
    @Operation(summary = "Get health change history for a specific server")
    public ResponseEntity<ApiResponse<Page<HealthLog>>> getHealthLogsByServer(
        @PathVariable Long serverId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("logTimestamp").descending());
        Page<HealthLog> logs = healthLogRepository.findByServerId(serverId, pageable);
        return ResponseEntity.ok(ApiResponse.success(logs));
    }
}
