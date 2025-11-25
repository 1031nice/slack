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

        // WebSocket 메시지 생성
        WebSocketMessage response = WebSocketMessage.builder()
                .type(WebSocketMessage.MessageType.MESSAGE)
                .channelId(savedMessage.getChannelId())
                .messageId(savedMessage.getId())
                .userId(savedMessage.getUserId())
                .content(savedMessage.getContent())
                .createdAt(savedMessage.getCreatedAt().toString())
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
     * 
     * @param channelId 채널 ID
     * @param message 브로드캐스팅할 메시지
     */
    public void broadcastToChannel(Long channelId, WebSocketMessage message) {
        String destination = "/topic/channel." + channelId;
        messagingTemplate.convertAndSend(destination, message);
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

