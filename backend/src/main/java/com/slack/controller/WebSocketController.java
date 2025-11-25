package com.slack.controller;

import com.slack.domain.user.User;
import com.slack.dto.message.MessageCreateRequest;
import com.slack.dto.message.MessageResponse;
import com.slack.dto.websocket.WebSocketMessage;
import com.slack.service.MessageService;
import com.slack.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class WebSocketController {

    private final MessageService messageService;
    private final UserService userService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 클라이언트로부터 메시지를 받아서 처리합니다.
     * 클라이언트는 /app/message.send로 메시지를 보냅니다.
     * 
     * @param message WebSocket 메시지
     * @param authentication 인증 정보 (JWT에서 추출)
     */
    @MessageMapping("/message.send")
    public void handleMessage(@Payload WebSocketMessage message, Authentication authentication) {
        try {
            log.info("Received message: channelId={}, content={}", message.getChannelId(), message.getContent());
            
            // JWT에서 authUserId 추출 (JWT의 sub 클레임)
            String authUserId = null;
            if (authentication != null && authentication.getPrincipal() instanceof Jwt) {
                Jwt jwt = (Jwt) authentication.getPrincipal();
                authUserId = jwt.getSubject();
            }
            
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
            String destination = "/topic/channel." + message.getChannelId();
            messagingTemplate.convertAndSend(destination, response);
            
            log.info("Broadcasted message to channel {}: messageId={}", message.getChannelId(), savedMessage.getId());
            
        } catch (Exception e) {
            log.error("Error handling message", e);
            
            // 에러 메시지 전송
            WebSocketMessage errorMessage = WebSocketMessage.builder()
                    .type(WebSocketMessage.MessageType.ERROR)
                    .content("Failed to send message: " + e.getMessage())
                    .build();
            
            String userDestination = authentication != null 
                    ? "/queue/errors." + authentication.getName()
                    : "/queue/errors";
            messagingTemplate.convertAndSend(userDestination, errorMessage);
        }
    }
}

