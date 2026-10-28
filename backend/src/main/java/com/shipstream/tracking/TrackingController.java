package com.shipstream.tracking;

import com.shipstream.tracking.TrackingDtos.TrackingResponse;
import com.shipstream.tracking.TrackingDtos.UpdateLocationRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tracking")
class TrackingController {

    private final TrackingService service;

    TrackingController(TrackingService service) {
        this.service = service;
    }

    @PostMapping("/update")
    TrackingResponse update(@Valid @RequestBody UpdateLocationRequest req) {
        return TrackingResponse.from(service.updateLocation(req));
    }

    @GetMapping("/{shipmentId}")
    TrackingResponse get(@PathVariable Long shipmentId) {
        return TrackingResponse.from(service.get(shipmentId));
    }
}
