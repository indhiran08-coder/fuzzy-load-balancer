package com.fuzzybalancer.simulation.service;

import com.fuzzybalancer.loadbalancer.service.LoadBalancerService;
import com.fuzzybalancer.server.entity.Server;
import com.fuzzybalancer.server.repository.ServerRepository;
import com.fuzzybalancer.server.service.ServerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SimulationService — Generates realistic load fluctuations on backend servers.
 *
 * Purpose:
 *   Simulates real-world server behavior so the load balancer can be tested
 *   without real backend servers. Metrics change over time to mimic:
 *   - Traffic spikes (sudden CPU/request increases)
 *   - Memory leaks (gradual RAM increase)
 *   - Slow endpoints (response time spikes)
 *   - Recovery after load reduction
 *
 * Simulation Model:
 *   Each server has a "drift" — a tendency to move in a direction.
 *   Metrics perform a random walk with realistic constraints:
 *   - CPU: changes by ±(5–20%) per tick, bounded [0, 100]
 *   - RAM: changes by ±(2–10%) per tick (RAM grows slower than CPU)
 *   - Requests: changes by ±(5–30) per tick
 *   - Response Time: correlated with CPU and request load
 *
 * Scheduling:
 *   @Scheduled runs the updateMetrics() method every N seconds.
 *   N is configured via app.simulation.interval-ms in application.properties.
 *   Can be toggled on/off via the REST API.
 *
 * AtomicBoolean isRunning:
 *   Thread-safe flag to enable/disable simulation at runtime.
 *   Uses AtomicBoolean (not volatile boolean) for safe concurrent access
 *   between the scheduler thread and API threads.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SimulationService {

    private final ServerRepository serverRepository;
    private final ServerService serverService;
    private final LoadBalancerService loadBalancerService;

    /**
     * isRunning — Controls whether the simulation is active.
     * Initially false — simulation must be started explicitly via the API.
     */
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    /** Seeded Random for reproducible test scenarios (can be overridden). */
    private final Random random = new Random();

    @Value("${app.simulation.auto-start:false}")
    private boolean autoStart;

    /**
     * onApplicationStartup() — Optionally start simulation automatically.
     * Called after Spring context initializes if auto-start is enabled.
     */
    @Transactional
    public void initialize() {
        if (autoStart) {
            log.info("Auto-starting simulation (app.simulation.auto-start=true)");
            isRunning.set(true);
        }
    }

    // =========================================================================
    // SCHEDULED TASK
    // =========================================================================

    /**
     * updateServerMetrics() — Periodic task that evolves server metrics.
     *
     * @Scheduled(fixedDelayString = "...") — Runs N milliseconds AFTER
     *   the previous execution completes (not a fixed rate).
     *   fixedDelay prevents overlapping executions if a tick takes too long.
     *
     * The method is a no-op when simulation is stopped (isRunning = false).
     */
    @Scheduled(fixedDelayString = "${app.simulation.interval-ms:5000}")
    @Transactional
    public void updateServerMetrics() {
        if (!isRunning.get()) {
            return; // Simulation stopped — do nothing
        }

        List<Server> servers = serverRepository.findAll();
        if (servers.isEmpty()) {
            log.debug("Simulation tick: no servers to update");
            return;
        }

        log.debug("Simulation tick: updating {} servers", servers.size());

        for (Server server : servers) {
            if (server.getHealthStatus() == Server.HealthStatus.OFFLINE) {
                continue; // Skip offline servers
            }

            // Generate new metric values using random walk
            double newCpu = evolveMetric(server.getCpuUsage(), 5, 20, 0, 100);
            double newRam = evolveMetric(server.getRamUsage(), 2, 10, 0, 100);
            int newRequests = (int) evolveMetric(server.getActiveRequests(), 3, 25, 0, 200);

            // Response time is correlated with CPU and request load:
            // Higher CPU/requests → higher response time
            double baseResponseTime = 50 + (newCpu * 15) + (newRequests * 5);
            double responseTimeNoise = (random.nextDouble() - 0.5) * 200;
            double newResponseTime = Math.max(20, Math.min(5000, baseResponseTime + responseTimeNoise));

            // Apply the updates via the efficient @Modifying query
            serverService.updateMetrics(server.getId(), newCpu, newRam, newRequests, newResponseTime);

            log.trace("Server {}: CPU={:.1f}%, RAM={:.1f}%, Req={}, RT={:.0f}ms",
                server.getName(), newCpu, newRam, newRequests, newResponseTime);
        }

        // Optionally simulate an incoming request every tick
        simulateIncomingRequest();
    }

    // =========================================================================
    // SIMULATION CONTROLS
    // =========================================================================

    /**
     * startSimulation() — Enables the scheduled metric updates.
     *
     * @return Status message
     */
    public String startSimulation() {
        if (isRunning.getAndSet(true)) {
            return "Simulation is already running";
        }
        log.info("Simulation STARTED");
        return "Simulation started. Server metrics will update every "
            + "${app.simulation.interval-ms} ms.";
    }

    /**
     * stopSimulation() — Disables the scheduled metric updates.
     *
     * @return Status message
     */
    public String stopSimulation() {
        if (!isRunning.getAndSet(false)) {
            return "Simulation is not running";
        }
        log.info("Simulation STOPPED");
        return "Simulation stopped. Server metrics are now static.";
    }

    /**
     * isSimulationRunning() — Status check.
     */
    public boolean isSimulationRunning() {
        return isRunning.get();
    }

    /**
     * triggerManualTick() — Manually triggers one simulation update cycle.
     *
     * Useful for testing: push one update without waiting for the scheduler.
     * Works even when simulation is stopped.
     */
    @Transactional
    public String triggerManualTick() {
        boolean wasRunning = isRunning.get();
        isRunning.set(true);
        updateServerMetrics();
        isRunning.set(wasRunning);
        return "Manual simulation tick executed. Server metrics updated.";
    }

    /**
     * resetAllMetrics() — Resets all server metrics to baseline values.
     *
     * Useful for resetting a test environment to a clean state.
     */
    @Transactional
    public String resetAllMetrics() {
        List<Server> servers = serverRepository.findAll();
        for (Server server : servers) {
            serverService.updateMetrics(server.getId(), 10.0, 15.0, 3, 80.0);
        }
        log.info("Reset metrics for {} servers to baseline", servers.size());
        return "Reset " + servers.size() + " server(s) to baseline metrics (CPU=10%, RAM=15%, Req=3, RT=80ms)";
    }

    /**
     * simulateStressTest() — Puts one random server under heavy load.
     * Useful for demonstrating fuzzy logic's ability to avoid the stressed server.
     */
    @Transactional
    public String simulateStressTest() {
        List<Server> availableServers = serverRepository.findAvailableServers();
        if (availableServers.isEmpty()) {
            return "No available servers to stress test";
        }

        // Pick a random server to stress
        Server victim = availableServers.get(random.nextInt(availableServers.size()));
        serverService.updateMetrics(victim.getId(), 95.0, 92.0, 180, 4500.0);

        log.info("Stress test applied to server: {}", victim.getName());
        return String.format("Stress test applied to %s: CPU=95%%, RAM=92%%, Requests=180, RT=4500ms. "
            + "Fuzzy logic should now route away from this server.", victim.getName());
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    /**
     * evolveMetric() — Random walk for a single metric value.
     *
     * Generates a change between ±minChange and ±maxChange,
     * then clamps the result to [minVal, maxVal].
     *
     * The random walk creates realistic-looking metric evolution:
     * rather than jumping to random values each tick, values drift
     * gradually like real server metrics do.
     *
     * @param current   Current metric value
     * @param minChange Minimum change magnitude per tick
     * @param maxChange Maximum change magnitude per tick
     * @param minVal    Floor (value cannot go below this)
     * @param maxVal    Ceiling (value cannot go above this)
     * @return New metric value after random drift
     */
    private double evolveMetric(double current, double minChange, double maxChange,
                                double minVal, double maxVal) {
        // Random direction and magnitude
        double magnitude = minChange + random.nextDouble() * (maxChange - minChange);
        double change = (random.nextBoolean() ? 1 : -1) * magnitude;

        double newValue = current + change;
        return Math.max(minVal, Math.min(maxVal, newValue));
    }

    /**
     * simulateIncomingRequest() — Routes a simulated request through the load balancer.
     * Called during each simulation tick to generate decision logs.
     */
    private void simulateIncomingRequest() {
        try {
            // Route with null HttpServletRequest (simulation mode)
            loadBalancerService.routeRequest(null, "/simulation/auto-request");
        } catch (Exception e) {
            log.debug("Could not simulate request during tick: {}", e.getMessage());
        }
    }
}
