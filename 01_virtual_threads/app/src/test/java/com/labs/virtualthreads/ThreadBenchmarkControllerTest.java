package com.labs.virtualthreads;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ThreadBenchmarkControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void threadInfo_shouldReturnVirtualThreadInfo() throws Exception {
        mockMvc.perform(get("/api/v1/threads/info"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isVirtual").value(true));
    }

    @Test
    void virtualEndpoint_shouldReturnResults() throws Exception {
        mockMvc.perform(get("/api/v1/threads/virtual")
                .param("tasks", "5")
                .param("latencyMs", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalTasks").value(5))
            .andExpect(jsonPath("$.type").value("virtual"))
            .andExpect(jsonPath("$.throughputTps").isNumber());
    }

    @Test
    void platformEndpoint_shouldReturnResults() throws Exception {
        mockMvc.perform(get("/api/v1/threads/platform")
                .param("tasks", "5")
                .param("latencyMs", "10")
                .param("poolSize", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalTasks").value(5))
            .andExpect(jsonPath("$.type").value("platform"));
    }

    @Test
    void actuatorHealth_shouldBeUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void actuatorPrometheus_shouldExposeMetrics() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("lab_thread_duration")));
    }
}
