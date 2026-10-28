package com.shipstream.shipment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public final class ShipmentDtos {
    private ShipmentDtos() {}

    public record CreateShipmentRequest(
            @NotBlank String senderAddress,
            @NotBlank String receiverAddress,
            Long originHubId,
            Long destinationHubId) {}

    public record StatusUpdateRequest(@NotNull ShipmentStatus status, Long hubId) {}

    public record ShipmentResponse(
            Long id,
            String trackingId,
            String senderAddress,
            String receiverAddress,
            ShipmentStatus status,
            Long currentHubId,
            Long destinationHubId,
            Instant estimatedDeliveryAt,
            Instant createdAt,
            Instant updatedAt,
            long version) {

        static ShipmentResponse from(Shipment s) {
            return new ShipmentResponse(s.getId(), s.getTrackingId(), s.getSenderAddress(), s.getReceiverAddress(),
                    s.getStatus(), s.getCurrentHub() == null ? null : s.getCurrentHub().getId(),
                    s.getDestinationHub() == null ? null : s.getDestinationHub().getId(),
                    s.getEstimatedDeliveryAt(), s.getCreatedAt(), s.getUpdatedAt(), s.getVersion());
        }
    }
}
