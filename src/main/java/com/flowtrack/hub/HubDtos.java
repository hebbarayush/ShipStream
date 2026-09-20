package com.flowtrack.hub;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class HubDtos {
    private HubDtos() {}

    public record CreateHubRequest(@NotBlank String name, @NotBlank String location, @Min(1) int capacity) {}

    public record HubResponse(Long id, String name, String location, int capacity) {
        static HubResponse from(Hub h) {
            return new HubResponse(h.getId(), h.getName(), h.getLocation(), h.getCapacity());
        }
    }

    public record CreateRouteRequest(@NotNull Long sourceHubId, @NotNull Long destinationHubId, @Min(1) int distanceKm) {}

    public record RouteResponse(Long id, Long sourceHubId, Long destinationHubId, int distanceKm) {
        static RouteResponse from(Route r) {
            return new RouteResponse(r.getId(), r.getSourceHub().getId(), r.getDestinationHub().getId(), r.getDistanceKm());
        }
    }
}
