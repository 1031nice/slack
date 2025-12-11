package com.slack.service;

import com.slack.repository.ChannelMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * Unread count tracking service using Redis Sorted Set
 * 
 * Key format: `unread:{userId}:{channelId}`
 * Members: messageIds (as strings)
 * Scores: timestamps (milliseconds since epoch)
 * 
 * Unread count = size of the sorted set
 * O(1) increment on new message, O(1) clear on read
 */
@Service
@RequiredArgsConstructor
public class UnreadCountService {

    private static final String UNREAD_KEY_PREFIX = "unread:";
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final ChannelMemberRepository channelMemberRepository;

    /**
     * Get unread count for a user in a channel
     * 
     * @param userId User ID
     * @param channelId Channel ID
     * @return Unread count (number of unread messages)
     */
    public long getUnreadCount(Long userId, Long channelId) {
        String key = buildKey(userId, channelId);
        Long count = redisTemplate.opsForZSet().count(key, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
        return count != null ? count : 0L;
    }

    /**
     * Increment unread count for all channel members except the message sender
     * 
     * @param channelId Channel ID
     * @param messageId Message ID
     * @param senderId User ID who sent the message (excluded from unread count)
     * @param timestamp Message timestamp (milliseconds since epoch)
     */
    public void incrementUnreadCount(Long channelId, Long messageId, Long senderId, long timestamp) {
        // Get all channel member IDs
        List<Long> memberIds = channelMemberRepository.findUserIdsByChannelId(channelId);
        
        // Add messageId to unread count for all members except sender
        String messageIdStr = messageId.toString();
        for (Long memberId : memberIds) {
            if (!memberId.equals(senderId)) {
                String key = buildKey(memberId, channelId);
                redisTemplate.opsForZSet().add(key, messageIdStr, timestamp);
            }
        }
    }

    /**
     * Clear unread count for a user in a channel
     * 
     * @param userId User ID
     * @param channelId Channel ID
     */
    public void clearUnreadCount(Long userId, Long channelId) {
        String key = buildKey(userId, channelId);
        redisTemplate.delete(key);
    }

    /**
     * Get unread message IDs for a user in a channel
     * 
     * @param userId User ID
     * @param channelId Channel ID
     * @return Set of message IDs (as strings)
     */
    public Set<Object> getUnreadMessageIds(Long userId, Long channelId) {
        String key = buildKey(userId, channelId);
        return redisTemplate.opsForZSet().range(key, 0, -1);
    }

    private String buildKey(Long userId, Long channelId) {
        return UNREAD_KEY_PREFIX + userId + ":" + channelId;
    }
}
