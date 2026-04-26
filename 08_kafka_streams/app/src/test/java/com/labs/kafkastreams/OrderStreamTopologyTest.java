package com.labs.kafkastreams;

import com.labs.kafkastreams.topology.OrderStreamTopology;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Produced;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test using kafka-streams-test-utils (no real Kafka broker required).
 */
class OrderStreamTopologyTest {

    private TopologyTestDriver testDriver;
    private TestInputTopic<String, String> inputTopic;
    private TestOutputTopic<String, String> outputTopic;

    @BeforeEach
    void setUp() {
        StreamsBuilder builder = new StreamsBuilder();
        OrderStreamTopology topology = new OrderStreamTopology();
        topology.buildPipeline(builder);

        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "test-lab08");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:9092");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG,
            org.apache.kafka.common.serialization.Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG,
            org.apache.kafka.common.serialization.Serdes.String().getClass());

        testDriver = new TopologyTestDriver(builder.build(), props);
        inputTopic = testDriver.createInputTopic(
            OrderStreamTopology.INPUT_TOPIC, new StringSerializer(), new StringSerializer());
        outputTopic = testDriver.createOutputTopic(
            OrderStreamTopology.OUTPUT_TOPIC, new StringDeserializer(), new StringDeserializer());
    }

    @AfterEach
    void tearDown() {
        testDriver.close();
    }

    @Test
    void completedOrders_shouldProduceMetrics() {
        inputTopic.pipeInput("order-1", "{\"userId\":\"user-1\",\"status\":\"COMPLETED\",\"amount\":50.0}");
        inputTopic.pipeInput("order-2", "{\"userId\":\"user-1\",\"status\":\"COMPLETED\",\"amount\":30.0}");

        assertThat(outputTopic.isEmpty()).isFalse();
        String metric = outputTopic.readValue();
        assertThat(metric).contains("user-1");
        assertThat(metric).contains("orderCount");
    }

    @Test
    void pendingOrders_shouldBeFiltered() {
        inputTopic.pipeInput("order-3", "{\"userId\":\"user-2\",\"status\":\"PENDING\",\"amount\":10.0}");
        assertThat(outputTopic.isEmpty()).isTrue();
    }

    @Test
    void invalidJson_shouldBeSkipped() {
        inputTopic.pipeInput("order-bad", "not-valid-json");
        assertThat(outputTopic.isEmpty()).isTrue();
    }
}
