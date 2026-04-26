package com.labs.dockeropt.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class ImageInfoController {

    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> info() {
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        Runtime runtime = Runtime.getRuntime();
        return ResponseEntity.ok(Map.of(
            "javaVersion", System.getProperty("java.version"),
            "jvmName", System.getProperty("java.vm.name"),
            "availableProcessors", runtime.availableProcessors(),
            "heapUsedMb", memory.getHeapMemoryUsage().getUsed() / 1024 / 1024,
            "heapMaxMb", memory.getHeapMemoryUsage().getMax() / 1024 / 1024,
            "nonHeapUsedMb", memory.getNonHeapMemoryUsage().getUsed() / 1024 / 1024,
            "user", System.getProperty("user.name")
        ));
    }
}
