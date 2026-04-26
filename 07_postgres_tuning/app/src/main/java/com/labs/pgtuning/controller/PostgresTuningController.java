package com.labs.pgtuning.controller;

import com.labs.pgtuning.service.DataSeeder;
import com.labs.pgtuning.service.QueryBenchmarkService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/postgres")
public class PostgresTuningController {

    private final QueryBenchmarkService benchmarkService;
    private final DataSeeder dataSeeder;

    public PostgresTuningController(QueryBenchmarkService benchmarkService, DataSeeder dataSeeder) {
        this.benchmarkService = benchmarkService;
        this.dataSeeder = dataSeeder;
    }

    @PostMapping("/seed")
    public ResponseEntity<Map<String, Object>> seed(
            @RequestParam(defaultValue = "100000") int rows) {
        dataSeeder.seed(rows);
        return ResponseEntity.ok(Map.of("seeded", rows));
    }

    @GetMapping("/benchmark")
    public ResponseEntity<Map<String, Object>> benchmark(
            @RequestParam(defaultValue = "index_scan") String mode,
            @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(benchmarkService.benchmarkQuery(mode, limit));
    }

    @GetMapping("/explain")
    public ResponseEntity<Map<String, Object>> explain(
            @RequestParam(defaultValue = "pending_no_index") String query) {
        return ResponseEntity.ok(benchmarkService.explainAnalyze(query));
    }

    @GetMapping("/compare")
    public ResponseEntity<Map<String, Object>> compare(
            @RequestParam(defaultValue = "100") int limit) {
        var seqScan = benchmarkService.benchmarkQuery("seq_scan", limit);
        var indexScan = benchmarkService.benchmarkQuery("index_scan", limit);
        double speedup = (double) (long) seqScan.get("durationMs") / Math.max((long) indexScan.get("durationMs"), 1);
        return ResponseEntity.ok(Map.of(
            "seqScan", seqScan,
            "indexScan", indexScan,
            "indexSpeedup", String.format("%.1fx faster", speedup)
        ));
    }
}
