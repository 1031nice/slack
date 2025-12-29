package com.slack.unread.service;

import com.slack.channel.domain.Channel;
import com.slack.channel.domain.ChannelType;
import com.slack.channel.repository.ChannelMemberRepository;
import com.slack.channel.repository.ChannelRepository;
import com.slack.message.domain.Message;
import com.slack.message.repository.MessageRepository;
import com.slack.unread.dto.UnreadMessageResponse;
import com.slack.unread.dto.UnreadSortOption;
import com.slack.unread.dto.UnreadsViewResponse;
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
public class UnreadsViewService {

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
     * @return UnreadsViewResponse with sorted unread messages
     */
    public UnreadsViewResponse getUnreads(Long userId, String sort, Integer limit) {
        UnreadSortOption sortOption = UnreadSortOption.fromString(sort);
        int messageLimit = normalizeLimit(limit);

        Set<Long> channelIds = getAccessibleChannelIds(userId);
        if (channelIds.isEmpty()) {
            return buildEmptyResponse();
        }

        List<UnreadMessageData> unreadMessageDataList = collectUnreadMessageData(channelIds, userId, sortOption);
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
     * Get all channel IDs that the user can access.
     * Includes PRIVATE channels where user is a member and PUBLIC channels in user's workspaces.
     */
    private Set<Long> getAccessibleChannelIds(Long userId) {

        // Get channels where user is explicitly a member (includes both PRIVATE and PUBLIC channels)
        List<Long> memberChannelIds = channelMemberRepository.findChannelIdsByUserId(userId);
        Set<Long> channelIds = new HashSet<>(memberChannelIds);

        // Additionally, get all PUBLIC channels from workspaces where user is a member
        // (PUBLIC channels are accessible to all workspace members)
        List<Long> workspaceIds = workspaceMemberRepository.findByUserId(userId).stream()
                .map(wm -> wm.getWorkspace().getId())
                .toList();

        for (Long workspaceId : workspaceIds) {
            List<Long> publicChannelIds = channelRepository.findByWorkspaceId(workspaceId).stream()
                    .filter(channel -> channel.getType() == ChannelType.PUBLIC)
                    .map(Channel::getId)
                    .toList();
            channelIds.addAll(publicChannelIds);
        }

        return channelIds;
    }

    /**
     * Collect unread message data from Redis for all accessible channels.
     */
    private List<UnreadMessageData> collectUnreadMessageData(Set<Long> channelIds, Long userId, UnreadSortOption sortOption) {
        List<UnreadMessageData> unreadMessageDataList = new ArrayList<>();
        Map<Long, String> channelNameCache = new HashMap<>();

        for (Long channelId : channelIds) {
            String channelName = getChannelName(channelId, channelNameCache);
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
     * Get channel name with caching.
     */
    private String getChannelName(Long channelId, Map<Long, String> channelNameCache) {
        return channelNameCache.computeIfAbsent(channelId, id ->
                channelRepository.findById(id)
                        .map(Channel::getName)
                        .orElse("Unknown Channel")
        );
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
    private UnreadsViewResponse applyLimitAndBuildResponse(List<UnreadMessageResponse> unreadMessages, int messageLimit) {
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
     * Build empty response.
     */
    private UnreadsViewResponse buildEmptyResponse() {
        return UnreadsViewResponse.builder()
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

