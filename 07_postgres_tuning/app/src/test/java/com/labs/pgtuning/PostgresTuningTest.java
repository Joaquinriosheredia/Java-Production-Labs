package com.labs.pgtuning;

import com.labs.pgtuning.service.DataSeeder;
import com.labs.pgtuning.service.QueryBenchmarkService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class PostgresTuningTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("pgtuning_lab")
        .withUsername("labs")
        .withPassword("labs");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private DataSeeder seeder;

    @Autowired
    private QueryBenchmarkService benchmarkService;

    @BeforeEach
    void seed() {
        seeder.seed(10_000);
    }

    @Test
    void indexScan_shouldBeConsistentlyFaster() {
        // Warm up
        benchmarkService.benchmarkQuery("index_scan", 100);
        benchmarkService.benchmarkQuery("seq_scan", 100);

        // Measure
        long indexMs = (long) benchmarkService.benchmarkQuery("index_scan", 100).get("durationMs");
        long seqMs = (long) benchmarkService.benchmarkQuery("seq_scan", 100).get("durationMs");

        assertThat(indexMs).isLessThan(seqMs);
    }

    @Test
    void benchmark_shouldReturnExpectedFields() {
        Map<String, Object> result = benchmarkService.benchmarkQuery("index_scan", 50);
        assertThat(result).containsKeys("mode", "limit", "rowsReturned", "durationMs");
        assertThat((int) result.get("rowsReturned")).isLessThanOrEqualTo(50);
    }
}
