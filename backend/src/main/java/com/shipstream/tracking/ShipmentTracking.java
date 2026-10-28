package com.shipstream.tracking;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Latest known position for a shipment. History-light by design: one row per shipment, overwritten. */
@Entity
@Table(name = "shipment_tracking")
@Getter
@NoArgsConstructor
public class ShipmentTracking {

    @Id
    @Column(name = "shipment_id")
    private Long shipmentId;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(name = "last_updated", nullable = false)
    private Instant lastUpdated;

    public ShipmentTracking(Long shipmentId, double latitude, double longitude) {
        this.shipmentId = shipmentId;
        update(latitude, longitude);
    }

    public void update(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.lastUpdated = Instant.now();
    }
}
