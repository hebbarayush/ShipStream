package com.shipstream.shipment;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

public final class ShipmentDtos {
    private ShipmentDtos() {}

    public record CreateShipmentRequest(
            @NotBlank String senderAddress,
            @NotBlank String receiverAddress,
            Long originHubId) {}

    public record ShipmentResponse(
            Long id,
            String trackingId,
            String senderAddress,
            String receiverAddress,
            ShipmentStatus status,
            Long currentHubId,
            Instant createdAt,
            Instant updatedAt,
            long version) {

        static ShipmentResponse from(Shipment s) {
            return new ShipmentResponse(s.getId(), s.getTrackingId(), s.getSenderAddress(), s.getReceiverAddress(),
                    s.getStatus(), s.getCurrentHub() == null ? null : s.getCurrentHub().getId(),
                    s.getCreatedAt(), s.getUpdatedAt(), s.getVersion());
        }
    }
}
