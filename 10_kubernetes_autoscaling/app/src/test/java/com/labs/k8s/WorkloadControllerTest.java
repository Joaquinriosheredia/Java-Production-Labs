package com.labs.k8s;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class WorkloadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void workEndpoint_shouldReturnResult() throws Exception {
        mockMvc.perform(get("/api/v1/work").param("workMs", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.processedMs").value(10));
    }

    @Test
    void metricsSnapshot_shouldReturnGaugeValues() throws Exception {
        mockMvc.perform(get("/api/v1/metrics/snapshot"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.activeRequests").isNumber())
            .andExpect(jsonPath("$.hpaMetric").value("lab_active_requests_gauge"));
    }

    @Test
    void actuatorLiveness_shouldBeUp() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void actuatorReadiness_shouldBeUp() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void prometheusMetrics_shouldIncludeLabMetric() throws Exception {
        // Trigger a request to register the gauge
        mockMvc.perform(get("/api/v1/work").param("workMs", "5"));

        mockMvc.perform(get("/actuator/prometheus"))
            .andExpect(status().isOk())
            .andExpect(content().string(
                org.hamcrest.Matchers.containsString("lab_active_requests")));
    }
}
