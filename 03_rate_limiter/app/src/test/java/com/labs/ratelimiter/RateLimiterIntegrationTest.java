package com.labs.ratelimiter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class RateLimiterIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.url",
                () -> "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379));
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void cleanRedis() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

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
        for (int i = 0; i < 20; i++) {
            mockMvc.perform(get("/api/v1/resource").header("X-API-Key", apiKey))
                .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/v1/resource").header("X-API-Key", apiKey))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.error").value("Too Many Requests"))
            .andExpect(header().exists("Retry-After"));
    }

    @Test
    void differentClients_shouldHaveSeparateBuckets() throws Exception {
        for (int i = 0; i < 20; i++) {
            mockMvc.perform(get("/api/v1/resource").header("X-API-Key", "client-A"));
        }
        mockMvc.perform(get("/api/v1/resource").header("X-API-Key", "client-A"))
            .andExpect(status().isTooManyRequests());
        mockMvc.perform(get("/api/v1/resource").header("X-API-Key", "client-B"))
            .andExpect(status().isOk());
    }
}
