package com.slack.unread.service;

import com.slack.channel.domain.Channel;
import com.slack.channel.domain.ChannelType;
import com.slack.channel.repository.ChannelMemberRepository;
import com.slack.channel.repository.ChannelRepository;
import com.slack.workspace.repository.WorkspaceMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final ChannelRepository channelRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;

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
     * For PUBLIC channels: includes all workspace members
     * For PRIVATE channels: includes only ChannelMember entries
     *
     * Performance: O(N) where N = number of channel members
     * Network calls: 1 (pipelined) instead of N (individual)
     */
    public void incrementUnreadCount(Long channelId, Long messageId, Long senderId, long timestamp) {
        Channel channel = channelRepository.findById(channelId).orElse(null);
        if (channel == null) {
            return;
        }

        Set<Long> memberIds = new HashSet<>();

        if (channel.getType() == ChannelType.PUBLIC) {
            // PUBLIC channel: get all workspace members
            List<Long> workspaceMemberIds = workspaceMemberRepository.findByWorkspaceId(channel.getWorkspace().getId()).stream()
                    .map(wm -> wm.getUser().getId())
                    .collect(Collectors.toList());
            memberIds.addAll(workspaceMemberIds);
        } else {
            // PRIVATE channel: get only ChannelMember entries
            List<Long> channelMemberIds = channelMemberRepository.findUserIdsByChannelId(channelId);
            memberIds.addAll(channelMemberIds);
        }

        if (memberIds.isEmpty()) {
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

    /**
     * Get unread message IDs for a user in a channel, sorted by timestamp.
     * Uses ZREVRANGE for descending (newest first) or ZRANGE for ascending (oldest first).
     *
     * @param userId User ID
     * @param channelId Channel ID
     * @param descending If true, returns newest first (ZREVRANGE), otherwise oldest first (ZRANGE)
     * @return Set of message IDs as strings, sorted by timestamp
     */
    public Set<String> getUnreadMessageIdsSorted(Long userId, Long channelId, boolean descending) {
        return getUnreadMessageIdsSorted(userId, channelId, descending, -1);
    }

    /**
     * Get unread message IDs for a user in a channel, sorted by timestamp with limit.
     * Uses ZREVRANGE for descending (newest first) or ZRANGE for ascending (oldest first).
     *
     * @param userId User ID
     * @param channelId Channel ID
     * @param descending If true, returns newest first (ZREVRANGE), otherwise oldest first (ZRANGE)
     * @param limit Maximum number of message IDs to return (-1 for all)
     * @return Set of message IDs as strings, sorted by timestamp
     */
    public Set<String> getUnreadMessageIdsSorted(Long userId, Long channelId, boolean descending, int limit) {
        String key = buildKey(userId, channelId);
        Set<Object> result;
        long end = limit > 0 ? limit - 1 : -1;

        if (descending) {
            // ZREVRANGE: newest first (descending by timestamp)
            result = redisTemplate.opsForZSet().reverseRange(key, 0, end);
        } else {
            // ZRANGE: oldest first (ascending by timestamp)
            result = redisTemplate.opsForZSet().range(key, 0, end);
        }

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
