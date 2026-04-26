package com.labs.virtualthreads.service;

import com.labs.virtualthreads.model.TaskResult;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Simulates I/O-bound work (e.g. DB calls, external API calls) to demonstrate
 * the difference in throughput between platform threads and virtual threads.
 *
 * Key insight: when a virtual thread blocks on I/O, it is unmounted from its
 * carrier thread, allowing the carrier to handle other virtual threads.
 * A platform thread pool blocks the OS thread during I/O, reducing concurrency.
 */
@Service
public class IoSimulationService {

    private static final Logger log = LoggerFactory.getLogger(IoSimulationService.class);
    private final Timer virtualThreadTimer;
    private final Timer platformThreadTimer;

    public IoSimulationService(MeterRegistry meterRegistry) {
        this.virtualThreadTimer = Timer.builder("lab.thread.duration")
            .tag("lab", "01_virtual_threads")
            .tag("type", "virtual")
            .register(meterRegistry);
        this.platformThreadTimer = Timer.builder("lab.thread.duration")
            .tag("lab", "01_virtual_threads")
            .tag("type", "platform")
            .register(meterRegistry);
    }

    /**
     * Runs N tasks concurrently on virtual threads.
     * Each task simulates latencyMs of blocking I/O.
     */
    public List<TaskResult> runOnVirtualThreads(int tasks, long latencyMs) throws InterruptedException {
        log.info("Starting {} tasks on virtual threads with {}ms latency", tasks, latencyMs);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            return runTasks(executor, tasks, latencyMs, virtualThreadTimer);
        }
    }

    /**
     * Runs N tasks concurrently on a fixed platform thread pool.
     * Demonstrates the bottleneck when pool size < concurrency demand.
     */
    public List<TaskResult> runOnPlatformThreads(int tasks, long latencyMs, int poolSize) throws InterruptedException {
        log.info("Starting {} tasks on platform thread pool (size={}) with {}ms latency", tasks, poolSize, latencyMs);

        try (ExecutorService executor = Executors.newFixedThreadPool(poolSize)) {
            return runTasks(executor, tasks, latencyMs, platformThreadTimer);
        }
    }

    private List<TaskResult> runTasks(ExecutorService executor, int tasks, long latencyMs, Timer timer)
            throws InterruptedException {
        List<CompletableFuture<TaskResult>> futures = new ArrayList<>();

        for (int i = 0; i < tasks; i++) {
            final String taskId = "task-" + i;
            futures.add(CompletableFuture.supplyAsync(() -> {
                long start = System.currentTimeMillis();
                try {
                    // Simulate blocking I/O — this is where virtual threads shine
                    Thread.sleep(latencyMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                long duration = System.currentTimeMillis() - start;
                Thread t = Thread.currentThread();
                timer.record(duration, TimeUnit.MILLISECONDS);
                return new TaskResult(
                    taskId,
                    t.getName(),
                    t.isVirtual(),
                    duration,
                    "OK",
                    Instant.now()
                );
            }, executor));
        }

        return futures.stream()
            .map(CompletableFuture::join)
            .toList();
    }
}
