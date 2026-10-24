package com.shipstream.tracking;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public final class TrackingDtos {
    private TrackingDtos() {}

    public record UpdateLocationRequest(
            @NotNull Long shipmentId,
            @NotNull Double latitude,
            @NotNull Double longitude) {}

    public record TrackingResponse(Long shipmentId, double latitude, double longitude, Instant lastUpdated) {
        static TrackingResponse from(ShipmentTracking t) {
            return new TrackingResponse(t.getShipmentId(), t.getLatitude(), t.getLongitude(), t.getLastUpdated());
        }
    }
}
