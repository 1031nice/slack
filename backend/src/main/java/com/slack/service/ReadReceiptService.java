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

    /**
     * Update read receipt for a user in a channel
     * 1. Updates Redis immediately (real-time)
     * 2. Broadcasts to channel members via WebSocket
     * 3. Persists to DB asynchronously (durability)
     *
     * @param userId User ID
     * @param channelId Channel ID
     * @param lastReadSequence Last read sequence number
     */
    @Transactional
    public void updateReadReceipt(Long userId, Long channelId, Long lastReadSequence) {
        if (lastReadSequence == null || lastReadSequence < 0) {
            log.warn("Invalid lastReadSequence: {}", lastReadSequence);
            return;
        }

        // 1. Update Redis (fast, real-time)
        String redisKey = buildRedisKey(userId, channelId);
        redisTemplate.opsForValue().set(redisKey, lastReadSequence.toString());

        log.debug("Updated read receipt in Redis: userId={}, channelId={}, sequence={}",
                userId, channelId, lastReadSequence);

        // 2. Broadcast via WebSocket (real-time notification)
        broadcastReadReceipt(userId, channelId, lastReadSequence);

        // 3. Persist to DB asynchronously (durability, no blocking)
        persistToDatabase(userId, channelId, lastReadSequence);
    }

    /**
     * Get read receipt for a user in a channel
     * 1. Tries Redis first (fast, real-time)
     * 2. Falls back to DB if Redis miss (recovery after restart)
     * 3. Repopulates Redis on DB hit (cache warming)
     *
     * @param userId User ID
     * @param channelId Channel ID
     * @return Last read sequence number, or null if not found
     */
    public Long getReadReceipt(Long userId, Long channelId) {
        String redisKey = buildRedisKey(userId, channelId);

        // 1. Try Redis first (cache hit)
        Object value = redisTemplate.opsForValue().get(redisKey);
        if (value != null) {
            try {
                return Long.parseLong(value.toString());
            } catch (NumberFormatException e) {
                log.error("Invalid read receipt format in Redis: userId={}, channelId={}, value={}",
                        userId, channelId, value);
                // Continue to DB fallback
            }
        }

        // 2. Redis miss - fallback to DB (recovery scenario)
        log.debug("Redis miss for read receipt, fetching from DB: userId={}, channelId={}",
                userId, channelId);

        return readReceiptRepository.findByUserIdAndChannelId(userId, channelId)
                .map(receipt -> {
                    Long sequence = receipt.getLastReadSequence();

                    // 3. Repopulate Redis (cache warming)
                    redisTemplate.opsForValue().set(redisKey, sequence.toString());
                    log.debug("Repopulated Redis from DB: userId={}, channelId={}, sequence={}",
                            userId, channelId, sequence);

                    return sequence;
                })
                .orElse(null);
    }

    /**
     * Get all read receipts for a channel from Redis
     * 
     * @param channelId Channel ID
     * @return Map of userId -> lastReadSequence
     */
    public java.util.Map<Long, Long> getChannelReadReceipts(Long channelId) {
        List<Long> memberIds = channelMemberRepository.findUserIdsByChannelId(channelId);
        
        java.util.Map<Long, Long> readReceipts = new java.util.HashMap<>();
        for (Long memberId : memberIds) {
            Long sequence = getReadReceipt(memberId, channelId);
            if (sequence != null) {
                readReceipts.put(memberId, sequence);
            }
        }
        
        return readReceipts;
    }

    /**
     * Broadcast read receipt to all channel members
     * 
     * @param userId User ID who read the messages
     * @param channelId Channel ID
     * @param lastReadSequence Last read sequence number
     */
    private void broadcastReadReceipt(Long userId, Long channelId, Long lastReadSequence) {
        WebSocketMessage readReceiptMessage = WebSocketMessage.builder()
                .type(WebSocketMessage.MessageType.READ)
                .channelId(channelId)
                .userId(userId)
                .sequenceNumber(lastReadSequence)
                .build();

        String channelDestination = "/topic/channel." + channelId;
        messagingTemplate.convertAndSend(channelDestination, readReceiptMessage);
        
        log.debug("Broadcasted read receipt: userId={}, channelId={}, sequence={}", 
                userId, channelId, lastReadSequence);
    }

    /**
     * Persist read receipt to database asynchronously
     * Uses separate transaction to avoid blocking the main transaction
     * Failures are logged but don't affect user experience (Redis already updated)
     *
     * @param userId User ID
     * @param channelId Channel ID
     * @param lastReadSequence Last read sequence number
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void persistToDatabase(Long userId, Long channelId, Long lastReadSequence) {
        try {
            ReadReceipt receipt = readReceiptRepository
                    .findByUserIdAndChannelId(userId, channelId)
                    .orElseGet(() -> {
                        User user = userRepository.getReferenceById(userId);
                        Channel channel = channelRepository.getReferenceById(channelId);
                        return ReadReceipt.builder()
                                .user(user)
                                .channel(channel)
                                .lastReadSequence(0L)
                                .build();
                    });

            receipt.updateLastReadSequence(lastReadSequence);
            readReceiptRepository.save(receipt);

            log.debug("Persisted read receipt to DB: userId={}, channelId={}, sequence={}",
                    userId, channelId, lastReadSequence);

        } catch (Exception e) {
            log.error("Failed to persist read receipt to DB (Redis already updated): " +
                            "userId={}, channelId={}, sequence={}",
                    userId, channelId, lastReadSequence, e);
            // Redis is already updated, so user experience is not affected
            // DB will be eventually consistent through retry or next update
        }
    }

    private String buildRedisKey(Long userId, Long channelId) {
        return READ_RECEIPT_KEY_PREFIX + userId + ":" + channelId;
    }
}
