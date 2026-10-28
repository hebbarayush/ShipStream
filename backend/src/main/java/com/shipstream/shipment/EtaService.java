package com.shipstream.shipment;

import com.shipstream.hub.Hub;
import com.shipstream.hub.RouteRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Deliberately basic, per the project's non-goals (no route optimization): a straight distance/speed
 * estimate off a direct {@code routes} row between the shipment's current and destination hub. No
 * multi-hop path-finding - if there's no direct route, there's no ETA, and that's fine for a demo.
 */
@Component
class EtaService {

    /** Assumed average ground-transport speed; good enough for a believable, if rough, ETA. */
    private static final double ASSUMED_SPEED_KMH = 50.0;

    private final RouteRepository routes;

    EtaService(RouteRepository routes) {
        this.routes = routes;
    }

    Optional<Instant> estimate(Hub from, Hub to) {
        if (from == null || to == null || from.getId().equals(to.getId())) {
            return Optional.empty();
        }
        return routes.findBySourceHubIdAndDestinationHubId(from.getId(), to.getId())
                .map(route -> Instant.now().plus(Duration.ofMinutes(
                        Math.round(route.getDistanceKm() / ASSUMED_SPEED_KMH * 60))));
    }
}
