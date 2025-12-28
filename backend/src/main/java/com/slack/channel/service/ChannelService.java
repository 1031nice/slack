package com.slack.channel.service;

import com.slack.channel.domain.Channel;
import com.slack.channel.domain.ChannelType;
import com.slack.channel.dto.ChannelCreateRequest;
import com.slack.channel.dto.ChannelResponse;
import com.slack.channel.repository.ChannelMemberRepository;
import com.slack.channel.repository.ChannelRepository;
import com.slack.common.service.PermissionService;
import com.slack.exception.ChannelNotFoundException;
import com.slack.exception.WorkspaceNotFoundException;
import com.slack.unread.service.UnreadCountService;
import com.slack.workspace.domain.Workspace;
import com.slack.workspace.repository.WorkspaceMemberRepository;
import com.slack.workspace.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChannelService {

    private static final String DEFAULT_CHANNEL_NAME = "general";

    private final ChannelRepository channelRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final UnreadCountService unreadCountService;
    private final PermissionService permissionService;

    @Transactional
    public ChannelResponse createChannel(Long workspaceId, ChannelCreateRequest request, Long userId) {
        // Authorization: must be workspace member to create channel
        permissionService.requireWorkspaceMember(userId, workspaceId);

        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new WorkspaceNotFoundException("Workspace not found with id: " + workspaceId));

        Channel channel = Channel.builder()
                .workspace(workspace)
                .name(request.getName())
                .type(request.getType())
                .createdBy(userId)
                .build();

        Channel saved = channelRepository.save(channel);
        return toResponse(saved, userId);
    }

    public ChannelResponse getChannelById(Long id, Long userId) {
        // Authorization: must have channel access
        permissionService.requireChannelAccess(userId, id);

        Channel channel = channelRepository.findById(id)
                .orElseThrow(() -> new ChannelNotFoundException("Channel not found with id: " + id));
        return toResponse(channel, userId);
    }

    public List<ChannelResponse> getWorkspaceChannels(Long workspaceId, Long userId) {
        // Authorization: must be workspace member to list channels
        permissionService.requireWorkspaceMember(userId, workspaceId);

        return channelRepository.findByWorkspaceId(workspaceId).stream()
                .filter(channel -> canUserAccessChannel(channel, userId))
                .map(channel -> toResponse(channel, userId))
                .collect(Collectors.toList());
    }

    /**
     * Checks if user can access the channel based on channel type and membership.
     */
    private boolean canUserAccessChannel(Channel channel, Long userId) {
        // PUBLIC channels: accessible to workspace members
        if (channel.getType() == ChannelType.PUBLIC) {
            return workspaceMemberRepository.existsByWorkspaceIdAndUserId(
                    channel.getWorkspace().getId(), userId);
        }

        // PRIVATE channels: accessible to channel members only
        return channelMemberRepository.existsByChannelIdAndUserId(channel.getId(), userId);
    }

    /**
     * Finds or creates the default channel for a workspace.
     * Creates a "general" PUBLIC channel if it doesn't exist.
     */
    @Transactional
    public Channel findOrCreateDefaultChannel(Workspace workspace, Long creator) {
        return channelRepository.findByWorkspaceIdAndName(workspace.getId(), DEFAULT_CHANNEL_NAME)
                .orElseGet(() -> {
                    Channel defaultChannel = Channel.builder()
                            .workspace(workspace)
                            .name(DEFAULT_CHANNEL_NAME)
                            .type(ChannelType.PUBLIC)
                            .createdBy(creator)
                            .build();
                    return channelRepository.save(defaultChannel);
                });
    }

    private ChannelResponse toResponse(Channel channel, Long userId) {
        long unreadCount = unreadCountService.getUnreadCount(userId, channel.getId());

        return ChannelResponse.builder()
                .id(channel.getId())
                .workspaceId(channel.getWorkspace().getId())
                .name(channel.getName())
                .type(channel.getType())
                .createdBy(channel.getCreatedBy())
                .createdAt(channel.getCreatedAt())
                .updatedAt(channel.getUpdatedAt())
                .unreadCount(unreadCount)
                .build();
    }
}

