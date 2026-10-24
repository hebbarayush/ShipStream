package com.shipstream.outbox;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Claims a batch of pending events for this transaction only. {@code FOR UPDATE SKIP LOCKED}
     * means several instances can poll concurrently without blocking on, or double-processing, the
     * same rows: locks are held only until the caller's transaction commits (see
     * {@code OutboxProcessor#processBatch}).
     */
    @Query(value = """
            SELECT * FROM outbox_events
            WHERE status = 'PENDING'
            ORDER BY created_at
            FOR UPDATE SKIP LOCKED
            LIMIT :limit
            """, nativeQuery = true)
    List<OutboxEvent> claimBatch(@Param("limit") int limit);

    long countByStatus(OutboxStatus status);
}
