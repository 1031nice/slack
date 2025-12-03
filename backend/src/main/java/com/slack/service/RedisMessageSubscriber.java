package com.slack.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.dto.websocket.WebSocketMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis Pub/Sub 메시지 구독 서비스
 * 
 * 다른 서버에서 발행한 메시지를 수신하여
 * 로컬 WebSocket 클라이언트에게 브로드캐스팅합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisMessageSubscriber implements MessageListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Redis에서 수신한 메시지를 처리합니다.
     * MessageListenerAdapter를 통해 호출됩니다.
     * 
     * @param message Redis 메시지
     * @param pattern 채널 패턴
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String jsonMessage = new String(message.getBody());
            WebSocketMessage webSocketMessage = objectMapper.readValue(jsonMessage, WebSocketMessage.class);
            
            // 로컬 WebSocket 클라이언트에게 브로드캐스팅
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

