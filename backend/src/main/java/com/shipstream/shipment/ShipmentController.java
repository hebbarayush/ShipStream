package com.shipstream.shipment;

import com.shipstream.shipment.ShipmentDtos.CreateShipmentRequest;
import com.shipstream.shipment.ShipmentDtos.PagedResponse;
import com.shipstream.shipment.ShipmentDtos.ShipmentEventResponse;
import com.shipstream.shipment.ShipmentDtos.ShipmentResponse;
import com.shipstream.shipment.ShipmentDtos.StatusUpdateRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/v1/shipments")
class ShipmentController {

    private final ShipmentService service;
    private final ObjectMapper objectMapper;

    ShipmentController(ShipmentService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    ResponseEntity<ShipmentResponse> create(@Valid @RequestBody CreateShipmentRequest req) {
        ShipmentResponse body = ShipmentResponse.from(service.create(req));
        return ResponseEntity.created(URI.create("/api/v1/shipments/" + body.id())).body(body);
    }

    @GetMapping
    PagedResponse<ShipmentResponse> list(
            @RequestParam(required = false) ShipmentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        return PagedResponse.from(service.list(status, pageable));
    }

    @GetMapping("/{id}")
    ShipmentResponse get(@PathVariable Long id) {
        return ShipmentResponse.from(service.get(id));
    }

    @GetMapping("/track/{trackingId}")
    ShipmentResponse track(@PathVariable String trackingId) {
        return ShipmentResponse.from(service.getByTrackingId(trackingId));
    }

    @PostMapping("/{id}/status")
    ShipmentResponse updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest req) {
        return ShipmentResponse.from(service.transition(id, req));
    }

    @GetMapping("/{id}/events")
    List<ShipmentEventResponse> events(@PathVariable Long id) {
        return service.events(id).stream()
                .map(e -> ShipmentEventResponse.from(e, objectMapper.readValue(e.getPayload(), Map.class)))
                .toList();
    }

    /** Admin cleanup - see {@code ShipmentService.deleteShipment}. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable Long id) {
        service.deleteShipment(id);
    }
}
