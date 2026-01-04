package com.slack.websocket.controller;

import com.slack.common.exception.WrongServerException;
import com.slack.common.service.ChannelRoutingService;
import com.slack.user.domain.User;
import com.slack.websocket.dto.WebSocketMessage;
import com.slack.websocket.service.WebSocketMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class WebSocketController {

    private final WebSocketMessageService webSocketMessageService;
    private final ChannelRoutingService channelRoutingService;

    /**
     * 클라이언트로부터 메시지를 받아서 처리합니다.
     * 클라이언트는 /app/message.send로 메시지를 보냅니다.
     *
     * Channel Partitioning: This server validates that it is responsible for
     * the given channel. If not, throws WrongServerException to alert the client.
     *
     * @param message WebSocket 메시지
     * @param user 인증된 사용자
     */
    @MessageMapping("/message.send")
    public void handleMessage(@Payload WebSocketMessage message, @AuthenticationPrincipal User user) {
        try {
            // Validate that this server should handle this channel
            if (message.getChannelId() != null && !channelRoutingService.isResponsibleFor(message.getChannelId())) {
                int expectedServer = channelRoutingService.getServerForChannel(message.getChannelId());
                int actualServer = channelRoutingService.getServerId();

                log.warn("Message for channel {} routed to wrong server. Expected: {}, Actual: {}",
                    message.getChannelId(), expectedServer, actualServer);

                throw new WrongServerException(message.getChannelId(), expectedServer, actualServer);
            }

            webSocketMessageService.handleIncomingMessage(message, user.getAuthUserId());
        } catch (WrongServerException e) {
            log.error("Wrong server routing error", e);
            webSocketMessageService.sendErrorMessage(user.getAuthUserId(),
                "This message should be handled by server " + e.getExpectedServerId() + ". Please reconnect.");
        } catch (Exception e) {
            log.error("Error handling message", e);
            webSocketMessageService.sendErrorMessage(user.getAuthUserId(), e.getMessage());
        }
    }

    /**
     * 클라이언트로부터 재전송 요청을 받아서 처리합니다.
     * 클라이언트는 /app/message.resend로 재전송 요청을 보냅니다.
     *
     * @param message 재전송 요청 WebSocket 메시지 (channelId + createdAt)
     * @param user 인증된 사용자
     */
    @MessageMapping("/message.resend")
    public void handleResend(@Payload WebSocketMessage message, @AuthenticationPrincipal User user) {
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
                    user.getAuthUserId()
            );
        } catch (Exception e) {
            log.error("Error handling resend request", e);
            webSocketMessageService.sendErrorMessage(user.getAuthUserId(), "Failed to resend messages: " + e.getMessage());
        }
    }

    /**
     * 클라이언트로부터 읽음 처리 요청을 받아서 처리합니다.
     * 클라이언트는 /app/message.read로 읽음 처리를 보냅니다.
     *
     * @param message 읽음 처리 WebSocket 메시지 (channelId, createdAt 포함)
     * @param user 인증된 사용자
     */
    @MessageMapping("/message.read")
    public void handleRead(@Payload WebSocketMessage message, @AuthenticationPrincipal User user) {
        try {
            if (message.getChannelId() == null) {
                log.warn("Invalid read request: channelId is null");
                return;
            }

            webSocketMessageService.handleRead(message, user.getAuthUserId());
        } catch (Exception e) {
            log.error("Error handling read request", e);
            webSocketMessageService.sendErrorMessage(user.getAuthUserId(), "Failed to process read receipt: " + e.getMessage());
        }
    }
}

