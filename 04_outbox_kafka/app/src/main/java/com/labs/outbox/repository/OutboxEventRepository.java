package com.labs.outbox.repository;

import com.labs.outbox.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Returns events eligible for publishing:
     * - PENDING: never attempted yet.
     * - FAILED: eligible for retry (retry_count < maxRetries AND next_retry_at has elapsed).
     *
     * Exponential backoff is enforced via next_retry_at, set by the publisher on each failure:
     * next_retry_at = now + min(2^(retryCount-1), 60) seconds.
     */
    @Query("""
        SELECT e FROM OutboxEvent e
        WHERE e.status = 'PENDING'
           OR (e.status = 'FAILED'
               AND e.retryCount < :maxRetries
               AND (e.nextRetryAt IS NULL OR e.nextRetryAt <= CURRENT_TIMESTAMP))
        ORDER BY e.createdAt ASC
        LIMIT 100
        """)
    List<OutboxEvent> findRetryableEvents(@Param("maxRetries") int maxRetries);

    long countByStatus(OutboxEvent.EventStatus status);
}
