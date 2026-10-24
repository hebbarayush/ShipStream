package com.shipstream.shipment;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Permanent, append-only audit trail of everything that happened to a shipment.
 * Distinct from {@code outbox_events} (Phase 4), which is a transient delivery queue.
 */
@Entity
@Table(name = "shipment_events")
@Getter
@NoArgsConstructor
public class ShipmentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shipment_id", nullable = false)
    private Long shipmentId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ShipmentEvent(Long shipmentId, String eventType, String payload) {
        this.shipmentId = shipmentId;
        this.eventType = eventType;
        this.payload = payload;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
