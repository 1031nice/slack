package com.slack.service;

import com.slack.config.KafkaConfig;
import com.slack.dto.events.ReadReceiptEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Kafka consumer for read receipt persistence
 * Implements batching and deduplication for efficient DB writes
 * ADR-0007: Kafka-based batching for read receipt persistence
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReadReceiptPersistenceService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * User-Channel composite key for deduplication
     */
    private record UserChannelKey(Long userId, Long channelId) {}

    /**
     * Consume read receipt events from Kafka in batches
     * Implements deduplication and batch upsert for efficiency
     *
     * Processing flow:
     * 1. Receive batch of events (up to 500 per poll)
     * 2. Deduplicate: Keep latest timestamp per user-channel pair
     * 3. Batch upsert to DB with GREATEST() for order resolution
     * 4. Manual acknowledgment after successful DB write
     *
     * @param events Batch of read receipt events
     * @param partition Kafka partition (for logging)
     * @param acknowledgment Manual acknowledgment handle
     */
    @KafkaListener(
            topics = KafkaConfig.READ_RECEIPTS_TOPIC,
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "batchFactory"
    )
    public void consumeReadReceipts(
            @Payload List<ReadReceiptEvent> events,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            Acknowledgment acknowledgment) {

        if (events == null || events.isEmpty()) {
            log.debug("Received empty batch from partition {}", partition);
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
            }
            return;
        }

        log.info("Consuming {} read receipt events from partition {}", events.size(), partition);

        try {
            // Deduplicate: Keep latest timestamp per user-channel pair
            Map<UserChannelKey, ReadReceiptEvent> deduplicated = deduplicateEvents(events);

            log.info("Deduplication: {} events -> {} unique user-channel pairs ({}% reduction)",
                    events.size(),
                    deduplicated.size(),
                    Math.round((1.0 - (double) deduplicated.size() / events.size()) * 100));

            // Batch upsert to DB
            batchUpsert(deduplicated);

            // Manual acknowledgment after successful DB write
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
            }

            log.info("Successfully persisted {} read receipts from partition {}", deduplicated.size(), partition);

        } catch (Exception e) {
            log.error("Failed to process read receipt batch from partition {}: {} events",
                    partition, events.size(), e);
            // Don't acknowledge - Kafka will redeliver
            // This ensures no data loss on failure
            throw e;  // Let Spring Kafka handle retry logic
        }
    }

    /**
     * Deduplicate events: Keep latest timestamp per user-channel pair
     * If same user reads channel multiple times in batch, only keep latest
     *
     * @param events List of read receipt events
     * @return Map of user-channel key to latest event
     */
    private Map<UserChannelKey, ReadReceiptEvent> deduplicateEvents(List<ReadReceiptEvent> events) {
        return events.stream()
                .collect(Collectors.toMap(
                        event -> new UserChannelKey(event.getUserId(), event.getChannelId()),
                        event -> event,
                        // Merge function: keep event with latest timestamp
                        (event1, event2) -> {
                            String ts1 = event1.getLastReadTimestamp();
                            String ts2 = event2.getLastReadTimestamp();
                            return ts1.compareTo(ts2) >= 0 ? event1 : event2;
                        }
                ));
    }

    /**
     * Batch upsert read receipts to database
     * Uses GREATEST() SQL function to prevent stale writes
     *
     * SQL logic:
     * - INSERT if not exists
     * - UPDATE only if new timestamp >= existing timestamp (monotonic)
     * - GREATEST() ensures newer timestamp always wins (prevents order inversion)
     *
     * @param deduplicated Map of user-channel key to event
     */
    @Transactional
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    protected void batchUpsert(Map<UserChannelKey, ReadReceiptEvent> deduplicated) {
        if (deduplicated.isEmpty()) {
            return;
        }

        String sql = """
            INSERT INTO read_receipts (user_id, channel_id, last_read_timestamp, updated_at)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (user_id, channel_id)
            DO UPDATE SET
                last_read_timestamp = GREATEST(
                    read_receipts.last_read_timestamp,
                    EXCLUDED.last_read_timestamp
                ),
                updated_at = CASE
                    WHEN EXCLUDED.last_read_timestamp >= read_receipts.last_read_timestamp
                    THEN EXCLUDED.updated_at
                    ELSE read_receipts.updated_at
                END
            WHERE EXCLUDED.last_read_timestamp >= read_receipts.last_read_timestamp
            """;

        jdbcTemplate.batchUpdate(sql, deduplicated.entrySet(), deduplicated.size(),
                (ps, entry) -> {
                    ReadReceiptEvent event = entry.getValue();
                    ps.setLong(1, event.getUserId());
                    ps.setLong(2, event.getChannelId());
                    ps.setString(3, event.getLastReadTimestamp());
                    ps.setTimestamp(4, Timestamp.from(Instant.now()));
                });

        log.debug("Batch upserted {} read receipts to database", deduplicated.size());
    }
}
