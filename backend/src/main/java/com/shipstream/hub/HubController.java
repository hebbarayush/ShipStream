package com.shipstream.hub;

import com.shipstream.common.NotFoundException;
import com.shipstream.hub.HubDtos.*;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
class HubController {

    private final HubRepository hubs;
    private final RouteRepository routes;

    HubController(HubRepository hubs, RouteRepository routes) {
        this.hubs = hubs;
        this.routes = routes;
    }

    @PostMapping("/hubs")
    @ResponseStatus(HttpStatus.CREATED)
    HubResponse createHub(@Valid @RequestBody CreateHubRequest req) {
        return HubResponse.from(hubs.save(
                new Hub(req.name(), req.location(), req.capacity(), req.latitude(), req.longitude())));
    }

    @GetMapping("/hubs")
    List<HubResponse> listHubs() {
        return hubs.findAll().stream().map(HubResponse::from).toList();
    }

    @PostMapping("/routes")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    RouteResponse createRoute(@Valid @RequestBody CreateRouteRequest req) {
        Hub src = hub(req.sourceHubId());
        Hub dst = hub(req.destinationHubId());
        return RouteResponse.from(routes.save(new Route(src, dst, req.distanceKm())));
    }

    @GetMapping("/routes")
    @Transactional(readOnly = true)
    List<RouteResponse> listRoutes() {
        return routes.findAll().stream().map(RouteResponse::from).toList();
    }

    /**
     * Deletes the hub's routes (either direction) first, then the hub itself. A hub still referenced
     * by a shipment (current or destination) is left alone - the FK violation surfaces as a 409 via
     * {@code ApiExceptionHandler}, same as any other data-integrity conflict, rather than silently
     * orphaning a shipment's hub reference.
     */
    @DeleteMapping("/hubs/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    void deleteHub(@PathVariable Long id) {
        Hub existing = hub(id);
        routes.deleteBySourceHubIdOrDestinationHubId(id, id);
        hubs.delete(existing);
    }

    @DeleteMapping("/routes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteRoute(@PathVariable Long id) {
        if (!routes.existsById(id)) {
            throw new NotFoundException("Route " + id + " not found");
        }
        routes.deleteById(id);
    }

    private Hub hub(Long id) {
        return hubs.findById(id).orElseThrow(() -> new NotFoundException("Hub " + id + " not found"));
    }
}
