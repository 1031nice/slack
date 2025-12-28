package com.slack.config;

import com.slack.dto.events.ReadReceiptEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;

/**
 * Kafka configuration for read receipt persistence (v0.4.1)
 * ADR-0007: Kafka-based batching for read receipt persistence
 */
@Configuration
@EnableKafka
public class KafkaConfig {

    public static final String READ_RECEIPTS_TOPIC = "read-receipts";

    /**
     * Create read-receipts topic with 8 partitions
     * Partitions allow parallel processing and horizontal scaling
     * Retention: 48 hours (2 days) - same as Slack's job queue
     */
    @Bean
    public NewTopic readReceiptsTopic() {
        return TopicBuilder.name(READ_RECEIPTS_TOPIC)
                .partitions(8)  // 8 partitions for parallel processing
                .replicas(1)    // Single broker, so replication=1
                .config("retention.ms", String.valueOf(48 * 60 * 60 * 1000))  // 48 hours
                .config("compression.type", "snappy")  // Compress data on disk
                .build();
    }

    /**
     * Configure batch listener for consumer
     * Enables batch processing with deduplication
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ReadReceiptEvent> batchFactory(
            ConsumerFactory<String, ReadReceiptEvent> consumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, ReadReceiptEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory);
        factory.setBatchListener(true);  // Enable batch processing
        factory.setConcurrency(4);  // 4 consumer threads (can scale up)

        // Manual acknowledgment for better control
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);

        return factory;
    }
}
