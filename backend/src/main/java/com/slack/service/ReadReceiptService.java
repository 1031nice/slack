package com.slack.service;

import com.slack.domain.channel.Channel;
import com.slack.domain.readreceipt.ReadReceipt;
import com.slack.domain.user.User;
import com.slack.dto.websocket.WebSocketMessage;
import com.slack.repository.ChannelMemberRepository;
import com.slack.repository.ReadReceiptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * Read receipt service for tracking and broadcasting read status
 * 
 * Uses Redis for real-time storage and PostgreSQL for persistence (v0.4.1)
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ReadReceiptService {

    private static final String READ_RECEIPT_KEY_PREFIX = "read_receipt:";
    
    private final ReadReceiptRepository readReceiptRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Update read receipt for a user in a channel
     * Stores in Redis for real-time access and broadcasts to channel members
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

        // Store in Redis for real-time access
        String redisKey = buildRedisKey(userId, channelId);
        redisTemplate.opsForValue().set(redisKey, lastReadSequence.toString());
        
        log.debug("Updated read receipt in Redis: userId={}, channelId={}, sequence={}", 
                userId, channelId, lastReadSequence);

        // Broadcast to channel members
        broadcastReadReceipt(userId, channelId, lastReadSequence);
    }

    /**
     * Get read receipt from Redis (real-time)
     * 
     * @param userId User ID
     * @param channelId Channel ID
     * @return Last read sequence number, or null if not found
     */
    public Long getReadReceipt(Long userId, Long channelId) {
        String redisKey = buildRedisKey(userId, channelId);
        Object value = redisTemplate.opsForValue().get(redisKey);
        
        if (value == null) {
            return null;
        }
        
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            log.error("Invalid read receipt format: userId={}, channelId={}, value={}", 
                    userId, channelId, value);
            return null;
        }
    }

    /**
     * Get all read receipts for a channel from Redis
     * 
     * @param channelId Channel ID
     * @return Map of userId -> lastReadSequence
     */
    public java.util.Map<Long, Long> getChannelReadReceipts(Long channelId) {
        // Get all channel member IDs
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
        // Get all channel member IDs
        List<Long> memberIds = channelMemberRepository.findUserIdsByChannelId(channelId);
        
        // Create read receipt message
        WebSocketMessage readReceiptMessage = WebSocketMessage.builder()
                .type(WebSocketMessage.MessageType.READ)
                .channelId(channelId)
                .userId(userId)
                .sequenceNumber(lastReadSequence)
                .build();

        // Broadcast to channel topic
        String channelDestination = "/topic/channel." + channelId;
        messagingTemplate.convertAndSend(channelDestination, readReceiptMessage);
        
        log.debug("Broadcasted read receipt: userId={}, channelId={}, sequence={}", 
                userId, channelId, lastReadSequence);
    }

    private String buildRedisKey(Long userId, Long channelId) {
        return READ_RECEIPT_KEY_PREFIX + userId + ":" + channelId;
    }
}
