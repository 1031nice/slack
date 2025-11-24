package com.slack.service;

import com.slack.domain.channel.Channel;
import com.slack.domain.message.Message;
import com.slack.domain.user.User;
import com.slack.dto.message.MessageCreateRequest;
import com.slack.dto.message.MessageResponse;
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

    @Transactional
    public MessageResponse createMessage(Long channelId, MessageCreateRequest request) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new RuntimeException("Channel not found with id: " + channelId));
        
        User user = userService.findById(request.getUserId());
        
        Message message = Message.builder()
                .channel(channel)
                .user(user)
                .content(request.getContent())
                .parentMessage(null) // v0.1에서는 스레드 미지원
                .build();
        
        Message saved = messageRepository.save(message);
        return toResponse(saved);
    }

    public MessageResponse getMessageById(Long id) {
        Message message = messageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Message not found with id: " + id));
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

    private MessageResponse toResponse(Message message) {
        return MessageResponse.builder()
                .id(message.getId())
                .channelId(message.getChannel().getId())
                .userId(message.getUser().getId())
                .content(message.getContent())
                .parentMessageId(message.getParentMessage() != null ? message.getParentMessage().getId() : null)
                .createdAt(message.getCreatedAt())
                .updatedAt(message.getUpdatedAt())
                .build();
    }
}

