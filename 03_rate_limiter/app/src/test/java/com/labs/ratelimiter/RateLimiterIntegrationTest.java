package com.labs.ratelimiter;

import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class RateLimiterIntegrationTest {

    @Container
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7-alpine"));

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.url", redis::getRedisURI);
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void firstRequest_shouldBeAllowed() throws Exception {
        mockMvc.perform(get("/api/v1/resource")
                .header("X-API-Key", "test-client-allowed"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Request processed"))
            .andExpect(header().exists("X-RateLimit-Remaining"));
    }

    @Test
    void afterExceedingLimit_shouldReturn429() throws Exception {
        String apiKey = "test-client-429";
        // Exhaust all 20 tokens
        for (int i = 0; i < 20; i++) {
            mockMvc.perform(get("/api/v1/resource").header("X-API-Key", apiKey))
                .andExpect(status().isOk());
        }
        // 21st request should be rejected
        mockMvc.perform(get("/api/v1/resource").header("X-API-Key", apiKey))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.error").value("Too Many Requests"))
            .andExpect(header().exists("Retry-After"));
    }

    @Test
    void differentClients_shouldHaveSeparateBuckets() throws Exception {
        // Exhaust client-A
        for (int i = 0; i < 20; i++) {
            mockMvc.perform(get("/api/v1/resource").header("X-API-Key", "client-A"));
        }
        mockMvc.perform(get("/api/v1/resource").header("X-API-Key", "client-A"))
            .andExpect(status().isTooManyRequests());

        // client-B should still be fine
        mockMvc.perform(get("/api/v1/resource").header("X-API-Key", "client-B"))
            .andExpect(status().isOk());
    }
}
