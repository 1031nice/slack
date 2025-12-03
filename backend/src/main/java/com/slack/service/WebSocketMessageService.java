package com.slack.service;

import com.slack.domain.user.User;
import com.slack.dto.message.MessageCreateRequest;
import com.slack.dto.message.MessageResponse;
import com.slack.dto.websocket.WebSocketMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketMessageService {

    private final MessageService messageService;
    private final UserService userService;
    private final SimpMessagingTemplate messagingTemplate;
    private final RedisMessagePublisher redisMessagePublisher;
    private final MessageSequenceService sequenceService;

    /**
     * WebSocket을 통해 받은 메시지를 처리하고 브로드캐스팅합니다.
     * 
     * @param message WebSocket 메시지
     * @param authentication 인증 정보 (JWT에서 추출)
     * @return 처리된 WebSocket 메시지
     * @throws IllegalArgumentException 인증 정보가 없거나 유효하지 않은 경우
     */
    public WebSocketMessage handleIncomingMessage(WebSocketMessage message, Authentication authentication) {
        log.info("Received message: channelId={}, content={}", message.getChannelId(), message.getContent());

        // JWT에서 authUserId 추출
        String authUserId = extractAuthUserId(authentication);
        if (authUserId == null) {
            throw new IllegalArgumentException("Authentication required");
        }

        // User 조회
        User user = userService.findByAuthUserId(authUserId);

        // 메시지 생성 요청
        MessageCreateRequest createRequest = MessageCreateRequest.builder()
                .userId(user.getId())
                .content(message.getContent())
                .build();

        // DB에 메시지 저장
        MessageResponse savedMessage = messageService.createMessage(message.getChannelId(), createRequest);

        // 채널별 시퀀스 번호 생성
        Long sequenceNumber = sequenceService.getNextSequenceNumber(message.getChannelId());

        // WebSocket 메시지 생성
        WebSocketMessage response = WebSocketMessage.builder()
                .type(WebSocketMessage.MessageType.MESSAGE)
                .channelId(savedMessage.getChannelId())
                .messageId(savedMessage.getId())
                .userId(savedMessage.getUserId())
                .content(savedMessage.getContent())
                .createdAt(savedMessage.getCreatedAt().toString())
                .sequenceNumber(sequenceNumber)
                .build();

        // 해당 채널의 모든 구독자에게 브로드캐스팅
        broadcastToChannel(message.getChannelId(), response);

        log.info("Broadcasted message to channel {}: messageId={}", message.getChannelId(), savedMessage.getId());

        return response;
    }

    /**
     * 에러 메시지를 특정 사용자에게 전송합니다.
     * 
     * @param authentication 인증 정보
     * @param errorMessage 에러 메시지
     */
    public void sendErrorMessage(Authentication authentication, String errorMessage) {
        WebSocketMessage error = WebSocketMessage.builder()
                .type(WebSocketMessage.MessageType.ERROR)
                .content("Failed to send message: " + errorMessage)
                .build();

        String userDestination = authentication != null
                ? "/queue/errors." + authentication.getName()
                : "/queue/errors";
        messagingTemplate.convertAndSend(userDestination, error);
    }

    /**
     * 메시지를 특정 채널의 모든 구독자에게 브로드캐스팅합니다.
     * Redis Pub/Sub을 통해 다른 서버에도 메시지를 전달합니다.
     * 
     * @param channelId 채널 ID
     * @param message 브로드캐스팅할 메시지
     */
    public void broadcastToChannel(Long channelId, WebSocketMessage message) {
        // Redis로 메시지 발행 (다른 서버들이 수신)
        redisMessagePublisher.publish(message);
        
        // 로컬 WebSocket 클라이언트에게도 브로드캐스팅
        // (RedisMessageSubscriber가 다른 서버에서 발행한 메시지를 수신하여 처리)
        String destination = "/topic/channel." + channelId;
        messagingTemplate.convertAndSend(destination, message);
    }

    /**
     * ACK 메시지를 처리합니다.
     * 클라이언트가 메시지를 수신했음을 확인합니다.
     * 
     * @param message ACK 메시지
     * @param authentication 인증 정보
     */
    public void handleAck(WebSocketMessage message, Authentication authentication) {
        if (message.getType() != WebSocketMessage.MessageType.ACK) {
            log.warn("Received non-ACK message in handleAck: type={}", message.getType());
            return;
        }

        log.debug("Received ACK: ackId={}, sequenceNumber={}", 
                message.getAckId(), message.getSequenceNumber());
        
        // TODO: v0.3 후속 작업에서 ACK 기반 재전송 로직 구현
        // 현재는 ACK를 받았다는 것만 로깅
    }

    /**
     * Authentication에서 authUserId를 추출합니다.
     * 
     * @param authentication 인증 정보
     * @return authUserId (JWT의 sub 클레임), 없으면 null
     */
    private String extractAuthUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt)) {
            return null;
        }
        Jwt jwt = (Jwt) authentication.getPrincipal();
        return jwt.getSubject();
    }
}

