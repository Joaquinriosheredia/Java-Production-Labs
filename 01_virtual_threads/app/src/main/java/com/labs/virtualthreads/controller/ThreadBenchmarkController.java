package com.labs.virtualthreads.controller;

import com.labs.virtualthreads.model.TaskResult;
import com.labs.virtualthreads.service.IoSimulationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/threads")
public class ThreadBenchmarkController {

    private static final Logger log = LoggerFactory.getLogger(ThreadBenchmarkController.class);
    private final IoSimulationService simulationService;

    public ThreadBenchmarkController(IoSimulationService simulationService) {
        this.simulationService = simulationService;
    }

    /**
     * Run N concurrent tasks on virtual threads.
     *
     * GET /api/v1/threads/virtual?tasks=200&latencyMs=100
     */
    @GetMapping("/virtual")
    public ResponseEntity<Map<String, Object>> runVirtual(
            @RequestParam(defaultValue = "100") int tasks,
            @RequestParam(defaultValue = "100") long latencyMs) throws InterruptedException {

        long start = System.currentTimeMillis();
        List<TaskResult> results = simulationService.runOnVirtualThreads(tasks, latencyMs);
        long totalMs = System.currentTimeMillis() - start;

        log.info("Virtual threads: {} tasks in {}ms", tasks, totalMs);
        return ResponseEntity.ok(buildResponse("virtual", results, totalMs));
    }

    /**
     * Run N concurrent tasks on a fixed platform thread pool.
     *
     * GET /api/v1/threads/platform?tasks=200&latencyMs=100&poolSize=20
     */
    @GetMapping("/platform")
    public ResponseEntity<Map<String, Object>> runPlatform(
            @RequestParam(defaultValue = "100") int tasks,
            @RequestParam(defaultValue = "100") long latencyMs,
            @RequestParam(defaultValue = "20") int poolSize) throws InterruptedException {

        long start = System.currentTimeMillis();
        List<TaskResult> results = simulationService.runOnPlatformThreads(tasks, latencyMs, poolSize);
        long totalMs = System.currentTimeMillis() - start;

        log.info("Platform threads: {} tasks (pool={}) in {}ms", tasks, poolSize, totalMs);
        return ResponseEntity.ok(buildResponse("platform", results, totalMs));
    }

    /**
     * Returns info about the thread handling this request.
     * Useful to verify virtual thread is active.
     *
     * GET /api/v1/threads/info
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> threadInfo() {
        Thread t = Thread.currentThread();
        return ResponseEntity.ok(Map.of(
            "name", t.getName(),
            "isVirtual", t.isVirtual(),
            "threadId", t.threadId()
        ));
    }

    private Map<String, Object> buildResponse(String type, List<TaskResult> results, long totalMs) {
        long virtualCount = results.stream().filter(TaskResult::isVirtual).count();
        return Map.of(
            "type", type,
            "totalTasks", results.size(),
            "totalDurationMs", totalMs,
            "virtualThreadCount", virtualCount,
            "platformThreadCount", results.size() - virtualCount,
            "throughputTps", results.size() * 1000.0 / Math.max(totalMs, 1)
        );
    }
}
