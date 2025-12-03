package com.slack.controller;

import com.slack.dto.websocket.WebSocketMessage;
import com.slack.service.WebSocketMessageService;
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
     * 클라이언트로부터 ACK 메시지를 받아서 처리합니다.
     * 클라이언트는 /app/message.ack로 ACK를 보냅니다.
     * 
     * @param message ACK WebSocket 메시지
     * @param authentication 인증 정보 (JWT에서 추출)
     */
    @MessageMapping("/message.ack")
    public void handleAck(@Payload WebSocketMessage message, Authentication authentication) {
        try {
            webSocketMessageService.handleAck(message, authentication);
        } catch (Exception e) {
            log.error("Error handling ACK", e);
        }
    }

    /**
     * 클라이언트로부터 재전송 요청을 받아서 처리합니다.
     * 클라이언트는 /app/message.resend로 재전송 요청을 보냅니다.
     * 
     * @param message 재전송 요청 WebSocket 메시지 (channelId, lastSequenceNumber 포함)
     * @param authentication 인증 정보 (JWT에서 추출)
     */
    @MessageMapping("/message.resend")
    public void handleResend(@Payload WebSocketMessage message, Authentication authentication) {
        try {
            if (message.getChannelId() == null || message.getSequenceNumber() == null) {
                log.warn("Invalid resend request: channelId={}, sequenceNumber={}", 
                        message.getChannelId(), message.getSequenceNumber());
                return;
            }
            
            webSocketMessageService.resendMissedMessages(
                    message.getChannelId(), 
                    message.getSequenceNumber(), 
                    authentication
            );
        } catch (Exception e) {
            log.error("Error handling resend request", e);
            webSocketMessageService.sendErrorMessage(authentication, "Failed to resend messages: " + e.getMessage());
        }
    }
}

