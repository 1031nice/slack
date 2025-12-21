package com.slack.service;

import com.slack.domain.channel.Channel;
import com.slack.domain.channel.ChannelType;
import com.slack.domain.message.Message;
import com.slack.dto.unread.UnreadMessageResponse;
import com.slack.dto.unread.UnreadsViewResponse;
import com.slack.repository.ChannelMemberRepository;
import com.slack.repository.ChannelRepository;
import com.slack.repository.MessageRepository;
import com.slack.repository.WorkspaceMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for aggregating unread messages across all channels for a user.
 * Provides sorted views of unread messages with different sorting options.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UnreadsViewService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final ChannelMemberRepository channelMemberRepository;
    private final UnreadCountService unreadCountService;
    private final MessageRepository messageRepository;
    private final ChannelRepository channelRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;

    /**
     * Get aggregated unread messages across all channels for a user.
     *
     * @param userId User ID
     * @param sort Sort option: "newest", "oldest", or "channel"
     * @param limit Maximum number of messages to return
     * @return UnreadsViewResponse with sorted unread messages
     */
    public UnreadsViewResponse getUnreads(Long userId, String sort, Integer limit) {
        // Validate and set defaults
        String sortOption = (sort != null && !sort.isEmpty()) ? sort.toLowerCase() : "newest";
        int messageLimit = (limit != null && limit > 0) ? Math.min(limit, MAX_LIMIT) : DEFAULT_LIMIT;

        // Get all channel IDs where user can access:
        // 1. PRIVATE channels: user is a ChannelMember
        // 2. PUBLIC channels: user is a WorkspaceMember
        Set<Long> channelIds = new HashSet<>();
        
        // Get PRIVATE channels where user is a member
        List<Long> privateChannelIds = channelMemberRepository.findChannelIdsByUserId(userId);
        channelIds.addAll(privateChannelIds);
        
        // Get PUBLIC channels from all workspaces where user is a member
        List<Long> workspaceIds = workspaceMemberRepository.findByUserId(userId).stream()
                .map(wm -> wm.getWorkspace().getId())
                .collect(Collectors.toList());
        
        for (Long workspaceId : workspaceIds) {
            List<Channel> publicChannels = channelRepository.findByWorkspaceId(workspaceId).stream()
                    .filter(channel -> channel.getType() == ChannelType.PUBLIC)
                    .collect(Collectors.toList());
            for (Channel channel : publicChannels) {
                channelIds.add(channel.getId());
            }
        }
        
        if (channelIds.isEmpty()) {
            return UnreadsViewResponse.builder()
                    .unreadMessages(Collections.emptyList())
                    .totalCount(0)
                    .build();
        }

        // Collect all unread message IDs with channel info
        List<UnreadMessageData> unreadMessageDataList = new ArrayList<>();
        Map<Long, String> channelNameCache = new HashMap<>();

        for (Long channelId : channelIds) {
            // Get channel name (cache for reuse)
            String channelName = channelNameCache.computeIfAbsent(channelId, id -> {
                return channelRepository.findById(id)
                        .map(Channel::getName)
                        .orElse("Unknown Channel");
            });

            // Get unread message IDs from Redis
            // For "newest" or "channel" sort, we want descending (newest first)
            // For "oldest" sort, we want ascending (oldest first)
            boolean descending = !"oldest".equals(sortOption);
            Set<String> unreadMessageIds = unreadCountService.getUnreadMessageIdsSorted(userId, channelId, descending);

            for (String messageIdStr : unreadMessageIds) {
                try {
                    Long messageId = Long.parseLong(messageIdStr);
                    unreadMessageDataList.add(new UnreadMessageData(messageId, channelId, channelName));
                } catch (NumberFormatException e) {
                    // Skip invalid message IDs
                }
            }
        }

        if (unreadMessageDataList.isEmpty()) {
            return UnreadsViewResponse.builder()
                    .unreadMessages(Collections.emptyList())
                    .totalCount(0)
                    .build();
        }

        // Fetch message details from PostgreSQL (batch query)
        Set<Long> messageIds = unreadMessageDataList.stream()
                .map(UnreadMessageData::getMessageId)
                .collect(Collectors.toSet());

        List<Message> messages = messageRepository.findAllById(messageIds);
        Map<Long, Message> messageMap = messages.stream()
                .collect(Collectors.toMap(Message::getId, msg -> msg));

        // Build response objects
        List<UnreadMessageResponse> unreadMessages = new ArrayList<>();
        for (UnreadMessageData data : unreadMessageDataList) {
            Message message = messageMap.get(data.getMessageId());
            if (message != null) {
                unreadMessages.add(UnreadMessageResponse.builder()
                        .messageId(message.getId())
                        .channelId(data.getChannelId())
                        .channelName(data.getChannelName())
                        .userId(message.getUser().getId())
                        .content(message.getContent())
                        .createdAt(message.getCreatedAt())
                        .sequenceNumber(message.getSequenceNumber())
                        .timestampId(message.getTimestampId())
                        .build());
            }
        }

        // Sort according to sort option
        switch (sortOption) {
            case "newest":
                unreadMessages.sort(Comparator.comparing(UnreadMessageResponse::getCreatedAt).reversed());
                break;
            case "oldest":
                unreadMessages.sort(Comparator.comparing(UnreadMessageResponse::getCreatedAt));
                break;
            case "channel":
                // Group by channel, then sort by createdAt DESC within each channel
                unreadMessages.sort(Comparator
                        .comparing(UnreadMessageResponse::getChannelName)
                        .thenComparing(UnreadMessageResponse::getCreatedAt, Comparator.reverseOrder()));
                break;
            default:
                // Default to newest
                unreadMessages.sort(Comparator.comparing(UnreadMessageResponse::getCreatedAt).reversed());
        }

        // Apply limit
        int totalCount = unreadMessages.size();
        if (unreadMessages.size() > messageLimit) {
            unreadMessages = unreadMessages.subList(0, messageLimit);
        }

        return UnreadsViewResponse.builder()
                .unreadMessages(unreadMessages)
                .totalCount(totalCount)
                .build();
    }

    /**
     * Helper class to hold unread message data before fetching from DB
     */
    private static class UnreadMessageData {
        private final Long messageId;
        private final Long channelId;
        private final String channelName;

        public UnreadMessageData(Long messageId, Long channelId, String channelName) {
            this.messageId = messageId;
            this.channelId = channelId;
            this.channelName = channelName;
        }

        public Long getMessageId() {
            return messageId;
        }

        public Long getChannelId() {
            return channelId;
        }

        public String getChannelName() {
            return channelName;
        }
    }
}

