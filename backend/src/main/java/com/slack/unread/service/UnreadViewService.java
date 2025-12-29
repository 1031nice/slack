package com.slack.unread.service;

import com.slack.channel.domain.Channel;
import com.slack.channel.domain.ChannelType;
import com.slack.channel.repository.ChannelMemberRepository;
import com.slack.channel.repository.ChannelRepository;
import com.slack.message.domain.Message;
import com.slack.message.repository.MessageRepository;
import com.slack.unread.dto.UnreadMessageResponse;
import com.slack.unread.dto.UnreadSortOption;
import com.slack.unread.dto.UnreadViewResponse;
import com.slack.unread.mapper.UnreadMapper;
import com.slack.workspace.repository.WorkspaceMemberRepository;
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
public class UnreadViewService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final ChannelMemberRepository channelMemberRepository;
    private final UnreadCountService unreadCountService;
    private final MessageRepository messageRepository;
    private final ChannelRepository channelRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UnreadMapper unreadMapper;

    /**
     * Get aggregated unread messages across all channels for a user.
     *
     * @param userId User ID
     * @param sort   Sort option: "newest", "oldest", or "channel"
     * @param limit  Maximum number of messages to return
     * @return UnreadViewResponse with sorted unread messages
     */
    public UnreadViewResponse getUnreads(Long userId, String sort, Integer limit) {
        UnreadSortOption sortOption = UnreadSortOption.fromString(sort);
        int messageLimit = normalizeLimit(limit);

        Map<Long, String> accessibleChannels = getAccessibleChannels(userId);
        if (accessibleChannels.isEmpty()) {
            return buildEmptyResponse();
        }

        List<UnreadMessageData> unreadMessageDataList = collectUnreadMessageData(
                accessibleChannels.keySet(), userId, sortOption, accessibleChannels);
        if (unreadMessageDataList.isEmpty()) {
            return buildEmptyResponse();
        }

        List<UnreadMessageResponse> unreadMessages = buildUnreadMessages(unreadMessageDataList);
        sortUnreadMessages(unreadMessages, sortOption);

        return applyLimitAndBuildResponse(unreadMessages, messageLimit);
    }

    /**
     * Normalize limit with default and max validation.
     */
    private int normalizeLimit(Integer limit) {
        return (limit != null && limit > 0) ? Math.min(limit, MAX_LIMIT) : DEFAULT_LIMIT;
    }

    /**
     * Get all accessible channels with their names for the user.
     * Returns a map of channelId -> channelName.
     * Includes PRIVATE channels where user is a member and PUBLIC channels in user's workspaces.
     */
    private Map<Long, String> getAccessibleChannels(Long userId) {
        Map<Long, String> channels = new HashMap<>();

        // Get channels where user is explicitly a member (includes both PRIVATE and PUBLIC channels)
        List<Long> memberChannelIds = channelMemberRepository.findChannelIdsByUserId(userId);
        if (!memberChannelIds.isEmpty()) {
            List<Channel> memberChannels = channelRepository.findAllById(memberChannelIds);
            for (Channel channel : memberChannels) {
                channels.put(channel.getId(), channel.getName());
            }
        }

        // Additionally, get all PUBLIC channels from workspaces where user is a member
        // (PUBLIC channels are accessible to all workspace members)
        List<Long> workspaceIds = workspaceMemberRepository.findByUserId(userId).stream()
                .map(wm -> wm.getWorkspace().getId())
                .toList();

        if (!workspaceIds.isEmpty()) {
            List<Channel> publicChannels = channelRepository.findByWorkspaceIdInAndType(
                    workspaceIds, ChannelType.PUBLIC);
            for (Channel channel : publicChannels) {
                channels.put(channel.getId(), channel.getName());
            }
        }

        return channels;
    }

    /**
     * Collect unread message data from Redis for all accessible channels.
     */
    private List<UnreadMessageData> collectUnreadMessageData(
            Set<Long> channelIds, Long userId, UnreadSortOption sortOption, Map<Long, String> channelNames) {
        List<UnreadMessageData> unreadMessageDataList = new ArrayList<>();

        for (Long channelId : channelIds) {
            String channelName = channelNames.getOrDefault(channelId, "Unknown Channel");
            boolean descending = sortOption != UnreadSortOption.OLDEST;
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

        return unreadMessageDataList;
    }

    /**
     * Build unread message response objects by fetching message details from database.
     */
    private List<UnreadMessageResponse> buildUnreadMessages(List<UnreadMessageData> unreadMessageDataList) {
        Set<Long> messageIds = unreadMessageDataList.stream()
                .map(UnreadMessageData::getMessageId)
                .collect(Collectors.toSet());

        List<Message> messages = messageRepository.findAllById(messageIds);
        Map<Long, Message> messageMap = messages.stream()
                .collect(Collectors.toMap(Message::getId, msg -> msg));

        List<UnreadMessageResponse> unreadMessages = new ArrayList<>();
        for (UnreadMessageData data : unreadMessageDataList) {
            Message message = messageMap.get(data.getMessageId());
            if (message != null) {
                unreadMessages.add(unreadMapper.toUnreadMessageResponse(
                        message,
                        data.getChannelId(),
                        data.getChannelName()
                ));
            }
        }

        return unreadMessages;
    }

    /**
     * Sort unread messages according to the sort option.
     */
    private void sortUnreadMessages(List<UnreadMessageResponse> unreadMessages, UnreadSortOption sortOption) {
        switch (sortOption) {
            case NEWEST:
                unreadMessages.sort(Comparator.comparing(UnreadMessageResponse::getCreatedAt).reversed());
                break;
            case OLDEST:
                unreadMessages.sort(Comparator.comparing(UnreadMessageResponse::getCreatedAt));
                break;
            case CHANNEL:
                unreadMessages.sort(Comparator
                        .comparing(UnreadMessageResponse::getChannelName)
                        .thenComparing(UnreadMessageResponse::getCreatedAt, Comparator.reverseOrder()));
                break;
        }
    }

    /**
     * Apply limit and build final response.
     */
    private UnreadViewResponse applyLimitAndBuildResponse(List<UnreadMessageResponse> unreadMessages, int messageLimit) {
        int totalCount = unreadMessages.size();
        if (unreadMessages.size() > messageLimit) {
            unreadMessages = unreadMessages.subList(0, messageLimit);
        }

        return UnreadViewResponse.builder()
                .unreadMessages(unreadMessages)
                .totalCount(totalCount)
                .build();
    }

    /**
     * Build empty response.
     */
    private UnreadViewResponse buildEmptyResponse() {
        return UnreadViewResponse.builder()
                .unreadMessages(Collections.emptyList())
                .totalCount(0)
                .build();
    }

    /**
     * Helper class to hold unread message data before fetching from DB
     */
    @lombok.Getter
    @lombok.AllArgsConstructor
    private static class UnreadMessageData {
        private final Long messageId;
        private final Long channelId;
        private final String channelName;
    }
}

