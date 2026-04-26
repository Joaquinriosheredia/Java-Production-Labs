package com.labs.dockeropt;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ImageInfoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void infoEndpoint_shouldReturnJvmDetails() throws Exception {
        mockMvc.perform(get("/api/v1/info"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.javaVersion").isString())
            .andExpect(jsonPath("$.heapUsedMb").isNumber())
            .andExpect(jsonPath("$.user").isString());
    }

    @Test
    void actuatorHealth_shouldBeUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }
}
