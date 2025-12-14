package com.slack.service;

import com.slack.repository.ChannelMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisCallback;
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
 *
 * @see <a href="../../../../../../docs/adr/0003-redis-zset-for-unread-counts.md">ADR-0003: Redis ZSET for Unread Counts</a>
 */
@Service
@RequiredArgsConstructor
public class UnreadCountService {

    private static final String UNREAD_KEY_PREFIX = "unread:";
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final ChannelMemberRepository channelMemberRepository;

    /**
     * Get unread count for a user in a channel.
     * Uses ZCARD for O(1) performance.
     */
    public long getUnreadCount(Long userId, Long channelId) {
        String key = buildKey(userId, channelId);
        Long count = redisTemplate.opsForZSet().zCard(key);
        return count != null ? count : 0L;
    }

    /**
     * Add unread message for all channel members except the sender.
     * Uses Redis Pipeline to batch operations and reduce network round trips.
     *
     * Performance: O(N) where N = number of channel members
     * Network calls: 1 (pipelined) instead of N (individual)
     */
    public void incrementUnreadCount(Long channelId, Long messageId, Long senderId, long timestamp) {
        List<Long> memberIds = channelMemberRepository.findUserIdsByChannelId(channelId);

        if (memberIds == null || memberIds.isEmpty()) {
            return;
        }

        String messageIdStr = messageId.toString();

        // Use pipeline to batch all ZADD operations into a single network call
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            memberIds.stream()
                    .filter(memberId -> !memberId.equals(senderId))
                    .forEach(memberId -> {
                        String key = buildKey(memberId, channelId);
                        redisTemplate.opsForZSet().add(key, messageIdStr, timestamp);
                    });
            return null;
        });
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
     * Get unread message IDs for a user in a channel, sorted by timestamp.
     * Returns message IDs in chronological order (oldest first).
     */
    public Set<String> getUnreadMessageIds(Long userId, Long channelId) {
        String key = buildKey(userId, channelId);
        Set<Object> result = redisTemplate.opsForZSet().range(key, 0, -1);

        if (result == null || result.isEmpty()) {
            return Set.of();
        }

        return result.stream()
                .map(Object::toString)
                .collect(java.util.stream.Collectors.toSet());
    }

    private String buildKey(Long userId, Long channelId) {
        return UNREAD_KEY_PREFIX + userId + ":" + channelId;
    }
}
