package com.fuzzybalancer.monitoring.repository;

import com.fuzzybalancer.monitoring.entity.RequestLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface RequestLogRepository extends JpaRepository<RequestLog, Long> {

    Page<RequestLog> findByHandledByServerId(Long serverId, Pageable pageable);

    Page<RequestLog> findBySuccess(Boolean success, Pageable pageable);

    @Query("SELECT AVG(r.responseTimeMs) FROM RequestLog r")
    Double findAverageResponseTime();

    @Query("SELECT AVG(r.responseTimeMs) FROM RequestLog r WHERE r.handledByServer.id = :serverId")
    Double findAverageResponseTimeByServer(@Param("serverId") Long serverId);

    @Query("SELECT COUNT(r) FROM RequestLog r WHERE r.requestTimestamp >= :since")
    Long countRequestsSince(@Param("since") LocalDateTime since);

    @Query("SELECT COUNT(r) FROM RequestLog r WHERE r.success = true")
    Long countSuccessfulRequests();

    @Query("SELECT COUNT(r) FROM RequestLog r WHERE r.success = false")
    Long countFailedRequests();

    @Query("""
        SELECT r FROM RequestLog r
        WHERE r.requestTimestamp BETWEEN :from AND :to
        ORDER BY r.requestTimestamp DESC
        """)
    Page<RequestLog> findByTimeRange(
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to,
        Pageable pageable
    );
}
