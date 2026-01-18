package com.slack.websocket.service;

import com.slack.user.domain.User;
import com.slack.message.dto.MessageCreateRequest;
import com.slack.message.dto.MessageResponse;
import com.slack.websocket.dto.WebSocketMessage;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import com.slack.message.service.MessageService;
import com.slack.user.service.UserService;
import com.slack.common.service.RedisMessagePublisher;
import com.slack.readreceipt.service.ReadReceiptService;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketMessageService {

    private final MessageService messageService;
    private final UserService userService;
    private final SimpMessagingTemplate messagingTemplate;
    private final RedisMessagePublisher redisMessagePublisher;
    private final ReadReceiptService readReceiptService;

    /**
     * Processes incoming WebSocket message and broadcasts to channel.
     *
     * ARCHITECTURAL NOTE: In a real distributed system (Slack), this "Logic" would not handle
     * WebSocket connections directly.
     * - Real Slack: Client -> [Gateway Server] -> gRPC Stream -> [Logic Server]
     * - Current: Client -> [Monolith (Spring STOMP)] -> Logic
     * We skipped implementing a separate Gateway server to reduce infra complexity,
     * but simulating the split via "Deep Dive 04" logic.
     *
     * @param message WebSocket message
     * @param authUserId Authenticated user's auth ID
     */
    public WebSocketMessage handleIncomingMessage(WebSocketMessage message, String authUserId) {
        log.info("Received message: channelId={}, content={}", message.getChannelId(), message.getContent());

        User user = userService.findByAuthUserId(authUserId);

        // Messages are ordered by timestampId (generated in MessageService)
        MessageCreateRequest createRequest = MessageCreateRequest.builder()
                .userId(user.getId())
                .content(message.getContent())
                .build();

        MessageResponse savedMessage = messageService.createMessage(message.getChannelId(), createRequest);
        WebSocketMessage response = toWebSocketMessage(savedMessage);
        broadcastToChannel(response);

        log.info("Broadcasted message to channel {}: messageId={}", message.getChannelId(), savedMessage.getId());

        return response;
    }

    /**
     * Sends error message to user.
     *
     * @param username Username for error destination
     * @param errorMessage Error message content
     */
    public void sendErrorMessage(String username, String errorMessage) {
        WebSocketMessage error = WebSocketMessage.builder()
                .type(WebSocketMessage.MessageType.ERROR)
                .content("Failed to send message: " + errorMessage)
                .build();

        String userDestination = "/queue/errors." + username;
        messagingTemplate.convertAndSend(userDestination, error);
    }

    /**
     * Broadcasts message to all channel subscribers via Redis Pub/Sub.
     *
     * ARCHITECTURAL NOTE: Redis Pub/Sub has O(N) bandwidth cost for N subscribers.
     * - Real Slack: Uses "Flannel" (Application-level Edge Cache) to multicast messages
     *   directly to Gateway servers, bypassing Redis for payload transport.
     * - Current: We use Redis Pub/Sub for simplicity (Tier 1 Architecture).
     *   See Deep Dive 03 for the limits of this approach.
     */
    public void broadcastToChannel(WebSocketMessage message) {
        redisMessagePublisher.publish(message);
    }

    /**
     * Resends messages missed since last timestamp on reconnection.
     *
     * @param channelId Channel ID
     * @param lastTimestamp Last received timestamp
     * @param authUserId Authenticated user's auth ID
     */
    public void resendMissedMessagesByTimestamp(Long channelId, String lastTimestamp, String authUserId) {
        log.info("Resending missed messages for channel {} after timestamp {}", channelId, lastTimestamp);

        List<MessageResponse> missedMessages = messageService.getMessagesAfterTimestamp(channelId, lastTimestamp);

        if (missedMessages.isEmpty()) {
            log.debug("No missed messages found for channel {} after timestamp {}", channelId, lastTimestamp);
            return;
        }

        log.info("Found {} missed messages for channel {}", missedMessages.size(), channelId);

        String userDestination = "/queue/resend." + authUserId;

        for (MessageResponse msg : missedMessages) {
            WebSocketMessage webSocketMessage = toWebSocketMessage(msg);
            messagingTemplate.convertAndSend(userDestination, webSocketMessage);
        }
    }

    /**
     * Handles READ message to update user's read receipt for channel.
     *
     * @param message WebSocket message with channelId and timestamp
     * @param authUserId Authenticated user's auth ID
     */
    public void handleRead(WebSocketMessage message, String authUserId) {
        if (!validateMessageType(message, WebSocketMessage.MessageType.READ, "handleRead")) {
            return;
        }

        User user = userService.findByAuthUserId(authUserId);

        // Use createdAt (timestamp) for read receipt
        String timestamp = message.getCreatedAt();
        if (timestamp == null || timestamp.trim().isEmpty()) {
            log.warn("Invalid read receipt: missing timestamp for channelId={}", message.getChannelId());
            return;
        }

        log.debug("Processing read receipt: userId={}, channelId={}, timestamp={}",
                user.getId(), message.getChannelId(), timestamp);

        readReceiptService.updateReadReceipt(
                user.getId(),
                message.getChannelId(),
                timestamp
        );
    }

    private WebSocketMessage toWebSocketMessage(MessageResponse message) {
        return WebSocketMessage.builder()
                .type(WebSocketMessage.MessageType.MESSAGE)
                .channelId(message.getChannelId())
                .messageId(message.getId())
                .userId(message.getUserId())
                .content(message.getContent())
                .createdAt(message.getCreatedAt().toString())
                .timestampId(message.getTimestampId())
                .build();
    }

    private boolean validateMessageType(WebSocketMessage message,
                                         WebSocketMessage.MessageType expectedType,
                                         String handlerName) {
        if (message.getType() != expectedType) {
            log.warn("Received non-{} message in {}: type={}",
                    expectedType, handlerName, message.getType());
            return false;
        }
        return true;
    }

}

