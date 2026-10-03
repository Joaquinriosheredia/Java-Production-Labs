package com.labs.pgtuning;

import com.labs.pgtuning.repository.EventRepository;
import com.labs.pgtuning.service.DataSeeder;
import com.labs.pgtuning.service.QueryBenchmarkService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
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
    private EventRepository repository;

    @Autowired
    private DataSeeder seeder;

    @Autowired
    private QueryBenchmarkService benchmarkService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transactions;

    @BeforeEach
    void seed() {
        repository.deleteAllInBatch();
        seeder.seed(10_000);
        jdbc.execute("ANALYZE events");
    }

    // Asserts the access path, not wall-clock time: on a 10K-row table a seq scan and an
    // index scan take about the same time, so a timing comparison is noise (it failed in CI).
    // Plans are checked with the same planner settings the service uses for each mode.
    @Test
    void indexMode_usesPartialIndexScan() {
        assertThat(explain(false)).contains("Index Scan using idx_events_pending");
    }

    @Test
    void seqMode_usesSeqScan() {
        assertThat(explain(true))
            .contains("Seq Scan on events")
            .doesNotContain("idx_events_pending");
    }

    private String explain(boolean disableIndexes) {
        return transactions.execute(status -> {
            if (disableIndexes) {
                jdbc.execute("SET LOCAL enable_indexscan = off");
                jdbc.execute("SET LOCAL enable_bitmapscan = off");
            }
            return String.join("\n", jdbc.queryForList(
                "EXPLAIN SELECT * FROM events WHERE status = 'PENDING' ORDER BY occurred_at ASC LIMIT 100",
                String.class));
        });
    }

    @Test
    void benchmark_shouldReturnExpectedFields() {
        Map<String, Object> result = benchmarkService.benchmarkQuery("index_scan", 50);
        assertThat(result).containsKeys("mode", "limit", "rowsReturned", "durationMs");
        assertThat((int) result.get("rowsReturned")).isLessThanOrEqualTo(50);
    }
}
