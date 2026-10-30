package com.shipstream.demo;

import com.shipstream.hub.Hub;
import com.shipstream.hub.HubRepository;
import com.shipstream.hub.Route;
import com.shipstream.hub.RouteRepository;
import com.shipstream.shipment.Shipment;
import com.shipstream.shipment.ShipmentDtos.CreateShipmentRequest;
import com.shipstream.shipment.ShipmentDtos.StatusUpdateRequest;
import com.shipstream.shipment.ShipmentService;
import com.shipstream.shipment.ShipmentStatus;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds a small hub network (four real Indian cities, fully routed both directions) and one demo
 * shipment at a fixed, memorable tracking ID, so both a recruiter/interviewer looking at this live
 * (public tracking, no login) and anyone logged into the ops dashboard trying "create a shipment" have
 * real data to work with immediately - no "create two hubs and a route first" friction. Tracking ID is
 * hardcoded in the frontend too - see {@code PublicTrackPage.tsx}.
 *
 * <p>Idempotent (every hub/route insert is a find-or-create), and self-healing on every restart: the
 * demo shipment gets walked to IN_TRANSIT fresh if it's a new install, or nudged back from DELAYED with
 * a fresh ETA if it drifted there from sitting unattended. Anything else - including a terminal state a
 * visitor pushed it into via the real ops login - is left alone: terminal really does mean terminal,
 * demo shipment included.
 */
@Component
class DemoDataSeeder implements ApplicationRunner {

    static final String DEMO_TRACKING_ID = "FT-DEMO00001";
    private static final String ORIGIN_NAME = "Demo Origin Hub";
    private static final String DESTINATION_NAME = "Demo Destination Hub";

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    /** name -> [location, latitude, longitude]. Keep ORIGIN_NAME/DESTINATION_NAME first: the demo
     *  shipment below is wired to those two specifically, and they're what's already seeded in any
     *  existing deployment - new entries only add hubs/routes, never rename these two. */
    private static final Map<String, Object[]> HUBS = new LinkedHashMap<>();
    static {
        HUBS.put(ORIGIN_NAME, new Object[] {"Bengaluru", 12.9716, 77.5946});
        HUBS.put(DESTINATION_NAME, new Object[] {"Chennai", 13.0827, 80.2707});
        HUBS.put("Mumbai Hub", new Object[] {"Mumbai", 19.0760, 72.8777});
        HUBS.put("Delhi Hub", new Object[] {"Delhi", 28.7041, 77.1025});
    }

    /** Straight-line-ish road distances (km), one entry per unordered pair; routes are seeded both ways. */
    private static final Map<List<String>, Integer> DISTANCES_KM = Map.of(
            List.of(ORIGIN_NAME, DESTINATION_NAME), 350,
            List.of(ORIGIN_NAME, "Mumbai Hub"), 840,
            List.of(ORIGIN_NAME, "Delhi Hub"), 1740,
            List.of(DESTINATION_NAME, "Mumbai Hub"), 1030,
            List.of(DESTINATION_NAME, "Delhi Hub"), 1760,
            List.of("Mumbai Hub", "Delhi Hub"), 1150);

    private final HubRepository hubs;
    private final RouteRepository routes;
    private final ShipmentService shipmentService;

    DemoDataSeeder(HubRepository hubs, RouteRepository routes, ShipmentService shipmentService) {
        this.hubs = hubs;
        this.routes = routes;
        this.shipmentService = shipmentService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Map<String, Hub> byName = new LinkedHashMap<>();
        HUBS.forEach((name, info) -> byName.put(name, hubs.findByName(name)
                .orElseGet(() -> hubs.save(new Hub(name, (String) info[0], 500, (double) info[1], (double) info[2])))));

        int routesCreated = 0;
        for (var pairDistance : DISTANCES_KM.entrySet()) {
            Hub a = byName.get(pairDistance.getKey().get(0));
            Hub b = byName.get(pairDistance.getKey().get(1));
            int km = pairDistance.getValue();
            routesCreated += ensureRoute(a, b, km) + ensureRoute(b, a, km);
        }
        if (routesCreated > 0) {
            log.info("Seeded {} hub route(s) across {} hubs", routesCreated, HUBS.size());
        }

        Hub origin = byName.get(ORIGIN_NAME);
        Hub destination = byName.get(DESTINATION_NAME);
        Shipment shipment = shipmentService.createSeedShipment(DEMO_TRACKING_ID,
                new CreateShipmentRequest("Demo Sender, Bengaluru", "Demo Receiver, Chennai",
                        origin.getId(), destination.getId()));

        if (shipment.getStatus() == ShipmentStatus.CREATED) {
            shipmentService.transition(shipment.getId(), new StatusUpdateRequest(ShipmentStatus.PICKED_UP, null));
            shipmentService.transition(shipment.getId(), new StatusUpdateRequest(ShipmentStatus.IN_TRANSIT, null));
            log.info("Seeded demo shipment '{}', walked to IN_TRANSIT", DEMO_TRACKING_ID);
        } else if (shipment.getStatus() == ShipmentStatus.DELAYED) {
            shipmentService.transition(shipment.getId(), new StatusUpdateRequest(ShipmentStatus.IN_TRANSIT, null));
            log.info("Demo shipment '{}' had drifted into DELAYED; resumed with a fresh ETA", DEMO_TRACKING_ID);
        }
    }

    private int ensureRoute(Hub source, Hub destination, int distanceKm) {
        if (routes.findBySourceHubIdAndDestinationHubId(source.getId(), destination.getId()).isPresent()) {
            return 0;
        }
        routes.save(new Route(source, destination, distanceKm));
        return 1;
    }
}
