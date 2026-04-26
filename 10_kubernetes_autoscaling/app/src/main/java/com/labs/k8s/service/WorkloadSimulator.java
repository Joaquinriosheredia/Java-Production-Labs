package com.labs.k8s.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simulates varying workload to demonstrate HPA scaling behavior.
 * Exposes a custom Prometheus metric: lab_active_requests_gauge
 * This metric drives the HPA scale-up/scale-down decision.
 */
@Service
public class WorkloadSimulator {

    private static final Logger log = LoggerFactory.getLogger(WorkloadSimulator.class);
    private final AtomicInteger activeRequests = new AtomicInteger(0);
    private final AtomicInteger queueDepth = new AtomicInteger(0);

    public WorkloadSimulator(MeterRegistry meterRegistry) {
        Gauge.builder("lab.active.requests", activeRequests, AtomicInteger::get)
            .tag("lab", "10_kubernetes_autoscaling")
            .description("Number of active requests being processed — drives HPA")
            .register(meterRegistry);

        Gauge.builder("lab.queue.depth", queueDepth, AtomicInteger::get)
            .tag("lab", "10_kubernetes_autoscaling")
            .description("Request queue depth")
            .register(meterRegistry);
    }

    public record WorkResult(int activeRequests, int queueDepth, long processedMs) {}

    public WorkResult processRequest(long workMs) throws InterruptedException {
        activeRequests.incrementAndGet();
        queueDepth.incrementAndGet();
        try {
            Thread.sleep(workMs);
            return new WorkResult(activeRequests.get(), queueDepth.get(), workMs);
        } finally {
            activeRequests.decrementAndGet();
            queueDepth.decrementAndGet();
        }
    }

    public void setQueueDepth(int depth) {
        queueDepth.set(depth);
    }

    public int getActiveRequests() { return activeRequests.get(); }
    public int getQueueDepth() { return queueDepth.get(); }
}
