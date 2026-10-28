package com.shipstream.shipment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;

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

    public record PagedResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {
        static PagedResponse<ShipmentResponse> from(Page<Shipment> page) {
            return new PagedResponse<>(page.getContent().stream().map(ShipmentResponse::from).toList(),
                    page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
        }
    }

    public record ShipmentEventResponse(String eventType, Map<String, Object> payload, Instant createdAt) {
        static ShipmentEventResponse from(ShipmentEvent e, Map<String, Object> payload) {
            return new ShipmentEventResponse(e.getEventType(), payload, e.getCreatedAt());
        }
    }
}
