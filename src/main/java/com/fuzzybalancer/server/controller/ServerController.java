package com.fuzzybalancer.server.controller;

import com.fuzzybalancer.common.response.ApiResponse;
import com.fuzzybalancer.server.dto.ServerRequest;
import com.fuzzybalancer.server.dto.ServerResponse;
import com.fuzzybalancer.server.entity.Server;
import com.fuzzybalancer.server.service.ServerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ServerController — REST API for backend server management.
 *
 * Base URL: /api/servers
 *
 * Endpoints:
 *   POST   /api/servers               — Add a new server (ADMIN)
 *   GET    /api/servers               — List all servers (paginated)
 *   GET    /api/servers/{id}          — Get server by ID
 *   GET    /api/servers/available     — Get servers eligible for routing
 *   PUT    /api/servers/{id}          — Update server config (ADMIN)
 *   PATCH  /api/servers/{id}/health   — Update health status (ADMIN)
 *   PUT    /api/servers/{id}/metrics  — Update server metrics
 *   DELETE /api/servers/{id}          — Delete server (ADMIN)
 *
 * @SecurityRequirement(name = "bearerAuth") — Tells Swagger UI to send
 *   the JWT token in the Authorization header when testing these endpoints.
 */
@RestController
@RequestMapping("/api/servers")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Server Management", description = "APIs for managing backend servers in the load balancer")
@SecurityRequirement(name = "bearerAuth")
public class ServerController {

    private final ServerService serverService;

    // -------------------------------------------------------------------------
    // CREATE
    // -------------------------------------------------------------------------

    /**
     * addServer() — Registers a new backend server.
     *
     * @PreAuthorize("hasRole('ADMIN')") — Only users with ROLE_ADMIN can call this.
     * Spring Security evaluates this before entering the method.
     * Returns 403 if the user has ROLE_USER only.
     *
     * Returns HTTP 201 CREATED with the new server's details.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Register a new server", description = "Add a backend server to the load balancer pool (ADMIN only)")
    public ResponseEntity<ApiResponse<ServerResponse>> addServer(
        @Valid @RequestBody ServerRequest request
    ) {
        log.info("POST /api/servers — adding server: {}", request.getName());
        ServerResponse response = serverService.createServer(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(response, "Server registered successfully"));
    }

    // -------------------------------------------------------------------------
    // READ
    // -------------------------------------------------------------------------

    /**
     * getAllServers() — Returns paginated server list.
     *
     * Query params:
     *   page  — Page number (0-indexed, default: 0)
     *   size  — Items per page (default: 10, max: 100)
     *   sort  — Field to sort by (e.g., "name,asc" or "cpuUsage,desc")
     *
     * Example: GET /api/servers?page=0&size=5&sort=cpuUsage,desc
     *
     * @RequestParam(defaultValue = "...") — Uses the default if the param is absent.
     */
    @GetMapping
    @Operation(summary = "List all servers", description = "Returns paginated list of all registered servers")
    public ResponseEntity<ApiResponse<Page<ServerResponse>>> getAllServers(
        @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") int page,
        @Parameter(description = "Items per page") @RequestParam(defaultValue = "10") int size,
        @Parameter(description = "Sort field") @RequestParam(defaultValue = "name") String sortBy,
        @Parameter(description = "Sort direction") @RequestParam(defaultValue = "asc") String direction
    ) {
        Sort sort = direction.equalsIgnoreCase("desc")
            ? Sort.by(sortBy).descending()
            : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(page, Math.min(size, 100), sort);
        Page<ServerResponse> servers = serverService.getAllServers(pageable);
        return ResponseEntity.ok(ApiResponse.success(servers, "Servers retrieved successfully"));
    }

    /**
     * getServerById() — Fetches a single server by its database ID.
     *
     * @PathVariable — Extracts {id} from the URL path.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get server by ID")
    public ResponseEntity<ApiResponse<ServerResponse>> getServerById(
        @PathVariable Long id
    ) {
        ServerResponse server = serverService.getServerById(id);
        return ResponseEntity.ok(ApiResponse.success(server));
    }

    /**
     * getAvailableServers() — Returns only HEALTHY and DEGRADED servers.
     * Used by the load balancer UI to show routing candidates.
     */
    @GetMapping("/available")
    @Operation(summary = "Get available servers", description = "Returns only servers with HEALTHY or DEGRADED status")
    public ResponseEntity<ApiResponse<List<ServerResponse>>> getAvailableServers() {
        List<ServerResponse> servers = serverService.getAvailableServers();
        return ResponseEntity.ok(ApiResponse.success(servers,
            "Found " + servers.size() + " available server(s)"));
    }

    // -------------------------------------------------------------------------
    // UPDATE
    // -------------------------------------------------------------------------

    /**
     * updateServer() — Updates server configuration.
     * ADMIN-only endpoint.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update server configuration")
    public ResponseEntity<ApiResponse<ServerResponse>> updateServer(
        @PathVariable Long id,
        @Valid @RequestBody ServerRequest request
    ) {
        log.info("PUT /api/servers/{} — updating server", id);
        ServerResponse response = serverService.updateServer(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Server updated successfully"));
    }

    /**
     * updateHealthStatus() — Changes the health status of a server.
     *
     * @PatchMapping — Partial update (only changing health status, not the whole entity).
     * PATCH is semantically more correct than PUT for partial updates.
     *
     * Example: PATCH /api/servers/1/health?status=OFFLINE
     */
    @PatchMapping("/{id}/health")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update server health status", description = "Manually set server health: HEALTHY, DEGRADED, UNHEALTHY, OFFLINE")
    public ResponseEntity<ApiResponse<ServerResponse>> updateHealthStatus(
        @PathVariable Long id,
        @RequestParam Server.HealthStatus status
    ) {
        log.info("PATCH /api/servers/{}/health — status={}", id, status);
        ServerResponse response = serverService.updateHealthStatus(id, status);
        return ResponseEntity.ok(ApiResponse.success(response,
            "Health status updated to " + status));
    }

    /**
     * updateMetrics() — Updates real-time performance metrics.
     *
     * Used for manual testing and by the Simulation Module.
     * Example: PUT /api/servers/1/metrics?cpu=75.5&ram=60.0&requests=45&responseTime=350.0
     */
    @PutMapping("/{id}/metrics")
    @Operation(summary = "Update server metrics", description = "Update CPU, RAM, active requests, and response time")
    public ResponseEntity<ApiResponse<String>> updateMetrics(
        @PathVariable Long id,
        @RequestParam Double cpu,
        @RequestParam Double ram,
        @RequestParam Integer requests,
        @RequestParam Double responseTime
    ) {
        serverService.updateMetrics(id, cpu, ram, requests, responseTime);
        return ResponseEntity.ok(ApiResponse.success("Metrics updated", "Server metrics updated successfully"));
    }

    // -------------------------------------------------------------------------
    // DELETE
    // -------------------------------------------------------------------------

    /**
     * deleteServer() — Permanently removes a server.
     * Only ADMINs can delete servers.
     * Returns HTTP 204 NO CONTENT (success but no body).
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a server", description = "Permanently removes a server from the registry (ADMIN only)")
    public ResponseEntity<ApiResponse<Void>> deleteServer(@PathVariable Long id) {
        log.info("DELETE /api/servers/{}", id);
        serverService.deleteServer(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT)
            .body(ApiResponse.success(null, "Server deleted successfully"));
    }
}
