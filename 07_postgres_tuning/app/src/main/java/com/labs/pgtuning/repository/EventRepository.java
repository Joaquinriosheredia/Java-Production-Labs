package com.labs.pgtuning.repository;

import com.labs.pgtuning.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface EventRepository extends JpaRepository<Event, UUID> {

    // Slow query: full table scan without index
    @Query(value = "SELECT * FROM events WHERE status = 'PENDING' ORDER BY occurred_at ASC LIMIT :limit", nativeQuery = true)
    List<Event> findPendingEventsNoIndex(int limit);

    // Fast query: uses partial index on status='PENDING'
    @Query(value = "SELECT * FROM events WHERE status = 'PENDING' ORDER BY occurred_at ASC LIMIT :limit /*+ IndexScan(events idx_events_pending) */", nativeQuery = true)
    List<Event> findPendingEventsWithIndex(int limit);

    // For seeding data
    long countByStatus(Event.EventStatus status);
}
