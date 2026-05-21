package com.labs.kafkastreams.config;

import com.labs.kafkastreams.topology.OrderStreamTopology;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic ordersTopic() {
        return TopicBuilder.name(OrderStreamTopology.INPUT_TOPIC)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic metricsTopic() {
        return TopicBuilder.name(OrderStreamTopology.OUTPUT_TOPIC)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
