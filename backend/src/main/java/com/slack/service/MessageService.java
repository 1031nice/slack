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

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MessageService {

    private final MessageRepository messageRepository;
    private final ChannelRepository channelRepository;
    private final UserService userService;
    private final UnreadCountService unreadCountService;
    private final MentionService mentionService;

    @Transactional
    public MessageResponse createMessage(Long channelId, MessageCreateRequest request) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new ChannelNotFoundException("Channel not found with id: " + channelId));
        
        User user = userService.findById(request.getUserId());
        
        Message message = Message.builder()
                .channel(channel)
                .user(user)
                .content(request.getContent())
                .parentMessage(null)
                .sequenceNumber(request.getSequenceNumber())
                .build();
        
        Message saved = messageRepository.save(message);
        
        long timestamp = saved.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        unreadCountService.incrementUnreadCount(channelId, saved.getId(), user.getId(), timestamp);
        mentionService.createMentions(saved);
        
        return toResponse(saved);
    }

    public MessageResponse getMessageById(Long id) {
        Message message = messageRepository.findById(id)
                .orElseThrow(() -> new MessageNotFoundException("Message not found with id: " + id));
        return toResponse(message);
    }

    public List<MessageResponse> getChannelMessages(Long channelId, Integer limit, Long before) {
        int pageSize = (limit != null && limit > 0) ? Math.min(limit, 100) : 50; // 기본 50, 최대 100
        Pageable pageable = PageRequest.of(0, pageSize);
        
        List<Message> messages = messageRepository.findMessagesByChannelId(channelId, before, pageable);
        return messages.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<MessageResponse> getMessagesAfterSequence(Long channelId, Long afterSequenceNumber) {
        List<Message> messages = messageRepository.findMessagesAfterSequence(channelId, afterSequenceNumber);
        return messages.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
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
                .build();
    }
}

