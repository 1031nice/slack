package com.slack.common.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.websocket.dto.WebSocketMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis Pub/Sub message subscriber service
 * Receives messages from other servers and broadcasts to local WebSocket clients
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisMessageSubscriber implements MessageListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String jsonMessage = new String(message.getBody());
            WebSocketMessage webSocketMessage = objectMapper.readValue(jsonMessage, WebSocketMessage.class);
            if (webSocketMessage.getChannelId() != null) {
                String destination = "/topic/channel." + webSocketMessage.getChannelId();
                messagingTemplate.convertAndSend(destination, webSocketMessage);
                log.debug("Broadcasted message from Redis to local clients: channelId={}, messageId={}", 
                        webSocketMessage.getChannelId(), webSocketMessage.getMessageId());
            }
        } catch (Exception e) {
            log.error("Failed to process message from Redis", e);
        }
    }
}

