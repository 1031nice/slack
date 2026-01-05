package com.slack.readreceipt.service;

import com.slack.readreceipt.dto.ReadReceiptEvent;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * DLT (Dead Letter Topic) consumer for read receipt reconciliation
 * Processes failed events from Kafka consumer to ensure eventual consistency
 *
 * This replaces the scheduled reconciliation job which scanned entire tables.
 * DLT-based approach is event-driven and only processes actual failures.
 *
 * ADR-0007: Kafka-based batching for read receipt persistence
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReadReceiptDltConsumer {

    private static final String READ_RECEIPT_KEY_PREFIX = "read_receipt:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final MeterRegistry meterRegistry;

    /**
     * Process failed read receipt events from DLT
     *
     * When Kafka consumer fails to process an event (after retries),
     * it lands in the DLT. This method reconciles the failure by:
     * 1. Fetching latest value from Redis (source of truth)
     * 2. Upserting to DB with GREATEST() for order safety
     *
     * @param event Failed read receipt event
     */
    @KafkaListener(
        topics = "${spring.kafka.consumer.dlt-topic:read-receipts-dlt}",
        groupId = "${spring.kafka.consumer.group-id}-dlt",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleFailedReadReceipt(ReadReceiptEvent event) {
        log.warn("Reconciling failed event from DLT: userId={}, channelId={}, timestamp={}",
            event.getUserId(), event.getChannelId(), event.getLastReadTimestamp());

        try {
            // 1. Get latest value from Redis (source of truth for real-time)
            String redisKey = buildRedisKey(event.getUserId(), event.getChannelId());
            Object redisValue = redisTemplate.opsForValue().get(redisKey);

            String timestampToUse;
            if (redisValue != null) {
                // Redis has the value, use it (most up-to-date)
                timestampToUse = redisValue.toString();
                log.debug("Using Redis value for reconciliation: {}", timestampToUse);
            } else {
                // Redis evicted, use event value as fallback
                timestampToUse = event.getLastReadTimestamp();
                log.info("Redis value evicted, using event timestamp: {}", timestampToUse);
            }

            // 2. Upsert to DB with GREATEST() for order resolution
            String sql = """
                INSERT INTO read_receipts (user_id, channel_id, last_read_timestamp, updated_at)
                VALUES (?, ?, ?, NOW())
                ON CONFLICT (user_id, channel_id)
                DO UPDATE SET
                    last_read_timestamp = GREATEST(
                        read_receipts.last_read_timestamp,
                        EXCLUDED.last_read_timestamp
                    ),
                    updated_at = NOW()
            """;

            jdbcTemplate.update(sql, event.getUserId(), event.getChannelId(), timestampToUse);

            log.info("DLT reconciliation success: userId={}, channelId={}, timestamp={}",
                event.getUserId(), event.getChannelId(), timestampToUse);

            // 3. Track success metric
            meterRegistry.counter("read_receipts.dlt.reconciled").increment();

        } catch (Exception e) {
            log.error("DLT reconciliation failed, needs manual intervention: " +
                    "userId={}, channelId={}, timestamp={}",
                event.getUserId(), event.getChannelId(), event.getLastReadTimestamp(), e);

            // 4. Track failure metric
            meterRegistry.counter("read_receipts.dlt.failed").increment();

            // In production, this would trigger:
            // - Critical alert to on-call engineer
            // - Save to manual review queue (separate table/topic)
            // - PagerDuty/Slack notification

            // For now, log and let Kafka retry or move to final DLT
            throw new RuntimeException("DLT reconciliation failed", e);
        }
    }

    private String buildRedisKey(Long userId, Long channelId) {
        return READ_RECEIPT_KEY_PREFIX + userId + ":" + channelId;
    }
}
