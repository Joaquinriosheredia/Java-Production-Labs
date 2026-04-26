package com.labs.kafkastreams.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.labs.kafkastreams.topology.OrderStreamTopology;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/streams")
public class StreamController {

    private static final Logger log = LoggerFactory.getLogger(StreamController.class);
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final StreamsBuilderFactoryBean streamsFactory;
    private final ObjectMapper mapper;

    public StreamController(KafkaTemplate<String, String> kafkaTemplate,
                             StreamsBuilderFactoryBean streamsFactory,
                             ObjectMapper mapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.streamsFactory = streamsFactory;
        this.mapper = mapper;
    }

    /**
     * Publish a test order to the input topic.
     */
    @PostMapping("/orders")
    public ResponseEntity<Map<String, Object>> publishOrder(
            @RequestParam(defaultValue = "user-1") String userId,
            @RequestParam(defaultValue = "COMPLETED") String status,
            @RequestParam(defaultValue = "99.99") double amount) throws Exception {

        String orderId = UUID.randomUUID().toString();
        String payload = mapper.writeValueAsString(Map.of(
            "orderId", orderId,
            "userId", userId,
            "status", status,
            "amount", amount
        ));
        kafkaTemplate.send(OrderStreamTopology.INPUT_TOPIC, orderId, payload).get();
        log.info("Published order {} for user {}", orderId, userId);
        return ResponseEntity.ok(Map.of("orderId", orderId, "topic", OrderStreamTopology.INPUT_TOPIC));
    }

    /**
     * Returns Kafka Streams application state.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        KafkaStreams streams = streamsFactory.getKafkaStreams();
        KafkaStreams.State state = streams != null ? streams.state() : KafkaStreams.State.NOT_RUNNING;
        return ResponseEntity.ok(Map.of(
            "state", state.toString(),
            "inputTopic", OrderStreamTopology.INPUT_TOPIC,
            "outputTopic", OrderStreamTopology.OUTPUT_TOPIC
        ));
    }
}
