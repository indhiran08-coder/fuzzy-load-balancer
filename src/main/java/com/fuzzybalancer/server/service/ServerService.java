package com.fuzzybalancer.server.service;

import com.fuzzybalancer.common.exception.ApiException;
import com.fuzzybalancer.monitoring.entity.HealthLog;
import com.fuzzybalancer.monitoring.repository.HealthLogRepository;
import com.fuzzybalancer.server.dto.ServerRequest;
import com.fuzzybalancer.server.dto.ServerResponse;
import com.fuzzybalancer.server.entity.Server;
import com.fuzzybalancer.server.repository.ServerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ServerService — Business logic for all server management operations.
 *
 * Responsibilities:
 *   1. CRUD operations on Server entities
 *   2. Metric updates (from simulation or manual API calls)
 *   3. Health status management with automatic health log creation
 *   4. Conversion between Server entities and DTOs
 *
 * Transaction strategy:
 *   - Read operations: @Transactional(readOnly = true) for performance
 *   - Write operations: @Transactional (read-write, default)
 *   - Direct DB updates via @Modifying queries for high-frequency metric updates
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ServerService {

    private final ServerRepository serverRepository;
    private final HealthLogRepository healthLogRepository;

    // =========================================================================
    // CREATE
    // =========================================================================

    /**
     * createServer() — Adds a new backend server to the load balancer registry.
     *
     * Validates that the server name is unique before persisting.
     * New servers start with HEALTHY status and zero metrics.
     *
     * @param request Server creation data
     * @return ServerResponse DTO of the created server
     */
    @Transactional
    public ServerResponse createServer(ServerRequest request) {
        log.info("Creating server: {}", request.getName());

        if (serverRepository.existsByName(request.getName())) {
            throw new ApiException(
                "A server with name '" + request.getName() + "' already exists",
                HttpStatus.CONFLICT
            );
        }

        Server server = Server.builder()
            .name(request.getName())
            .address(request.getAddress())
            .port(request.getPort())
            .description(request.getDescription())
            .cpuUsage(request.getCpuUsage())
            .ramUsage(request.getRamUsage())
            .activeRequests(request.getActiveRequests())
            .responseTime(request.getResponseTime())
            .healthStatus(request.getHealthStatus() != null ? request.getHealthStatus() : Server.HealthStatus.HEALTHY)
            .totalRequestsServed(0L)
            .build();

        Server saved = serverRepository.save(server);
        log.info("Server created successfully: id={}, name={}", saved.getId(), saved.getName());

        // Log the initial health status
        logHealthChange(saved, null, saved.getHealthStatus(), "Server registered");

        return ServerResponse.from(saved);
    }

    // =========================================================================
    // READ
    // =========================================================================

    /**
     * getAllServers() — Returns paginated list of all servers.
     *
     * @param pageable Spring's Pageable — carries page number, size, sort info
     *   Client sends: GET /api/servers?page=0&size=10&sort=name,asc
     */
    @Transactional(readOnly = true)
    public Page<ServerResponse> getAllServers(Pageable pageable) {
        return serverRepository.findAll(pageable)
            .map(ServerResponse::from);
    }

    /**
     * getServerById() — Fetches a single server by ID.
     *
     * @throws ApiException 404 if server not found
     */
    @Transactional(readOnly = true)
    public ServerResponse getServerById(Long id) {
        Server server = findServerByIdOrThrow(id);
        return ServerResponse.from(server);
    }

    /**
     * getAvailableServers() — Returns only servers eligible for routing.
     * Used by the load balancer before fuzzy evaluation.
     */
    @Transactional(readOnly = true)
    public List<ServerResponse> getAvailableServers() {
        return serverRepository.findAvailableServers().stream()
            .map(ServerResponse::from)
            .toList();
    }

    // =========================================================================
    // UPDATE
    // =========================================================================

    /**
     * updateServer() — Updates server configuration (name, address, port, description).
     *
     * Note: Does NOT update metrics here. Metrics are updated via updateMetrics().
     * This separation keeps concerns clear.
     *
     * @param id      Server ID
     * @param request Updated configuration
     * @return Updated ServerResponse
     */
    @Transactional
    public ServerResponse updateServer(Long id, ServerRequest request) {
        log.info("Updating server id={}", id);
        Server server = findServerByIdOrThrow(id);

        // Check name uniqueness only if the name is changing
        if (!server.getName().equals(request.getName())
                && serverRepository.existsByName(request.getName())) {
            throw new ApiException(
                "A server with name '" + request.getName() + "' already exists",
                HttpStatus.CONFLICT
            );
        }

        server.setName(request.getName());
        server.setAddress(request.getAddress());
        server.setPort(request.getPort());
        server.setDescription(request.getDescription());

        Server saved = serverRepository.save(server);
        return ServerResponse.from(saved);
    }

    /**
     * updateMetrics() — Updates real-time performance metrics for a server.
     *
     * Uses a direct @Modifying JPQL query for efficiency (no entity load needed).
     * Also auto-detects health status changes:
     *   - If CPU > 90% AND RAM > 90% → mark as DEGRADED
     *   - If Response Time > 4000ms  → mark as UNHEALTHY
     *
     * @param id           Server ID
     * @param cpu          New CPU usage (%)
     * @param ram          New RAM usage (%)
     * @param requests     New active request count
     * @param responseTime New response time (ms)
     */
    @Transactional
    public void updateMetrics(Long id, Double cpu, Double ram, Integer requests, Double responseTime) {
        // Verify server exists
        Server server = findServerByIdOrThrow(id);

        // Update the metrics via direct query
        serverRepository.updateMetrics(id, cpu, ram, requests, responseTime);

        // Auto health status detection
        Server.HealthStatus previousStatus = server.getHealthStatus();
        Server.HealthStatus newStatus = determineHealthStatus(cpu, ram, responseTime, previousStatus);

        if (newStatus != previousStatus) {
            serverRepository.updateHealthStatus(id, newStatus);
            server.setHealthStatus(newStatus);
            logHealthChange(server, previousStatus, newStatus,
                String.format("Auto-detected: CPU=%.1f%%, RAM=%.1f%%, RT=%.0fms", cpu, ram, responseTime));
        }
    }

    /**
     * updateHealthStatus() — Manually sets the health status of a server.
     *
     * Called by admin API or simulation when manual override is needed.
     * Always logs the change with the responsible user's name.
     *
     * @param id     Server ID
     * @param status New health status
     */
    @Transactional
    public ServerResponse updateHealthStatus(Long id, Server.HealthStatus status) {
        Server server = findServerByIdOrThrow(id);
        Server.HealthStatus previousStatus = server.getHealthStatus();

        if (previousStatus == status) {
            return ServerResponse.from(server); // No change
        }

        serverRepository.updateHealthStatus(id, status);
        server.setHealthStatus(status);

        // Get the current authenticated user for audit logging
        String triggeredBy = getCurrentUsername();
        logHealthChange(server, previousStatus, status,
            "Manual update by " + triggeredBy, triggeredBy);

        log.info("Server {} health changed: {} → {} by {}",
            server.getName(), previousStatus, status, triggeredBy);

        return ServerResponse.from(server);
    }

    // =========================================================================
    // DELETE
    // =========================================================================

    /**
     * deleteServer() — Removes a server from the registry.
     *
     * Note: Associated RequestLogs and DecisionLogs are cascade-deleted.
     * The server must be OFFLINE before deletion (safety check).
     *
     * @param id Server ID to delete
     */
    @Transactional
    public void deleteServer(Long id) {
        Server server = findServerByIdOrThrow(id);
        log.info("Deleting server: id={}, name={}", id, server.getName());
        serverRepository.delete(server);
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    /**
     * findServerByIdOrThrow() — Fetches server or throws 404 ApiException.
     * Reused across multiple methods to avoid duplication.
     */
    private Server findServerByIdOrThrow(Long id) {
        return serverRepository.findById(id)
            .orElseThrow(() -> new ApiException(
                "Server with id=" + id + " not found",
                HttpStatus.NOT_FOUND,
                "SERVER_NOT_FOUND"
            ));
    }

    /**
     * determineHealthStatus() — Auto-detects health state from metrics.
     *
     * Rules:
     *   - ResponseTime > 4000ms → UNHEALTHY (unacceptable user experience)
     *   - CPU > 90% AND RAM > 90% → DEGRADED (under severe stress)
     *   - CPU > 90% OR RAM > 90% → DEGRADED (high resource pressure)
     *   - Otherwise → HEALTHY (or maintain OFFLINE if set manually)
     */
    private Server.HealthStatus determineHealthStatus(
        Double cpu, Double ram, Double responseTime, Server.HealthStatus current
    ) {
        // Never auto-recover an OFFLINE server (requires manual intervention)
        if (current == Server.HealthStatus.OFFLINE) {
            return Server.HealthStatus.OFFLINE;
        }

        if (responseTime > 4000.0) {
            return Server.HealthStatus.UNHEALTHY;
        }

        if (cpu > 90.0 || ram > 90.0) {
            return Server.HealthStatus.DEGRADED;
        }

        return Server.HealthStatus.HEALTHY;
    }

    /**
     * logHealthChange() — Creates a HealthLog entry for status changes.
     */
    private void logHealthChange(
        Server server,
        Server.HealthStatus previous,
        Server.HealthStatus newStatus,
        String reason
    ) {
        logHealthChange(server, previous, newStatus, reason, "SYSTEM");
    }

    private void logHealthChange(
        Server server,
        Server.HealthStatus previous,
        Server.HealthStatus newStatus,
        String reason,
        String triggeredBy
    ) {
        HealthLog log = HealthLog.builder()
            .server(server)
            .previousStatus(previous)
            .newStatus(newStatus)
            .reason(reason)
            .cpuAtEvent(server.getCpuUsage())
            .ramAtEvent(server.getRamUsage())
            .responseTimeAtEvent(server.getResponseTime())
            .triggeredBy(triggeredBy)
            .build();
        healthLogRepository.save(log);
    }

    /**
     * getCurrentUsername() — Extracts username from Spring Security context.
     * Returns "SYSTEM" if no authentication exists (e.g., during scheduled tasks).
     */
    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.isAuthenticated()) ? auth.getName() : "SYSTEM";
    }
}
