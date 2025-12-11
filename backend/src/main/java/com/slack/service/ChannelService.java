package com.slack.service;

import com.slack.domain.channel.Channel;
import com.slack.domain.channel.ChannelType;
import com.slack.domain.workspace.Workspace;
import com.slack.dto.channel.ChannelCreateRequest;
import com.slack.dto.channel.ChannelResponse;
import com.slack.exception.ChannelNotFoundException;
import com.slack.exception.WorkspaceNotFoundException;
import com.slack.repository.ChannelRepository;
import com.slack.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChannelService {

    private final ChannelRepository channelRepository;
    private final WorkspaceRepository workspaceRepository;
    private final PermissionService permissionService;
    private final UnreadCountService unreadCountService;

    @Transactional
    public ChannelResponse createChannel(Long workspaceId, ChannelCreateRequest request) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new WorkspaceNotFoundException("Workspace not found with id: " + workspaceId));
        
        Channel channel = Channel.builder()
                .workspace(workspace)
                .name(request.getName())
                .type(request.getType())
                .createdBy(request.getCreatedBy())
                .build();
        
        Channel saved = channelRepository.save(channel);
        // New channel has no unread messages, so unreadCount is 0
        return toResponse(saved, null);
    }

    public ChannelResponse getChannelById(Long id) {
        Channel channel = channelRepository.findById(id)
                .orElseThrow(() -> new ChannelNotFoundException("Channel not found with id: " + id));
        return toResponse(channel, null);
    }

    /**
     * Get channel by ID with unread count for a specific user
     * 
     * @param id Channel ID
     * @param userId User ID (for unread count calculation)
     * @return Channel response with unread count
     */
    public ChannelResponse getChannelById(Long id, Long userId) {
        Channel channel = channelRepository.findById(id)
                .orElseThrow(() -> new ChannelNotFoundException("Channel not found with id: " + id));
        return toResponse(channel, userId);
    }

    /**
     * 사용자가 접근 가능한 워크스페이스의 채널 목록을 반환합니다.
     * - PUBLIC 채널: 워크스페이스 멤버면 모두 표시
     * - PRIVATE 채널: 채널 멤버만 표시
     * 
     * @param workspaceId 워크스페이스 ID
     * @param userId 사용자 ID
     * @return 사용자가 접근 가능한 채널 목록
     */
    public List<ChannelResponse> getWorkspaceChannels(Long workspaceId, Long userId) {
        return channelRepository.findByWorkspaceId(workspaceId).stream()
                .filter(channel -> canUserAccessChannel(channel, userId))
                .map(channel -> toResponse(channel, userId))
                .collect(Collectors.toList());
    }

    /**
     * 사용자가 특정 채널에 접근할 수 있는지 확인합니다.
     * 
     * @param channel 채널
     * @param userId 사용자 ID
     * @return 접근 가능한 경우 true
     */
    private boolean canUserAccessChannel(Channel channel, Long userId) {
        // PUBLIC 채널은 워크스페이스 멤버면 접근 가능
        if (channel.getType() == ChannelType.PUBLIC) {
            return permissionService.isWorkspaceMember(userId, channel.getWorkspace().getId());
        }
        
        // PRIVATE 채널은 채널 멤버만 접근 가능
        return permissionService.isChannelMember(userId, channel.getId());
    }

    /**
     * 기본 channel을 찾거나 생성합니다.
     * v0.1에서는 각 workspace에 "general"이라는 기본 PUBLIC channel을 생성합니다.
     * 
     * @param workspace 기본 channel이 속할 workspace
     * @param creator 기본 channel이 없을 경우 생성할 creator의 user ID
     * @return 기본 channel
     */
    @Transactional
    public Channel findOrCreateDefaultChannel(Workspace workspace, Long creator) {
        final String DEFAULT_CHANNEL_NAME = "general";
        
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
        ChannelResponse.ChannelResponseBuilder builder = ChannelResponse.builder()
                .id(channel.getId())
                .workspaceId(channel.getWorkspace().getId())
                .name(channel.getName())
                .type(channel.getType())
                .createdBy(channel.getCreatedBy())
                .createdAt(channel.getCreatedAt())
                .updatedAt(channel.getUpdatedAt());
        
        // Add unread count if userId is provided
        if (userId != null) {
            long unreadCount = unreadCountService.getUnreadCount(userId, channel.getId());
            builder.unreadCount(unreadCount);
        } else {
            builder.unreadCount(0L);
        }
        
        return builder.build();
    }
}

