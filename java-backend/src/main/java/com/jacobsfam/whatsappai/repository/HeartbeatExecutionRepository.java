package com.jacobsfam.whatsappai.repository;

import com.jacobsfam.whatsappai.model.entity.HeartbeatExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for heartbeat execution records.
 */
@Repository
public interface HeartbeatExecutionRepository extends JpaRepository<HeartbeatExecution, Long> {

    /**
     * Find the most recent execution for a given task.
     * Used to determine if a task is due to run again.
     */
    Optional<HeartbeatExecution> findTopByTaskNameOrderByExecutedAtDesc(String taskName);

    /**
     * Find recent executions for a task (for deduplication/pattern detection).
     */
    List<HeartbeatExecution> findTop10ByTaskNameOrderByExecutedAtDesc(String taskName);

    /**
     * Find executions within a time range (for metrics/reporting).
     */
    List<HeartbeatExecution> findByExecutedAtBetween(LocalDateTime start, LocalDateTime end);

    /**
     * Count consecutive failures for a task (for escalation logic).
     */
    @Query("""
        SELECT COUNT(e) FROM HeartbeatExecution e
        WHERE e.taskName = ?1
        AND e.result = 'ERROR'
        AND e.executedAt > ?2
        ORDER BY e.executedAt DESC
        """)
    long countRecentFailures(String taskName, LocalDateTime since);

    /**
     * Find all error executions for monitoring.
     */
    List<HeartbeatExecution> findByResultOrderByExecutedAtDesc(String result);
}
