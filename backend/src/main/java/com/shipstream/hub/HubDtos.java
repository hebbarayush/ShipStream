package com.shipstream.hub;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class HubDtos {
    private HubDtos() {}

    public record CreateHubRequest(
            @NotBlank String name,
            @NotBlank String location,
            @Min(1) int capacity,
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude) {}

    public record HubResponse(Long id, String name, String location, int capacity, double latitude, double longitude) {
        static HubResponse from(Hub h) {
            return new HubResponse(h.getId(), h.getName(), h.getLocation(), h.getCapacity(),
                    h.getLatitude(), h.getLongitude());
        }
    }

    public record CreateRouteRequest(@NotNull Long sourceHubId, @NotNull Long destinationHubId, @Min(1) int distanceKm) {}

    public record RouteResponse(Long id, Long sourceHubId, Long destinationHubId, int distanceKm) {
        static RouteResponse from(Route r) {
            return new RouteResponse(r.getId(), r.getSourceHub().getId(), r.getDestinationHub().getId(), r.getDistanceKm());
        }
    }
}
