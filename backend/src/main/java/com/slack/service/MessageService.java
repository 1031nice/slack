package com.slack.service;

import com.slack.domain.channel.Channel;
import com.slack.domain.message.Message;
import com.slack.domain.user.User;
import com.slack.dto.message.MessageCreateRequest;
import com.slack.dto.message.MessageResponse;
import com.slack.exception.ChannelNotFoundException;
import com.slack.exception.MessageNotFoundException;
import com.slack.repository.ChannelRepository;
import com.slack.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing message operations.
 * Handles message CRUD operations and delegates to UnreadCountService and MentionService.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MessageService {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 100;

    private final MessageRepository messageRepository;
    private final ChannelRepository channelRepository;
    private final UserService userService;
    private final UnreadCountService unreadCountService;
    private final MentionService mentionService;
    private final PermissionService permissionService;
    private final MessageTimestampGenerator timestampGenerator;

    /**
     * Create a new message in a channel.
     * This method is internal-only, called by WebSocketMessageService.
     *
     * @param channelId channel ID
     * @param request message creation request (with server-generated userId and sequenceNumber)
     * @return created message response
     * @throws IllegalArgumentException if channelId is invalid
     * @throws ChannelNotFoundException if channel not found
     */
    @Transactional
    public MessageResponse createMessage(Long channelId, MessageCreateRequest request) {
        validateChannelId(channelId);

        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new ChannelNotFoundException("Channel not found with id: " + channelId));

        User user = userService.findById(request.getUserId());

        // Generate timestamp-based message ID (unique per channel)
        String timestampId = timestampGenerator.generateTimestampId();

        Message message = Message.builder()
                .channel(channel)
                .user(user)
                .content(request.getContent())
                .parentMessage(null)
                .sequenceNumber(request.getSequenceNumber())
                .timestampId(timestampId)
                .build();

        Message saved = messageRepository.save(message);

        long timestamp = saved.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        unreadCountService.incrementUnreadCount(channelId, saved.getId(), user.getId(), timestamp);
        mentionService.createMentions(saved);

        return toResponse(saved);
    }

    /**
     * Get message by ID.
     *
     * @param id message ID
     * @param userId user ID requesting the message
     * @return message response
     * @throws IllegalArgumentException if id is invalid
     * @throws MessageNotFoundException if message not found
     */
    public MessageResponse getMessageById(Long id, Long userId) {
        validateMessageId(id);

        // Authorization: must have access to message's channel
        permissionService.requireMessageAccess(userId, id);

        Message message = messageRepository.findById(id)
                .orElseThrow(() -> new MessageNotFoundException("Message not found with id: " + id));
        return toResponse(message);
    }

    /**
     * Get messages in a channel with pagination.
     *
     * @param channelId channel ID
     * @param userId user ID requesting the messages
     * @param limit maximum number of messages to return (default 50, max 100)
     * @param before fetch messages before this message ID (for pagination)
     * @return list of message responses
     * @throws IllegalArgumentException if channelId is invalid
     */
    public List<MessageResponse> getChannelMessages(Long channelId, Long userId, Integer limit, Long before) {
        validateChannelId(channelId);

        // Authorization: must have channel access
        permissionService.requireChannelAccess(userId, channelId);

        // Default 50, max 100
        int pageSize = (limit != null && limit > 0)
            ? Math.min(limit, MAX_PAGE_SIZE)
            : DEFAULT_PAGE_SIZE;
        Pageable pageable = PageRequest.of(0, pageSize);

        List<Message> messages = messageRepository.findMessagesByChannelId(channelId, before, pageable);
        return messages.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get messages after a specific sequence number.
     * Used for retrieving missed messages during reconnection.
     *
     * @param channelId channel ID
     * @param afterSequenceNumber fetch messages after this sequence number
     * @return list of message responses ordered by sequence number
     * @throws IllegalArgumentException if channelId or afterSequenceNumber is invalid
     * @deprecated Use {@link #getMessagesAfterTimestamp(Long, String)} instead (Phase 3)
     */
    @Deprecated
    public List<MessageResponse> getMessagesAfterSequence(Long channelId, Long afterSequenceNumber) {
        validateChannelId(channelId);
        if (afterSequenceNumber == null || afterSequenceNumber < 0) {
            throw new IllegalArgumentException("Invalid sequence number: " + afterSequenceNumber);
        }

        List<Message> messages = messageRepository.findMessagesAfterSequence(channelId, afterSequenceNumber);
        return messages.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Phase 3: Get messages after a specific timestamp.
     * Used for retrieving missed messages during reconnection.
     *
     * @param channelId channel ID
     * @param afterTimestamp fetch messages after this timestamp (timestampId or ISO datetime)
     * @return list of message responses ordered by timestamp
     * @throws IllegalArgumentException if channelId or afterTimestamp is invalid
     */
    public List<MessageResponse> getMessagesAfterTimestamp(Long channelId, String afterTimestamp) {
        validateChannelId(channelId);
        if (afterTimestamp == null || afterTimestamp.trim().isEmpty()) {
            throw new IllegalArgumentException("Invalid timestamp: " + afterTimestamp);
        }

        List<Message> messages = messageRepository.findMessagesAfterTimestamp(channelId, afterTimestamp);
        return messages.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private void validateChannelId(Long channelId) {
        if (channelId == null || channelId <= 0) {
            throw new IllegalArgumentException("Invalid channel ID: " + channelId);
        }
    }

    private void validateMessageId(Long messageId) {
        if (messageId == null || messageId <= 0) {
            throw new IllegalArgumentException("Invalid message ID: " + messageId);
        }
    }

    private MessageResponse toResponse(Message message) {
        return MessageResponse.builder()
                .id(message.getId())
                .channelId(message.getChannel().getId())
                .userId(message.getUser().getId())
                .content(message.getContent())
                .parentMessageId(message.getParentMessage() != null ? message.getParentMessage().getId() : null)
                .createdAt(message.getCreatedAt())
                .updatedAt(message.getUpdatedAt())
                .sequenceNumber(message.getSequenceNumber())
                .timestampId(message.getTimestampId())
                .build();
    }
}
