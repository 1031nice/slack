package com.slack.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 메시지 시퀀스 번호 생성 서비스
 * 
 * Redis를 사용하여 채널별로 고유한 시퀀스 번호를 생성합니다.
 * 멀티 서버 환경에서도 채널별 메시지 순서를 보장합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageSequenceService {

    private final RedisTemplate<String, Object> redisTemplate;
    
    private static final String SEQUENCE_KEY_PREFIX = "slack:sequence:channel:";

    /**
     * 특정 채널의 다음 시퀀스 번호를 생성합니다.
     * Redis INCR을 사용하여 원자적으로 증가시킵니다.
     * 
     * @param channelId 채널 ID
     * @return 새로운 시퀀스 번호
     */
    public Long getNextSequenceNumber(Long channelId) {
        String key = SEQUENCE_KEY_PREFIX + channelId;
        Long sequence = redisTemplate.opsForValue().increment(key);
        
        if (sequence == null) {
            log.error("Failed to generate sequence number for channel: {}", channelId);
            throw new RuntimeException("Failed to generate sequence number");
        }
        
        log.debug("Generated sequence number for channel {}: {}", channelId, sequence);
        return sequence;
    }

    /**
     * 특정 채널의 현재 시퀀스 번호를 반환합니다 (증가하지 않음).
     * 
     * @param channelId 채널 ID
     * @return 현재 시퀀스 번호, 없으면 0
     */
    public Long getCurrentSequenceNumber(Long channelId) {
        String key = SEQUENCE_KEY_PREFIX + channelId;
        Object value = redisTemplate.opsForValue().get(key);
        
        if (value == null) {
            return 0L;
        }
        
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            log.error("Invalid sequence number format for channel {}: {}", channelId, value);
            return 0L;
        }
    }
}

