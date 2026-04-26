package com.labs.redisvskafka.config;

import com.labs.redisvskafka.service.MessagingBenchmarkService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Configuration
public class MessagingConfig {

    @Bean
    public RedisMessageListenerContainer redisContainer(
            RedisConnectionFactory factory,
            MessagingBenchmarkService benchmarkService) {
        var container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        var adapter = new MessageListenerAdapter(benchmarkService, "incrementRedisReceived");
        container.addMessageListener(adapter, new PatternTopic("lab06:benchmark"));
        return container;
    }

    @Component
    static class KafkaConsumer {
        private final MessagingBenchmarkService service;

        KafkaConsumer(MessagingBenchmarkService service) {
            this.service = service;
        }

        @KafkaListener(topics = "lab06-benchmark", groupId = "lab06-consumer")
        public void consume(String message) {
            service.incrementKafkaReceived();
        }
    }
}
