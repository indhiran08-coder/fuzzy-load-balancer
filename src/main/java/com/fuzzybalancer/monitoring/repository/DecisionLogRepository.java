package com.fuzzybalancer.monitoring.repository;

import com.fuzzybalancer.monitoring.entity.DecisionLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DecisionLogRepository — Queries for load balancer decision history.
 *
 * Powers:
 *   - Decision history view (paginated)
 *   - Dashboard: most selected server
 *   - Dashboard: average fuzzy score over time
 *   - Dashboard: load distribution per server
 */
@Repository
public interface DecisionLogRepository extends JpaRepository<DecisionLog, Long> {

    Page<DecisionLog> findBySelectedServerId(Long serverId, Pageable pageable);

    /**
     * findMostSelectedServer() — Dashboard: which server was chosen most often?
     *
     * Returns a list of Object[] where:
     *   [0] = server name (String)
     *   [1] = selection count (Long)
     *
     * Ordered by count DESC so the first result is the most selected.
     */
    @Query("""
        SELECT s.name, COUNT(d) as cnt
        FROM DecisionLog d JOIN d.selectedServer s
        GROUP BY s.name
        ORDER BY cnt DESC
        """)
    List<Object[]> findSelectionCountPerServer();

    /**
     * findAverageWinningScore() — Dashboard: mean fuzzy score across all decisions.
     */
    @Query("SELECT AVG(d.winningScore) FROM DecisionLog d")
    Double findAverageWinningScore();

    /**
     * countDecisionsSince() — Dashboard: how many requests in the last N minutes?
     */
    @Query("SELECT COUNT(d) FROM DecisionLog d WHERE d.decisionTimestamp >= :since")
    Long countDecisionsSince(@Param("since") LocalDateTime since);

    /**
     * findByTimeRange() — Filtering decisions by time window.
     */
    @Query("SELECT d FROM DecisionLog d WHERE d.decisionTimestamp BETWEEN :from AND :to ORDER BY d.decisionTimestamp DESC")
    Page<DecisionLog> findByTimeRange(
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to,
        Pageable pageable
    );

    /** Total decision count for a specific server. */
    long countBySelectedServerId(Long serverId);
}
