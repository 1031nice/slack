package com.slack.service;

import com.slack.domain.channel.Channel;
import com.slack.domain.readreceipt.ReadReceipt;
import com.slack.domain.user.User;
import com.slack.dto.websocket.WebSocketMessage;
import com.slack.repository.ChannelMemberRepository;
import com.slack.repository.ChannelRepository;
import com.slack.repository.ReadReceiptRepository;
import com.slack.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
    private final UserRepository userRepository;
    private final ChannelRepository channelRepository;
    private final PermissionService permissionService;

    /**
     * Update read receipt for a user in a channel
     * 1. Updates Redis immediately (real-time)
     * 2. Broadcasts to channel members via WebSocket
     * 3. Persists to DB asynchronously (durability)
     *
     * @param userId User ID
     * @param channelId Channel ID
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

        // 3. Persist to DB asynchronously (durability, no blocking)
        persistToDatabase(userId, channelId, lastReadTimestamp);
    }

    /**
     * Get read receipt for a user in a channel
     * 1. Tries Redis first (fast, real-time)
     * 2. Falls back to DB if Redis miss (recovery after restart)
     * 3. Repopulates Redis on DB hit (cache warming)
     *
     * @param userId User ID
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
     *
     * @param channelId Channel ID
     * @param userId user ID requesting the read receipts
     * @return Map of userId -> lastReadTimestamp
     */
    public java.util.Map<Long, String> getChannelReadReceipts(Long channelId, Long userId) {
        // Authorization: must have channel access
        permissionService.requireChannelAccess(userId, channelId);

        List<Long> memberIds = channelMemberRepository.findUserIdsByChannelId(channelId);

        java.util.Map<Long, String> readReceipts = new java.util.HashMap<>();
        for (Long memberId : memberIds) {
            String timestamp = getReadReceipt(memberId, channelId);
            if (timestamp != null) {
                readReceipts.put(memberId, timestamp);
            }
        }

        return readReceipts;
    }

    /**
     * Broadcast read receipt to all channel members
     *
     * @param userId User ID who read the messages
     * @param channelId Channel ID
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
     * Persist read receipt to database asynchronously
     * Uses separate transaction to avoid blocking the main transaction
     * Failures are logged but don't affect user experience (Redis already updated)
     *
     * @param userId User ID
     * @param channelId Channel ID
     * @param lastReadTimestamp Last read timestamp
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void persistToDatabase(Long userId, Long channelId, String lastReadTimestamp) {
        try {
            ReadReceipt receipt = readReceiptRepository
                    .findByUserIdAndChannelId(userId, channelId)
                    .orElseGet(() -> {
                        User user = userRepository.getReferenceById(userId);
                        Channel channel = channelRepository.getReferenceById(channelId);
                        return ReadReceipt.builder()
                                .user(user)
                                .channel(channel)
                                .lastReadTimestamp("0")  // Initial timestamp
                                .build();
                    });

            receipt.updateLastReadTimestamp(lastReadTimestamp);
            readReceiptRepository.save(receipt);

            log.debug("Persisted read receipt to DB: userId={}, channelId={}, timestamp={}",
                    userId, channelId, lastReadTimestamp);

        } catch (Exception e) {
            log.error("Failed to persist read receipt to DB (Redis already updated): " +
                            "userId={}, channelId={}, timestamp={}",
                    userId, channelId, lastReadTimestamp, e);
            // Redis is already updated, so user experience is not affected
            // DB will be eventually consistent through retry or next update
        }
    }

    private String buildRedisKey(Long userId, Long channelId) {
        return READ_RECEIPT_KEY_PREFIX + userId + ":" + channelId;
    }
}
