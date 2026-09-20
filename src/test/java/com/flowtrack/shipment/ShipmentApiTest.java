package com.flowtrack.shipment;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.flowtrack.TestcontainersConfiguration;
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
}
