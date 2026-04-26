package com.labs.pgtuning.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class QueryBenchmarkService {

    private static final Logger log = LoggerFactory.getLogger(QueryBenchmarkService.class);
    private final EntityManager em;
    private final Timer seqScanTimer;
    private final Timer indexScanTimer;

    public QueryBenchmarkService(EntityManager em, MeterRegistry meterRegistry) {
        this.em = em;
        this.seqScanTimer = Timer.builder("lab.pg.query.duration")
            .tag("lab", "07_postgres_tuning")
            .tag("plan", "seq_scan")
            .register(meterRegistry);
        this.indexScanTimer = Timer.builder("lab.pg.query.duration")
            .tag("lab", "07_postgres_tuning")
            .tag("plan", "index_scan")
            .register(meterRegistry);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> explainAnalyze(String queryName) {
        String sql = switch (queryName) {
            case "pending_no_index" ->
                "EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) SELECT * FROM events WHERE status = 'PENDING' ORDER BY occurred_at ASC LIMIT 100";
            case "pending_with_index" ->
                "EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) SELECT * FROM events WHERE status = 'PENDING' ORDER BY occurred_at ASC LIMIT 100";
            case "user_events" ->
                "EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) SELECT * FROM events WHERE user_id = 'user-1' ORDER BY occurred_at DESC LIMIT 50";
            default -> throw new IllegalArgumentException("Unknown query: " + queryName);
        };

        List<?> result = em.createNativeQuery(sql).getResultList();
        return Map.of("query", queryName, "plan", result.toString());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> benchmarkQuery(String mode, int limit) {
        long start = System.currentTimeMillis();
        List<?> results;

        if ("seq_scan".equals(mode)) {
            // Force sequential scan by disabling index usage
            em.createNativeQuery("SET enable_indexscan = off").executeUpdate();
            em.createNativeQuery("SET enable_bitmapscan = off").executeUpdate();
            results = em.createNativeQuery(
                "SELECT * FROM events WHERE status = 'PENDING' ORDER BY occurred_at ASC LIMIT :limit")
                .setParameter("limit", limit)
                .getResultList();
            em.createNativeQuery("SET enable_indexscan = on").executeUpdate();
            em.createNativeQuery("SET enable_bitmapscan = on").executeUpdate();
        } else {
            results = em.createNativeQuery(
                "SELECT * FROM events WHERE status = 'PENDING' ORDER BY occurred_at ASC LIMIT :limit")
                .setParameter("limit", limit)
                .getResultList();
        }

        long durationMs = System.currentTimeMillis() - start;
        (mode.equals("seq_scan") ? seqScanTimer : indexScanTimer)
            .record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);

        return Map.of(
            "mode", mode,
            "limit", limit,
            "rowsReturned", results.size(),
            "durationMs", durationMs
        );
    }
}
