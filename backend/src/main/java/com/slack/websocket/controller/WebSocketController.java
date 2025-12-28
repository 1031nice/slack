package com.slack.websocket.controller;

import com.slack.websocket.dto.WebSocketMessage;
import com.slack.websocket.service.WebSocketMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class WebSocketController {

    private final WebSocketMessageService webSocketMessageService;

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
            webSocketMessageService.handleIncomingMessage(message, authentication);
        } catch (Exception e) {
            log.error("Error handling message", e);
            webSocketMessageService.sendErrorMessage(authentication, e.getMessage());
        }
    }

    /**
     * 클라이언트로부터 재전송 요청을 받아서 처리합니다.
     * 클라이언트는 /app/message.resend로 재전송 요청을 보냅니다.
     *
     * @param message 재전송 요청 WebSocket 메시지 (channelId + createdAt)
     * @param authentication 인증 정보 (JWT에서 추출)
     */
    @MessageMapping("/message.resend")
    public void handleResend(@Payload WebSocketMessage message, Authentication authentication) {
        try {
            if (message.getChannelId() == null) {
                log.warn("Invalid resend request: channelId is null");
                return;
            }

            if (message.getCreatedAt() == null || message.getCreatedAt().trim().isEmpty()) {
                log.warn("Invalid resend request: createdAt is missing for channelId={}", message.getChannelId());
                return;
            }

            log.info("Handling timestamp-based resend for channel {}", message.getChannelId());
            webSocketMessageService.resendMissedMessagesByTimestamp(
                    message.getChannelId(),
                    message.getCreatedAt(),
                    authentication
            );
        } catch (Exception e) {
            log.error("Error handling resend request", e);
            webSocketMessageService.sendErrorMessage(authentication, "Failed to resend messages: " + e.getMessage());
        }
    }

    /**
     * 클라이언트로부터 읽음 처리 요청을 받아서 처리합니다.
     * 클라이언트는 /app/message.read로 읽음 처리를 보냅니다.
     *
     * @param message 읽음 처리 WebSocket 메시지 (channelId, createdAt 포함)
     * @param authentication 인증 정보 (JWT에서 추출)
     */
    @MessageMapping("/message.read")
    public void handleRead(@Payload WebSocketMessage message, Authentication authentication) {
        try {
            if (message.getChannelId() == null) {
                log.warn("Invalid read request: channelId is null");
                return;
            }

            webSocketMessageService.handleRead(message, authentication);
        } catch (Exception e) {
            log.error("Error handling read request", e);
            webSocketMessageService.sendErrorMessage(authentication, "Failed to process read receipt: " + e.getMessage());
        }
    }
}

