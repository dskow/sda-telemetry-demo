package com.skowronski.sda.telemetry.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.skowronski.sda.telemetry.domain.OrbitClass;
import com.skowronski.sda.telemetry.domain.Rso;
import com.skowronski.sda.telemetry.domain.RsoType;
import com.skowronski.sda.telemetry.service.RsoCatalog;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RsoControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        RsoCatalog catalog = new RsoCatalog(new SimpleMeterRegistry());
        RsoController controller = new RsoController(catalog);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Test
    void getMissingRsoReturns404() throws Exception {
        mockMvc.perform(get("/api/rso/does-not-exist"))
                .andExpect(status().isNotFound());
    }

    @Test
    void postCreatesAndGetReturnsRso() throws Exception {
        Rso rso = new Rso("25544", "ISS", RsoType.PAYLOAD, OrbitClass.LEO,
                51.6, 0.0, 408.0, Instant.parse("2026-05-08T00:00:00Z"));

        mockMvc.perform(post("/api/rso")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rso)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/rso/25544"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.designator").value("ISS"));
    }

    @Test
    void deleteRemovesRso() throws Exception {
        Rso rso = new Rso("Z1", "Z1", RsoType.DEBRIS, OrbitClass.LEO, 0, 0, 100, Instant.now());
        mockMvc.perform(post("/api/rso")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rso)))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/rso/Z1")).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/rso/Z1")).andExpect(status().isNotFound());
    }
}
