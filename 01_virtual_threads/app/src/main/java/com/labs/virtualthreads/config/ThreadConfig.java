package com.labs.virtualthreads.config;

import org.springframework.boot.web.embedded.tomcat.TomcatProtocolHandlerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.concurrent.Executors;

@Configuration
public class ThreadConfig {

    /**
     * Enables Virtual Threads for Tomcat's request handling.
     * Each incoming HTTP request is handled by a new virtual thread instead of
     * a platform thread from a fixed pool. This is the key configuration for
     * Project Loom integration in Spring Boot 3.2+.
     */
    @Bean
    @Profile("!platform-threads")
    public TomcatProtocolHandlerCustomizer<?> virtualThreadsCustomizer() {
        return protocolHandler ->
            protocolHandler.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    }
}
