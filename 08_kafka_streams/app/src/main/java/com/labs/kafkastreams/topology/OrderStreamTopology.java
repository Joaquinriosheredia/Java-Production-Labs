package com.labs.kafkastreams.topology;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Kafka Streams topology for real-time order processing:
 *
 * 1. Filter: only COMPLETED orders
 * 2. Map: extract relevant fields
 * 3. GroupBy: user ID
 * 4. Window: 60-second tumbling window
 * 5. Aggregate: count orders and sum revenue per user
 * 6. Output: to "order-metrics" topic
 */
@Component
public class OrderStreamTopology {

    private static final Logger log = LoggerFactory.getLogger(OrderStreamTopology.class);
    private final ObjectMapper mapper = new ObjectMapper();

    public static final String INPUT_TOPIC = "orders-stream";
    public static final String OUTPUT_TOPIC = "order-metrics";

    @Autowired
    public void buildPipeline(StreamsBuilder builder) {
        KStream<String, String> orders = builder.stream(
            INPUT_TOPIC,
            Consumed.with(Serdes.String(), Serdes.String())
        );

        orders
            // Step 1: Filter only COMPLETED orders
            .filter((key, value) -> {
                try {
                    JsonNode node = mapper.readTree(value);
                    return "COMPLETED".equals(node.path("status").asText());
                } catch (Exception e) {
                    log.warn("Invalid order JSON: {}", value);
                    return false;
                }
            })
            // Step 2: Extract userId and amount
            .map((key, value) -> {
                try {
                    JsonNode node = mapper.readTree(value);
                    String userId = node.path("userId").asText("unknown");
                    return KeyValue.pair(userId, value);
                } catch (Exception e) {
                    return KeyValue.pair("error", value);
                }
            })
            // Step 3–4: Group by userId + 60-second tumbling window
            .groupByKey()
            .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(60)))
            // Step 5: Count orders per user per window
            .count()
            // Step 6: Output metrics
            .toStream()
            .map((windowedKey, count) -> {
                String userId = windowedKey.key();
                ObjectNode metric = mapper.createObjectNode();
                metric.put("userId", userId);
                metric.put("orderCount", count);
                metric.put("windowStart", windowedKey.window().start());
                metric.put("windowEnd", windowedKey.window().end());
                return KeyValue.pair(userId, metric.toString());
            })
            .to(OUTPUT_TOPIC, Produced.with(Serdes.String(), Serdes.String()));

        log.info("Kafka Streams topology built: {} → filter → group → window(60s) → count → {}",
            INPUT_TOPIC, OUTPUT_TOPIC);
    }
}
