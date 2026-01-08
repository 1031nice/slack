package com.slack.common.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.websocket.dto.WebSocketMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Service;

/**
 * Redis Pub/Sub message publisher service
 * Publishes messages to Redis for other servers to receive
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisMessagePublisher {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ChannelTopic messageTopic;
    private final ObjectMapper objectMapper;

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

