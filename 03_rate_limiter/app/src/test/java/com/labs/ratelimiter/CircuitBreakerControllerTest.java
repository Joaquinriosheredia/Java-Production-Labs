package com.labs.ratelimiter;

import com.labs.ratelimiter.controller.RateLimitedController;
import com.labs.ratelimiter.service.TokenBucketService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RateLimitedController.class)
class CircuitBreakerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TokenBucketService tokenBucketService;

    @Test
    void whenRedisUnavailable_shouldReturn503() throws Exception {
        when(tokenBucketService.tryConsume(any()))
            .thenThrow(new RedisUnavailableException("Redis circuit open", new RuntimeException("connection refused")));

        mockMvc.perform(get("/api/v1/resource").header("X-API-Key", "test-client"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.error").value("Rate limiter temporarily unavailable. Please retry shortly."));
    }
}
