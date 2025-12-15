package com.slack.service;

import com.slack.exception.SequenceGenerationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Service for generating message sequence numbers using Redis.
 * Ensures message ordering per channel in multi-server environments.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageSequenceService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String SEQUENCE_KEY_PREFIX = "slack:sequence:channel:";

    /**
     * Generate next sequence number for a channel.
     * Uses Redis INCR for atomic increment.
     *
     * @param channelId channel ID
     * @return next sequence number (starts from 1)
     * @throws IllegalArgumentException if channelId is invalid
     * @throws SequenceGenerationException if sequence generation fails
     */
    public Long getNextSequenceNumber(Long channelId) {
        validateChannelId(channelId);

        String key = buildKey(channelId);
        Long sequence = redisTemplate.opsForValue().increment(key);

        if (sequence == null) {
            log.error("Failed to generate sequence number for channel: {}", channelId);
            throw new SequenceGenerationException(
                "Failed to generate sequence number for channel: " + channelId
            );
        }

        log.debug("Generated sequence number for channel {}: {}", channelId, sequence);
        return sequence;
    }

    private void validateChannelId(Long channelId) {
        if (channelId == null || channelId <= 0) {
            throw new IllegalArgumentException("Invalid channel ID: " + channelId);
        }
    }

    private String buildKey(Long channelId) {
        return SEQUENCE_KEY_PREFIX + channelId;
    }
}

