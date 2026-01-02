package com.slack.readreceipt.service;

import com.slack.channel.repository.ChannelMemberRepository;
import com.slack.common.service.PermissionService;
import com.slack.config.KafkaConfig;
import com.slack.readreceipt.domain.ReadReceipt;
import com.slack.readreceipt.dto.ReadReceiptEvent;
import com.slack.readreceipt.repository.ReadReceiptRepository;
import com.slack.websocket.dto.WebSocketMessage;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

/**
 * Read receipt service for tracking and broadcasting read status
 * Hybrid approach: Redis for real-time access, PostgreSQL for durability
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ReadReceiptService {

    private static final String READ_RECEIPT_KEY_PREFIX = "read_receipt:";

    private final ChannelMemberRepository channelMemberRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final ReadReceiptRepository readReceiptRepository;
    private final PermissionService permissionService;
    private final KafkaTemplate<String, ReadReceiptEvent> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    /**
     * Fallback queue for Kafka producer failures
     * Events that fail to publish to Kafka are queued here for retry
     */
    private final Queue<ReadReceiptEvent> fallbackQueue = new ConcurrentLinkedQueue<>();

    /**
     * Update read receipt for a user in a channel
     * 1. Updates Redis immediately (real-time)
     * 2. Broadcasts to channel members via WebSocket
     * 3. Publishes to Kafka for durable persistence (v0.4.2)
     *
     * @param userId            User ID
     * @param channelId         Channel ID
     * @param lastReadTimestamp Last read timestamp (timestampId or ISO datetime)
     */
    @Transactional
    public void updateReadReceipt(Long userId, Long channelId, String lastReadTimestamp) {
        if (lastReadTimestamp == null || lastReadTimestamp.trim().isEmpty()) {
            log.warn("Invalid lastReadTimestamp: {}", lastReadTimestamp);
            return;
        }

        // 1. Update Redis (fast, real-time)
        String redisKey = buildRedisKey(userId, channelId);
        redisTemplate.opsForValue().set(redisKey, lastReadTimestamp);

        log.debug("Updated read receipt in Redis: userId={}, channelId={}, timestamp={}",
                userId, channelId, lastReadTimestamp);

        // 2. Broadcast via WebSocket (real-time notification)
        broadcastReadReceipt(userId, channelId, lastReadTimestamp);

        // 3. Publish to Kafka (durable, batched persistence - v0.4.2)
        publishToKafka(userId, channelId, lastReadTimestamp);
    }

    /**
     * Get read receipt for a user in a channel
     * 1. Tries Redis first (fast, real-time)
     * 2. Falls back to DB if Redis miss (recovery after restart)
     * 3. Repopulates Redis on DB hit (cache warming)
     *
     * @param userId    User ID
     * @param channelId Channel ID
     * @return Last read timestamp, or null if not found
     */
    public String getReadReceipt(Long userId, Long channelId) {
        String redisKey = buildRedisKey(userId, channelId);

        // 1. Try Redis first (cache hit)
        Object value = redisTemplate.opsForValue().get(redisKey);
        if (value != null) {
            return value.toString();
        }

        // 2. Redis miss - fallback to DB (recovery scenario)
        log.debug("Redis miss for read receipt, fetching from DB: userId={}, channelId={}",
                userId, channelId);

        return readReceiptRepository.findByUserIdAndChannelId(userId, channelId)
                .map(receipt -> {
                    String timestamp = receipt.getLastReadTimestamp();

                    // 3. Repopulate Redis (cache warming)
                    redisTemplate.opsForValue().set(redisKey, timestamp);
                    log.debug("Repopulated Redis from DB: userId={}, channelId={}, timestamp={}",
                            userId, channelId, timestamp);

                    return timestamp;
                })
                .orElse(null);
    }

    /**
     * Get all read receipts for a channel from Redis
     * Optimized to avoid N+1 query issue by using Redis Pipeline and batch DB query
     * Pipeline approach ensures compatibility with Redis Cluster (vs MGET)
     *
     * @param channelId Channel ID
     * @param userId    user ID requesting the read receipts
     * @return Map of userId -> lastReadTimestamp
     */
    public Map<Long, String> getChannelReadReceipts(Long channelId, Long userId) {
        // Authorization: must have channel access
        permissionService.requireChannelAccess(userId, channelId);

        List<Long> memberIds = channelMemberRepository.findUserIdsByChannelId(channelId);
        if (memberIds.isEmpty()) {
            return new HashMap<>();
        }

        // 1. Build Redis keys for all members
        List<String> redisKeys = memberIds.stream()
                .map(memberId -> buildRedisKey(memberId, channelId))
                .toList();

        // 2. Batch fetch from Redis using Pipeline (Cluster-safe)
        List<Object> redisValues = redisTemplate.executePipelined(
                (RedisCallback<Object>) connection -> {
                    for (String key : redisKeys) {
                        connection.stringCommands().get(key.getBytes());
                    }
                    return null;
                });

        Map<Long, String> readReceipts = new HashMap<>();
        List<Long> cacheMisses = new ArrayList<>();

        // 3. Process Redis results
        for (int i = 0; i < memberIds.size(); i++) {
            Long memberId = memberIds.get(i);
            Object value = redisValues.get(i);

            if (value != null) {
                readReceipts.put(memberId, value.toString());
            } else {
                cacheMisses.add(memberId);
            }
        }

        // 4. Batch fetch cache misses from DB (single query instead of N queries)
        if (!cacheMisses.isEmpty()) {
            log.debug("Redis cache misses for {} members, fetching from DB", cacheMisses.size());

            List<ReadReceipt> dbReceipts = readReceiptRepository.findByChannelIdAndUserIdIn(channelId, cacheMisses);

            // 5. Repopulate Redis and result map
            for (ReadReceipt receipt : dbReceipts) {
                String timestamp = receipt.getLastReadTimestamp();
                Long memberId = receipt.getUser().getId();

                readReceipts.put(memberId, timestamp);

                // Cache warming
                String redisKey = buildRedisKey(memberId, channelId);
                redisTemplate.opsForValue().set(redisKey, timestamp);
            }

            log.debug("Repopulated Redis from DB: {} read receipts", dbReceipts.size());
        }

        return readReceipts;
    }

    /**
     * Broadcast read receipt to all channel members
     *
     * @param userId            User ID who read the messages
     * @param channelId         Channel ID
     * @param lastReadTimestamp Last read timestamp
     */
    private void broadcastReadReceipt(Long userId, Long channelId, String lastReadTimestamp) {
        WebSocketMessage readReceiptMessage = WebSocketMessage.builder()
                .type(WebSocketMessage.MessageType.READ)
                .channelId(channelId)
                .userId(userId)
                .createdAt(lastReadTimestamp)  // Use createdAt field for timestamp
                .build();

        String channelDestination = "/topic/channel." + channelId;
        messagingTemplate.convertAndSend(channelDestination, readReceiptMessage);

        log.debug("Broadcasted read receipt: userId={}, channelId={}, timestamp={}",
                userId, channelId, lastReadTimestamp);
    }

    /**
     * Publish read receipt event to Kafka for durable persistence
     * Uses fallback queue for resilience against Kafka failures
     * Kafka consumer will batch process events and persist to DB
     *
     * @param userId            User ID
     * @param channelId         Channel ID
     * @param lastReadTimestamp Last read timestamp
     */
    private void publishToKafka(Long userId, Long channelId, String lastReadTimestamp) {
        try {
            ReadReceiptEvent event = ReadReceiptEvent.builder()
                    .userId(userId)
                    .channelId(channelId)
                    .lastReadTimestamp(lastReadTimestamp)
                    .build();

            // Sync send with timeout for fast failure detection
            kafkaTemplate.send(KafkaConfig.READ_RECEIPTS_TOPIC, event.getPartitionKey(), event)
                    .get(100, TimeUnit.MILLISECONDS);

            log.debug("Published read receipt to Kafka: userId={}, channelId={}, timestamp={}",
                    userId, channelId, lastReadTimestamp);

        } catch (Exception e) {
            log.error("Kafka publish failed, using fallback queue: userId={}, channelId={}, timestamp={}",
                    userId, channelId, lastReadTimestamp, e);

            // Fallback: Queue for retry
            ReadReceiptEvent event = ReadReceiptEvent.builder()
                    .userId(userId)
                    .channelId(channelId)
                    .lastReadTimestamp(lastReadTimestamp)
                    .build();

            fallbackQueue.offer(event);
            meterRegistry.counter("read_receipts.kafka.fallback").increment();

            // Redis is already updated, user experience unaffected
            // Scheduled retry will attempt republish every 5 seconds
        }
    }

    /**
     * Retry publishing events from fallback queue to Kafka
     * Runs every 5 seconds to attempt republishing failed events
     * Stops on first failure to avoid overwhelming Kafka during outages
     */
    @Scheduled(fixedDelay = 5000)
    public void retryFallbackQueue() {
        if (fallbackQueue.isEmpty()) {
            return;
        }

        int retried = 0;
        int succeeded = 0;

        log.info("Retrying fallback queue, size: {}", fallbackQueue.size());

        ReadReceiptEvent event;
        while ((event = fallbackQueue.poll()) != null) {
            retried++;
            try {
                kafkaTemplate.send(KafkaConfig.READ_RECEIPTS_TOPIC, event.getPartitionKey(), event)
                        .get(100, TimeUnit.MILLISECONDS);

                succeeded++;
                meterRegistry.counter("read_receipts.fallback.success").increment();

                log.debug("Fallback retry success: userId={}, channelId={}",
                        event.getUserId(), event.getChannelId());

            } catch (Exception e) {
                // Re-queue for next retry
                fallbackQueue.offer(event);

                log.warn("Fallback retry failed, re-queued: userId={}, channelId={}, remaining={}",
                        event.getUserId(), event.getChannelId(), fallbackQueue.size());

                // Stop on first failure to avoid log spam during Kafka outage
                break;
            }
        }

        if (succeeded > 0) {
            log.info("Fallback retry completed: retried={}, succeeded={}, remaining={}",
                    retried, succeeded, fallbackQueue.size());
        }

        // Track queue size metric
        meterRegistry.gauge("read_receipts.fallback.queue_size", fallbackQueue.size());
    }

    private String buildRedisKey(Long userId, Long channelId) {
        return READ_RECEIPT_KEY_PREFIX + userId + ":" + channelId;
    }
}
