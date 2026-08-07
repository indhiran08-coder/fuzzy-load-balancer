package com.fuzzybalancer.config;

import com.fuzzybalancer.auth.entity.Role;
import com.fuzzybalancer.auth.entity.User;
import com.fuzzybalancer.auth.repository.RoleRepository;
import com.fuzzybalancer.auth.repository.UserRepository;
import com.fuzzybalancer.server.entity.Server;
import com.fuzzybalancer.server.repository.ServerRepository;
import com.fuzzybalancer.simulation.service.SimulationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * DataInitializer — Seeds the database with required startup data.
 *
 * Implements ApplicationRunner — Spring calls run() after the application
 * context is fully loaded and ready, but before it starts accepting requests.
 * This is the correct hook for DB seeding (not @PostConstruct, which runs
 * before security/JPA are fully wired).
 *
 * Seeded data:
 *   1. Roles: ROLE_ADMIN, ROLE_USER (required for auth to work)
 *   2. Admin user: admin/admin123 (for initial access)
 *   3. Demo servers: Server-A, Server-B, Server-C (for simulation)
 *
 * Idempotent:
 *   All seed operations check existsByX() before inserting.
 *   Running the app multiple times will NOT create duplicate data.
 *
 * @Component — Makes this a Spring-managed bean.
 *   Spring auto-detects ApplicationRunner implementations and invokes them.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements ApplicationRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final ServerRepository serverRepository;
    private final PasswordEncoder passwordEncoder;
    private final SimulationService simulationService;

    /**
     * run() — Entry point called by Spring after application startup.
     *
     * The @Transactional annotation ensures all seed operations are atomic.
     * If any seed fails (e.g., DB connection error), the whole transaction
     * rolls back and the app fails cleanly.
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        log.info("=== DataInitializer: Seeding database ===");

        seedRoles();
        seedAdminUser();
        seedDemoServers();
        simulationService.initialize();

        log.info("=== DataInitializer: Complete ===");
        log.info(">>> Swagger UI: http://localhost:8080/swagger-ui.html");
        log.info(">>> Default admin: username=admin, password=admin123");
        log.info(">>> Default user:  username=user,  password=user123");
    }

    // =========================================================================
    // ROLE SEEDING
    // =========================================================================

    /**
     * seedRoles() — Creates ROLE_ADMIN and ROLE_USER if they don't exist.
     *
     * These roles MUST exist before any user can be created or logged in.
     * Without them, the registration endpoint would throw an exception
     * when trying to assign the default role.
     */
    private void seedRoles() {
        if (!roleRepository.existsById(1L)) {
            Role adminRole = Role.builder()
                .name(Role.RoleName.ROLE_ADMIN)
                .description("Full system access — can manage servers, users, and configuration")
                .build();
            roleRepository.save(adminRole);
            log.info("Created ROLE_ADMIN");
        }

        if (roleRepository.findByName(Role.RoleName.ROLE_USER).isEmpty()) {
            Role userRole = Role.builder()
                .name(Role.RoleName.ROLE_USER)
                .description("Standard access — can view servers, trigger requests, view monitoring")
                .build();
            roleRepository.save(userRole);
            log.info("Created ROLE_USER");
        }
    }

    // =========================================================================
    // DEFAULT USER SEEDING
    // =========================================================================

    /**
     * seedAdminUser() — Creates default admin and regular user accounts.
     *
     * These accounts allow immediate testing after startup without
     * needing to call /api/auth/register first.
     *
     * DEFAULT CREDENTIALS (change in production!):
     *   Admin: username=admin, password=admin123
     *   User:  username=user,  password=user123
     */
    private void seedAdminUser() {
        // Seed admin
        if (!userRepository.existsByUsername("admin")) {
            Role adminRole = roleRepository.findByName(Role.RoleName.ROLE_ADMIN)
                .orElseThrow(() -> new RuntimeException("ROLE_ADMIN not found"));
            Role userRole = roleRepository.findByName(Role.RoleName.ROLE_USER)
                .orElseThrow(() -> new RuntimeException("ROLE_USER not found"));

            User admin = User.builder()
                .username("admin")
                .email("admin@fuzzybalancer.com")
                .password(passwordEncoder.encode("admin123"))
                .roles(Set.of(adminRole, userRole)) // Admin has both roles
                .enabled(true)
                .build();
            userRepository.save(admin);
            log.info("Created default admin user (username=admin, password=admin123)");
        }

        // Seed regular user
        if (!userRepository.existsByUsername("user")) {
            Role userRole = roleRepository.findByName(Role.RoleName.ROLE_USER)
                .orElseThrow(() -> new RuntimeException("ROLE_USER not found"));

            User regularUser = User.builder()
                .username("user")
                .email("user@fuzzybalancer.com")
                .password(passwordEncoder.encode("user123"))
                .roles(Set.of(userRole))
                .enabled(true)
                .build();
            userRepository.save(regularUser);
            log.info("Created default regular user (username=user, password=user123)");
        }
    }

    // =========================================================================
    // DEMO SERVER SEEDING
    // =========================================================================

    /**
     * seedDemoServers() — Creates three simulated backend servers.
     *
     * The three servers are given different initial metrics to demonstrate
     * that the fuzzy engine immediately differentiates between them.
     *
     * Initial states:
     *   Server-A: Light load (ideal candidate)
     *   Server-B: Medium load (acceptable)
     *   Server-C: Medium-high load (less preferred)
     */
    private void seedDemoServers() {
        if (!serverRepository.existsByName("Server-A")) {
            serverRepository.save(Server.builder()
                .name("Server-A")
                .address("192.168.1.10")
                .port(8081)
                .description("Primary server — US East region. High-performance compute node.")
                .cpuUsage(15.0)
                .ramUsage(20.0)
                .activeRequests(5)
                .responseTime(85.0)
                .healthStatus(Server.HealthStatus.HEALTHY)
                .totalRequestsServed(0L)
                .build());
            log.info("Created Server-A (light load)");
        }

        if (!serverRepository.existsByName("Server-B")) {
            serverRepository.save(Server.builder()
                .name("Server-B")
                .address("192.168.1.11")
                .port(8082)
                .description("Secondary server — US West region. Standard compute node.")
                .cpuUsage(45.0)
                .ramUsage(55.0)
                .activeRequests(30)
                .responseTime(320.0)
                .healthStatus(Server.HealthStatus.HEALTHY)
                .totalRequestsServed(0L)
                .build());
            log.info("Created Server-B (medium load)");
        }

        if (!serverRepository.existsByName("Server-C")) {
            serverRepository.save(Server.builder()
                .name("Server-C")
                .address("192.168.1.12")
                .port(8083)
                .description("Tertiary server — EU West region. Economy compute node.")
                .cpuUsage(68.0)
                .ramUsage(72.0)
                .activeRequests(65)
                .responseTime(750.0)
                .healthStatus(Server.HealthStatus.HEALTHY)
                .totalRequestsServed(0L)
                .build());
            log.info("Created Server-C (medium-high load)");
        }
    }
}
