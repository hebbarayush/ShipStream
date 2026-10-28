package com.shipstream.outbox;

import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Call from inside an existing {@code @Transactional} method. Propagation is REQUIRED (the default),
 * so the outbox row commits atomically with whatever business change triggered it.
 */
@Component
public class OutboxWriter {

    private final OutboxEventRepository repo;
    private final ObjectMapper mapper;

    OutboxWriter(OutboxEventRepository repo, ObjectMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    public void write(String eventType, Map<String, Object> payload) {
        try {
            repo.save(new OutboxEvent(eventType, mapper.writeValueAsString(payload)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize outbox payload for " + eventType, e);
        }
    }
}
