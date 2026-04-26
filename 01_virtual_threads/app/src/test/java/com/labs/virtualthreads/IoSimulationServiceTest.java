package com.labs.virtualthreads;

import com.labs.virtualthreads.model.TaskResult;
import com.labs.virtualthreads.service.IoSimulationService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IoSimulationServiceTest {

    private final IoSimulationService service =
        new IoSimulationService(new SimpleMeterRegistry());

    @Test
    void virtualThreads_shouldCompleteAllTasks() throws InterruptedException {
        List<TaskResult> results = service.runOnVirtualThreads(50, 10);

        assertThat(results).hasSize(50);
        assertThat(results).allMatch(r -> r.result().equals("OK"));
        assertThat(results).allMatch(TaskResult::isVirtual);
    }

    @Test
    void virtualThreads_shouldBeFasterThanPlatformWithSmallPool() throws InterruptedException {
        int tasks = 40;
        long latencyMs = 50;
        int smallPool = 4;

        long virtualStart = System.currentTimeMillis();
        service.runOnVirtualThreads(tasks, latencyMs);
        long virtualDuration = System.currentTimeMillis() - virtualStart;

        long platformStart = System.currentTimeMillis();
        service.runOnPlatformThreads(tasks, latencyMs, smallPool);
        long platformDuration = System.currentTimeMillis() - platformStart;

        // Virtual threads should finish in roughly 1 batch (~latencyMs overhead)
        // Platform threads with pool=4 need tasks/pool batches
        assertThat(virtualDuration).isLessThan(platformDuration);
    }

    @Test
    void platformThreads_shouldCompleteAllTasks() throws InterruptedException {
        List<TaskResult> results = service.runOnPlatformThreads(10, 10, 5);

        assertThat(results).hasSize(10);
        assertThat(results).allMatch(r -> r.result().equals("OK"));
        assertThat(results).noneMatch(TaskResult::isVirtual);
    }
}
