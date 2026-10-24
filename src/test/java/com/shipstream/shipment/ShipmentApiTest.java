package com.shipstream.shipment;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.shipstream.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ShipmentApiTest {

    @Autowired MockMvc mvc;

    @Test
    void createsShipmentAndFetchesItByIdAndTrackingId() throws Exception {
        String body = """
                {"senderAddress":"12 MG Road, Bengaluru","receiverAddress":"5 Park St, Kolkata"}""";

        String location = mvc.perform(post("/api/v1/shipments").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.trackingId", startsWith("FT-")))
                .andReturn().getResponse().getHeader("Location");

        mvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.senderAddress").value("12 MG Road, Bengaluru"));
    }

    @Test
    void rejectsBlankAddresses() throws Exception {
        mvc.perform(post("/api/v1/shipments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senderAddress\":\"\",\"receiverAddress\":\"x\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownShipmentIs404() throws Exception {
        mvc.perform(get("/api/v1/shipments/track/FT-NOPE")).andExpect(status().isNotFound());
    }

    @Test
    void hubsAndRoutesCanBeCreatedAndListed() throws Exception {
        String a = mvc.perform(post("/api/v1/hubs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"BLR-1\",\"location\":\"Bengaluru\",\"capacity\":500}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String b = mvc.perform(post("/api/v1/hubs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"CCU-1\",\"location\":\"Kolkata\",\"capacity\":400}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long idA = Long.parseLong(a.replaceAll(".*\"id\":(\\d+).*", "$1"));
        long idB = Long.parseLong(b.replaceAll(".*\"id\":(\\d+).*", "$1"));

        mvc.perform(post("/api/v1/routes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceHubId\":%d,\"destinationHubId\":%d,\"distanceKm\":1870}".formatted(idA, idB)))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/v1/routes")).andExpect(status().isOk()).andExpect(jsonPath("$[0].distanceKm").value(1870));
    }

    @Test
    void transitionsThroughValidStatusesAndRejectsSkippingStages() throws Exception {
        Long id = createShipment();

        mvc.perform(post("/api/v1/shipments/" + id + "/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_TRANSIT\"}"))
                .andExpect(status().isConflict()); // CREATED -> IN_TRANSIT skips PICKED_UP

        mvc.perform(post("/api/v1/shipments/" + id + "/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PICKED_UP\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PICKED_UP"))
                .andExpect(jsonPath("$.version").value(1));

        mvc.perform(post("/api/v1/shipments/" + id + "/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_TRANSIT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_TRANSIT"));
    }

    @Test
    void concurrentTransitionsOnSameShipmentLeaveOneLoserWithAConflict() throws Exception {
        Long id = createShipment();
        mvc.perform(post("/api/v1/shipments/" + id + "/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PICKED_UP\"}"))
                .andExpect(status().isOk());

        // Both requests race to move the same (version=1) shipment forward; exactly one must win.
        java.util.concurrent.Callable<Integer> attempt = () -> mvc.perform(
                        post("/api/v1/shipments/" + id + "/status").contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"IN_TRANSIT\"}"))
                .andReturn().getResponse().getStatus();

        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        var futures = pool.invokeAll(java.util.List.of(attempt, attempt));
        pool.shutdown();
        var statuses = futures.stream().map(f -> {
            try {
                return f.get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).sorted().toList();

        org.assertj.core.api.Assertions.assertThat(statuses).containsExactly(200, 409);
    }

    @Test
    void tracksLocationUpdatesForAShipment() throws Exception {
        Long id = createShipment();

        mvc.perform(get("/api/v1/tracking/" + id)).andExpect(status().isNotFound());

        mvc.perform(post("/api/v1/tracking/update").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shipmentId\":%d,\"latitude\":12.97,\"longitude\":77.59}".formatted(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latitude").value(12.97));

        mvc.perform(post("/api/v1/tracking/update").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shipmentId\":%d,\"latitude\":22.57,\"longitude\":88.36}".formatted(id)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/tracking/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latitude").value(22.57))
                .andExpect(jsonPath("$.longitude").value(88.36));
    }

    private Long createShipment() throws Exception {
        String body = """
                {"senderAddress":"12 MG Road, Bengaluru","receiverAddress":"5 Park St, Kolkata"}""";
        String response = mvc.perform(post("/api/v1/shipments").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(response.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }
}
