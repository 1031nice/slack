package com.slack.service;

import com.slack.domain.user.User;
import com.slack.dto.message.MessageCreateRequest;
import com.slack.dto.message.MessageResponse;
import com.slack.dto.websocket.WebSocketMessage;
import com.slack.service.auth.AuthenticationExtractor;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
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
    private final ReadReceiptService readReceiptService;
    private final AuthenticationExtractor authenticationExtractor;

    /**
     * Processes incoming WebSocket message and broadcasts to channel.
     */
    public WebSocketMessage handleIncomingMessage(WebSocketMessage message, Authentication authentication) {
        log.info("Received message: channelId={}, content={}", message.getChannelId(), message.getContent());

        String authUserId = extractAndValidateAuthUserId(authentication);
        User user = userService.findByAuthUserId(authUserId);
        Long sequenceNumber = sequenceService.getNextSequenceNumber(message.getChannelId());

        MessageCreateRequest createRequest = MessageCreateRequest.builder()
                .userId(user.getId())
                .content(message.getContent())
                .sequenceNumber(sequenceNumber)
                .build();

        MessageResponse savedMessage = messageService.createMessage(message.getChannelId(), createRequest);
        WebSocketMessage response = toWebSocketMessage(savedMessage);
        broadcastToChannel(response);

        log.info("Broadcasted message to channel {}: messageId={}", message.getChannelId(), savedMessage.getId());

        return response;
    }

    /**
     * Sends error message to user.
     */
    public void sendErrorMessage(Authentication authentication, String errorMessage) {
        WebSocketMessage error = WebSocketMessage.builder()
                .type(WebSocketMessage.MessageType.ERROR)
                .content("Failed to send message: " + errorMessage)
                .build();

        String userDestination = getUserDestination(authentication, "errors");
        messagingTemplate.convertAndSend(userDestination, error);
    }

    /**
     * Broadcasts message to all channel subscribers via Redis Pub/Sub.
     */
    public void broadcastToChannel(WebSocketMessage message) {
        redisMessagePublisher.publish(message);
    }

    /**
     * Handles ACK message from client confirming message receipt.
     */
    public void handleAck(WebSocketMessage message, Authentication authentication) {
        if (!validateMessageType(message, WebSocketMessage.MessageType.ACK, "handleAck")) {
            return;
        }

        log.debug("Received ACK: ackId={}, sequenceNumber={}",
                message.getAckId(), message.getSequenceNumber());

        // TODO: Implement ACK-based retransmission logic in v0.3
    }

    /**
     * Resends messages missed since last sequence number on reconnection.
     */
    public void resendMissedMessages(Long channelId, Long lastSequenceNumber, Authentication authentication) {
        log.info("Resending missed messages for channel {} after sequence {}", channelId, lastSequenceNumber);
        
        List<MessageResponse> missedMessages = messageService.getMessagesAfterSequence(channelId, lastSequenceNumber);
        
        if (missedMessages.isEmpty()) {
            log.debug("No missed messages found for channel {} after sequence {}", channelId, lastSequenceNumber);
            return;
        }
        
        log.info("Found {} missed messages for channel {}", missedMessages.size(), channelId);

        String userDestination = getUserDestination(authentication, "resend");

        for (MessageResponse msg : missedMessages) {
            WebSocketMessage webSocketMessage = toWebSocketMessage(msg);
            messagingTemplate.convertAndSend(userDestination, webSocketMessage);
        }
    }

    /**
     * Handles READ message to update user's read receipt for channel.
     */
    public void handleRead(WebSocketMessage message, Authentication authentication) {
        if (!validateMessageType(message, WebSocketMessage.MessageType.READ, "handleRead")) {
            return;
        }

        String authUserId = extractAndValidateAuthUserId(authentication);
        User user = userService.findByAuthUserId(authUserId);

        log.debug("Processing read receipt: userId={}, channelId={}, sequenceNumber={}", 
                user.getId(), message.getChannelId(), message.getSequenceNumber());

        readReceiptService.updateReadReceipt(
                user.getId(),
                message.getChannelId(),
                message.getSequenceNumber()
        );
    }

    private String extractAndValidateAuthUserId(Authentication authentication) {
        String authUserId = authenticationExtractor.extractAuthUserId(authentication);
        if (authUserId == null) {
            throw new IllegalArgumentException("Authentication required");
        }
        return authUserId;
    }

    private WebSocketMessage toWebSocketMessage(MessageResponse message) {
        return WebSocketMessage.builder()
                .type(WebSocketMessage.MessageType.MESSAGE)
                .channelId(message.getChannelId())
                .messageId(message.getId())
                .userId(message.getUserId())
                .content(message.getContent())
                .createdAt(message.getCreatedAt().toString())
                .sequenceNumber(message.getSequenceNumber())
                .timestampId(message.getTimestampId())
                .build();
    }

    private String getUserDestination(Authentication authentication, String queueType) {
        return authentication != null
                ? "/queue/" + queueType + "." + authentication.getName()
                : "/queue/" + queueType;
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

