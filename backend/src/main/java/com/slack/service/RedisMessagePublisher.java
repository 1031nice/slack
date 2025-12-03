package com.slack.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.dto.websocket.WebSocketMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Service;

/**
 * Redis Pub/Sub 메시지 발행 서비스
 * 
 * 로컬 서버에서 생성된 메시지를 Redis로 발행하여
 * 다른 서버들이 수신할 수 있도록 합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisMessagePublisher {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ChannelTopic messageTopic;
    private final ObjectMapper objectMapper;

    /**
     * WebSocket 메시지를 Redis로 발행합니다.
     * 다른 서버들이 이 메시지를 수신하여 로컬 WebSocket 클라이언트에게 전달합니다.
     * 
     * @param message 발행할 WebSocket 메시지
     */
    public void publish(WebSocketMessage message) {
        try {
            String jsonMessage = objectMapper.writeValueAsString(message);
            redisTemplate.convertAndSend(messageTopic.getTopic(), jsonMessage);
            log.debug("Published message to Redis: channelId={}, messageId={}", 
                    message.getChannelId(), message.getMessageId());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize message for Redis", e);
            throw new RuntimeException("Failed to publish message to Redis", e);
        }
    }
}

