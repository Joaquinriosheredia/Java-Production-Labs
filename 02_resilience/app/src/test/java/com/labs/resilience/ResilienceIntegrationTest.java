package com.labs.resilience;

import com.labs.resilience.service.ExternalServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ResilienceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExternalServiceClient client;

    @BeforeEach
    void resetFailureRate() {
        client.setFailureRate(0);
    }

    @Test
    void callEndpoint_withNoFailures_shouldReturnOk() throws Exception {
        mockMvc.perform(get("/api/v1/call").param("requestId", "test-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fallback").value(false))
            .andExpect(jsonPath("$.result").value(org.hamcrest.Matchers.containsString("OK")));
    }

    @Test
    void fallback_invoked_whenServiceAlwaysFails() throws Exception {
        client.setFailureRate(100);

        // Drain minimum calls to open circuit
        for (int i = 0; i < 12; i++) {
            mockMvc.perform(get("/api/v1/call").param("requestId", "drain-" + i));
        }

        // After circuit opens, fallback should be returned
        mockMvc.perform(get("/api/v1/call").param("requestId", "check"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fallback").value(true));
    }

    @Test
    void adminEndpoint_shouldUpdateFailureRate() throws Exception {
        mockMvc.perform(post("/api/v1/admin/failure-rate").param("percent", "75"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.failureRatePercent").value(75));
        assertThat(client.getFailureRate()).isEqualTo(75);
    }

    @Test
    void actuatorHealth_shouldExposeCircuitBreakerStatus() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void actuatorPrometheus_shouldIncludeResilienceMetrics() throws Exception {
        // Make a call first to register metrics
        mockMvc.perform(get("/api/v1/call"));

        mockMvc.perform(get("/actuator/prometheus"))
            .andExpect(status().isOk())
            .andExpect(content().string(
                org.hamcrest.Matchers.containsString("resilience4j")));
    }
}
