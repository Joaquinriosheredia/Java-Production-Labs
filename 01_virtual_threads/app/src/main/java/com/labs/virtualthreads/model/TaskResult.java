package com.labs.virtualthreads.model;

import java.time.Instant;

public record TaskResult(
    String taskId,
    String threadName,
    boolean isVirtual,
    long durationMs,
    String result,
    Instant completedAt
) {}
