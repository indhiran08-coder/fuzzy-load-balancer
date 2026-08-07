package com.fuzzybalancer.server.repository;

import com.fuzzybalancer.server.entity.Server;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * ServerRepository — Spring Data JPA repository for Server entities.
 *
 * Provides CRUD operations plus custom queries for:
 *   - Filtering servers by health status
 *   - Finding available servers for load balancing
 *   - Updating metrics directly via @Modifying queries (avoids fetching the entity)
 *   - Dashboard aggregations (count by status)
 */
@Repository
public interface ServerRepository extends JpaRepository<Server, Long> {

    /** Find a server by its unique name. */
    Optional<Server> findByName(String name);

    /** Check if a server name already exists (for duplicate prevention). */
    boolean existsByName(String name);

    /**
     * findByHealthStatus() — Returns all servers with a given health status.
     * Used to list only HEALTHY servers for routing.
     */
    List<Server> findByHealthStatus(Server.HealthStatus healthStatus);

    /**
     * findAvailableServers() — Returns servers that can accept new requests.
     * "Available" means HEALTHY or DEGRADED (not UNHEALTHY or OFFLINE).
     *
     * This is the query called by the load balancer before fuzzy evaluation.
     * Only available servers are candidates for routing.
     *
     * @Query — Custom JPQL query for multi-value IN clause.
     */
    @Query("SELECT s FROM Server s WHERE s.healthStatus IN ('HEALTHY', 'DEGRADED') ORDER BY s.name")
    List<Server> findAvailableServers();

    /**
     * findAllWithPagination() — Returns servers with pagination support.
     * Pageable is automatically handled by Spring Data — no SQL LIMIT needed.
     */
    Page<Server> findAll(Pageable pageable);

    /**
     * countByHealthStatus() — Aggregation for dashboard.
     * Returns the count of servers in each health state.
     */
    long countByHealthStatus(Server.HealthStatus healthStatus);

    /**
     * updateMetrics() — Directly updates metrics without loading the entity.
     *
     * @Modifying — Required for UPDATE/DELETE queries. Without this,
     *   Spring Data treats @Query as a SELECT.
     *   clearAutomatically = true clears the 1st-level cache after the update,
     *   ensuring subsequent findById() calls see the new values.
     *
     * Why use this instead of loading and saving?
     *   For high-frequency metric updates (every 5 seconds per server),
     *   loading the full entity and saving it back creates 2 DB round-trips.
     *   A direct UPDATE query is 1 round-trip. At scale this matters.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE Server s SET
            s.cpuUsage = :cpu,
            s.ramUsage = :ram,
            s.activeRequests = :requests,
            s.responseTime = :responseTime,
            s.lastUpdated = CURRENT_TIMESTAMP
        WHERE s.id = :id
        """)
    int updateMetrics(
        @Param("id") Long id,
        @Param("cpu") Double cpu,
        @Param("ram") Double ram,
        @Param("requests") Integer requests,
        @Param("responseTime") Double responseTime
    );

    /**
     * updateHealthStatus() — Updates only the health status field.
     * Used by the simulation module's health monitor.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Server s SET s.healthStatus = :status, s.lastUpdated = CURRENT_TIMESTAMP WHERE s.id = :id")
    int updateHealthStatus(@Param("id") Long id, @Param("status") Server.HealthStatus status);

    /**
     * incrementRequestCount() — Atomically increments request counters.
     * Called when a server is selected by the load balancer.
     *
     * Using a direct UPDATE is critical here — avoids race conditions
     * that would occur if we loaded and incremented in Java.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE Server s SET
            s.totalRequestsServed = s.totalRequestsServed + 1,
            s.activeRequests = s.activeRequests + 1,
            s.lastUpdated = CURRENT_TIMESTAMP
        WHERE s.id = :id
        """)
    int incrementRequestCount(@Param("id") Long id);
}
