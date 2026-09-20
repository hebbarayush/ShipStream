package com.flowtrack.hub;

import com.flowtrack.common.NotFoundException;
import com.flowtrack.hub.HubDtos.*;
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
        return HubResponse.from(hubs.save(new Hub(req.name(), req.location(), req.capacity())));
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

    private Hub hub(Long id) {
        return hubs.findById(id).orElseThrow(() -> new NotFoundException("Hub " + id + " not found"));
    }
}
