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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds one demo shipment with a fixed, memorable tracking ID so the public tracking page (and a
 * recruiter/interviewer looking at this live) has something real to search for without creating a
 * login first. Hardcoded in the frontend too - see {@code PublicTrackPage.tsx}.
 *
 * <p>Idempotent, and self-healing on every restart: a fresh install gets the shipment walked to
 * IN_TRANSIT so there's a live ETA and a moving dot to look at; one that's drifted into DELAYED (the
 * delay-detection job caught it while nobody was watching) gets nudged back with a fresh ETA. Anything
 * else - including a terminal state a visitor pushed it into by using the real ops login - is left
 * alone: terminal really does mean terminal, demo shipment included.
 */
@Component
class DemoDataSeeder implements ApplicationRunner {

    static final String DEMO_TRACKING_ID = "FT-DEMO00001";
    private static final String ORIGIN_NAME = "Demo Origin Hub";
    private static final String DESTINATION_NAME = "Demo Destination Hub";

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

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
        Hub origin = hubs.findByName(ORIGIN_NAME)
                .orElseGet(() -> hubs.save(new Hub(ORIGIN_NAME, "Bengaluru", 500, 12.9716, 77.5946)));
        Hub destination = hubs.findByName(DESTINATION_NAME)
                .orElseGet(() -> hubs.save(new Hub(DESTINATION_NAME, "Chennai", 500, 13.0827, 80.2707)));
        routes.findBySourceHubIdAndDestinationHubId(origin.getId(), destination.getId())
                .orElseGet(() -> routes.save(new Route(origin, destination, 350)));

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
}
