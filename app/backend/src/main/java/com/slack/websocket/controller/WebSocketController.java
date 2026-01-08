package com.slack.websocket.controller;

import com.slack.websocket.dto.WebSocketMessage;
import com.slack.websocket.service.WebSocketMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.security.Principal;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class WebSocketController {

    private final WebSocketMessageService webSocketMessageService;

    @MessageMapping("/message.send")
    public void handleMessage(Principal principal, @Payload WebSocketMessage message) {
        String authUserId = principal != null ? principal.getName() : null;
        try {
            if (authUserId == null) {
                log.error("authUserId is null in handleMessage (Principal is null)");
                throw new IllegalStateException("User not authenticated");
            }
            log.info("[DEBUG] Handling message from authUserId: {}", authUserId);
            webSocketMessageService.handleIncomingMessage(message, authUserId);
        } catch (Exception e) {
            log.error("Error handling message", e);
            if (authUserId != null) {
                webSocketMessageService.sendErrorMessage(authUserId, e.getMessage());
            }
        }
    }

    @MessageMapping("/message.resend")
    public void handleResend(Principal principal, @Payload WebSocketMessage message) {
        String authUserId = principal != null ? principal.getName() : null;
        try {
            if (authUserId == null) {
                 log.error("authUserId is null in handleResend (Principal is null)");
                 return;
            }

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
                    authUserId
            );
        } catch (Exception e) {
            log.error("Error handling resend request", e);
            webSocketMessageService.sendErrorMessage(authUserId, "Failed to resend messages: " + e.getMessage());
        }
    }

    @MessageMapping("/message.read")
    public void handleRead(Principal principal, @Payload WebSocketMessage message) {
        String authUserId = principal != null ? principal.getName() : null;
        try {
             if (authUserId == null) {
                 log.error("authUserId is null in handleRead (Principal is null)");
                 return;
            }

            if (message.getChannelId() == null) {
                log.warn("Invalid read request: channelId is null");
                return;
            }

            webSocketMessageService.handleRead(message, authUserId);
        } catch (Exception e) {
            log.error("Error handling read request", e);
            webSocketMessageService.sendErrorMessage(authUserId, "Failed to process read receipt: " + e.getMessage());
        }
    }
}

