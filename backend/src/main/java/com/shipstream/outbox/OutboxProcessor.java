package com.shipstream.outbox;

import com.shipstream.realtime.NotifyPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Polls {@code outbox_events} and fans each one out via Postgres NOTIFY. Runs on every instance;
 * {@code FOR UPDATE SKIP LOCKED} in {@link OutboxEventRepository#claimBatch} is what lets them share
 * the queue safely instead of double-processing the same row.
 */
@Component
class OutboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(OutboxProcessor.class);
    private static final int BATCH_SIZE = 20;
    private static final int MAX_RETRIES = 5;

    private final OutboxEventRepository repo;
    private final NotifyPublisher notifier;

    OutboxProcessor(OutboxEventRepository repo, NotifyPublisher notifier) {
        this.repo = repo;
        this.notifier = notifier;
    }

    // @Transactional goes directly on the @Scheduled method: the scheduler calls this bean through
    // its Spring AOP proxy, so the transaction advice applies. Splitting this into a plain poll()
    // that internally calls a separate @Transactional processBatch() would be a self-invocation bug
    // -- that inner call bypasses the proxy, so claimed rows would never actually get flushed back.
    @Scheduled(fixedDelay = 2000)
    @Transactional
    void poll() {
        for (OutboxEvent event : repo.claimBatch(BATCH_SIZE)) {
            try {
                notifier.publish(event.getPayload());
                event.markProcessed();
            } catch (Exception e) {
                log.warn("Outbox event {} ({}) failed (attempt {})", event.getId(), event.getEventType(),
                        event.getRetryCount() + 1, e);
                event.markFailed(MAX_RETRIES);
            }
        }
    }
}
