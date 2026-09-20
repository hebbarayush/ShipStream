package com.shipstream.shipment;

import com.shipstream.shipment.ShipmentDtos.CreateShipmentRequest;
import com.shipstream.shipment.ShipmentDtos.ShipmentResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/shipments")
class ShipmentController {

    private final ShipmentService service;

    ShipmentController(ShipmentService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<ShipmentResponse> create(@Valid @RequestBody CreateShipmentRequest req) {
        ShipmentResponse body = ShipmentResponse.from(service.create(req));
        return ResponseEntity.created(URI.create("/api/v1/shipments/" + body.id())).body(body);
    }

    @GetMapping("/{id}")
    ShipmentResponse get(@PathVariable Long id) {
        return ShipmentResponse.from(service.get(id));
    }

    @GetMapping("/track/{trackingId}")
    ShipmentResponse track(@PathVariable String trackingId) {
        return ShipmentResponse.from(service.getByTrackingId(trackingId));
    }
}
