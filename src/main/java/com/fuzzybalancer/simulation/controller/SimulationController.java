package com.fuzzybalancer.simulation.controller;

import com.fuzzybalancer.common.response.ApiResponse;
import com.fuzzybalancer.simulation.service.SimulationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * SimulationController — REST API for controlling the server load simulation.
 *
 * Base URL: /api/simulation
 *
 * Endpoints:
 *   POST /api/simulation/start        — Start auto-simulation
 *   POST /api/simulation/stop         — Stop auto-simulation
 *   GET  /api/simulation/status       — Check if simulation is running
 *   POST /api/simulation/trigger      — Manual single tick
 *   POST /api/simulation/stress       — Apply stress to a random server
 *   POST /api/simulation/reset        — Reset all metrics to baseline
 */
@RestController
@RequestMapping("/api/simulation")
@RequiredArgsConstructor
@Tag(name = "Simulation", description = "Control the server load simulation engine")
@SecurityRequirement(name = "bearerAuth")
public class SimulationController {

    private final SimulationService simulationService;

    @PostMapping("/start")
    @Operation(summary = "Start simulation", description = "Starts automatic periodic server metric updates")
    public ResponseEntity<ApiResponse<String>> startSimulation() {
        String message = simulationService.startSimulation();
        return ResponseEntity.ok(ApiResponse.success(message));
    }

    @PostMapping("/stop")
    @Operation(summary = "Stop simulation", description = "Stops automatic metric updates — metrics become static")
    public ResponseEntity<ApiResponse<String>> stopSimulation() {
        String message = simulationService.stopSimulation();
        return ResponseEntity.ok(ApiResponse.success(message));
    }

    @GetMapping("/status")
    @Operation(summary = "Get simulation status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStatus() {
        Map<String, Object> status = Map.of(
            "running", simulationService.isSimulationRunning(),
            "description", simulationService.isSimulationRunning()
                ? "Simulation is active — server metrics are updating automatically"
                : "Simulation is stopped — server metrics are static"
        );
        return ResponseEntity.ok(ApiResponse.success(status));
    }

    @PostMapping("/trigger")
    @Operation(summary = "Manual tick", description = "Triggers one round of metric updates immediately")
    public ResponseEntity<ApiResponse<String>> triggerTick() {
        String message = simulationService.triggerManualTick();
        return ResponseEntity.ok(ApiResponse.success(message));
    }

    @PostMapping("/stress")
    @Operation(summary = "Stress test", description = "Applies heavy load to a random server to demonstrate fuzzy routing avoidance")
    public ResponseEntity<ApiResponse<String>> stressTest() {
        String message = simulationService.simulateStressTest();
        return ResponseEntity.ok(ApiResponse.success(message));
    }

    @PostMapping("/reset")
    @Operation(summary = "Reset metrics", description = "Resets all server metrics to low baseline values")
    public ResponseEntity<ApiResponse<String>> resetMetrics() {
        String message = simulationService.resetAllMetrics();
        return ResponseEntity.ok(ApiResponse.success(message));
    }
}
