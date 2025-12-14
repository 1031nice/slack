package com.slack.service;

import com.slack.domain.mention.Mention;
import com.slack.domain.message.Message;
import com.slack.domain.user.User;
import com.slack.dto.websocket.WebSocketMessage;
import com.slack.repository.MentionRepository;
import com.slack.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for detecting and creating @mention notifications
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class MentionService {

    // Pattern to match @username mentions
    // Matches @username where username can contain letters, numbers, underscores, hyphens
    // Example: @john, @john_doe, @user123
    private static final Pattern MENTION_PATTERN = Pattern.compile("@([a-zA-Z0-9_-]+)");

    private final MentionRepository mentionRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Parse @username mentions from message content
     *
     * @param content Message content
     * @return Set of unique usernames (without @ symbol)
     */
    public Set<String> parseMentions(String content) {
        Set<String> mentions = new HashSet<>();
        if (content == null || content.isEmpty()) {
            return mentions;
        }

        Matcher matcher = MENTION_PATTERN.matcher(content);
        while (matcher.find()) {
            String username = matcher.group(1);
            mentions.add(username);
        }

        return mentions;
    }

    /**
     * Create mention notifications for a message
     * Finds users by name (case-insensitive) and creates Mention entities
     *
     * @param message Message that contains mentions
     * @return List of created Mention entities
     */
    @Transactional
    public List<Mention> createMentions(Message message) {
        List<Mention> createdMentions = new ArrayList<>();

        if (message == null || message.getContent() == null || message.getId() == null) {
            return createdMentions;
        }

        Set<String> mentionedUsernames = parseMentions(message.getContent());
        if (mentionedUsernames.isEmpty()) {
            return createdMentions;
        }

        // Batch query all users by mentioned usernames (1 query instead of N)
        List<User> allUsers = userRepository.findByNameIgnoreCaseIn(mentionedUsernames);

        // Filter out self-mentions
        List<User> validUsers = allUsers.stream()
                .filter(user -> !user.getId().equals(message.getUser().getId()))
                .toList();

        if (validUsers.isEmpty()) {
            return createdMentions;
        }

        List<Long> userIds = validUsers.stream()
                .map(User::getId)
                .toList();

        // Batch query existing mentions (1 query instead of M)
        List<Mention> existingMentions = mentionRepository
                .findByMessageIdAndMentionedUserIdIn(message.getId(), userIds);

        Set<Long> existingUserIds = existingMentions.stream()
                .map(mention -> mention.getMentionedUser().getId())
                .collect(java.util.stream.Collectors.toSet());

        // Filter out duplicates and build new mentions
        List<Mention> newMentions = validUsers.stream()
                .filter(user -> !existingUserIds.contains(user.getId()))
                .map(user -> Mention.builder()
                        .message(message)
                        .mentionedUser(user)
                        .isRead(false)
                        .build())
                .toList();

        // Batch save all mentions (1 query instead of K)
        if (!newMentions.isEmpty()) {
            List<Mention> savedMentions = mentionRepository.saveAll(newMentions);
            createdMentions.addAll(savedMentions);

            // Send notifications for each saved mention
            savedMentions.forEach(mention -> {
                log.debug("Created mention: messageId={}, mentionedUserId={}",
                        message.getId(), mention.getMentionedUser().getId());
                sendMentionNotification(mention);
            });
        }

        return createdMentions;
    }

    /**
     * Get all mentions for a user
     *
     * @param userId User ID
     * @return List of mentions
     */
    public List<Mention> getMentionsByUserId(Long userId) {
        return mentionRepository.findByMentionedUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * Get unread mentions for a user
     *
     * @param userId User ID
     * @return List of unread mentions
     */
    public List<Mention> getUnreadMentionsByUserId(Long userId) {
        return mentionRepository.findUnreadMentionsByUserId(userId);
    }

    /**
     * Mark mention as read
     *
     * @param mentionId Mention ID
     */
    @Transactional
    public void markMentionAsRead(Long mentionId) {
        Mention mention = mentionRepository.findById(mentionId)
                .orElseThrow(() -> new RuntimeException("Mention not found with id: " + mentionId));
        mention.markAsRead();
        mentionRepository.save(mention);
    }

    /**
     * Send WebSocket notification to mentioned user
     *
     * @param mention Mention entity
     */
    private void sendMentionNotification(Mention mention) {
        try {
            WebSocketMessage notification = WebSocketMessage.builder()
                    .type(WebSocketMessage.MessageType.MENTION)
                    .channelId(mention.getMessage().getChannel().getId())
                    .messageId(mention.getMessage().getId())
                    .userId(mention.getMessage().getUser().getId())
                    .content(mention.getMessage().getContent())
                    .createdAt(mention.getCreatedAt().toString())
                    .build();

            String userDestination = "/queue/mentions." + mention.getMentionedUser().getId();
            messagingTemplate.convertAndSend(userDestination, notification);

            log.debug("Sent mention notification to user {}: messageId={}",
                    mention.getMentionedUser().getId(), mention.getMessage().getId());
        } catch (Exception e) {
            log.error("Failed to send mention notification", e);
        }
    }
}
