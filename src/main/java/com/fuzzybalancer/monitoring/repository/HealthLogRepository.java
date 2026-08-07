package com.fuzzybalancer.monitoring.repository;

import com.fuzzybalancer.monitoring.entity.HealthLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface HealthLogRepository extends JpaRepository<HealthLog, Long> {

    Page<HealthLog> findByServerId(Long serverId, Pageable pageable);

    List<HealthLog> findByServerIdOrderByLogTimestampDesc(Long serverId);

    @Query("SELECT h FROM HealthLog h WHERE h.logTimestamp BETWEEN :from AND :to ORDER BY h.logTimestamp DESC")
    List<HealthLog> findByTimeRange(
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to
    );

    long countByServerIdAndNewStatus(Long serverId, com.fuzzybalancer.server.entity.Server.HealthStatus status);
}
