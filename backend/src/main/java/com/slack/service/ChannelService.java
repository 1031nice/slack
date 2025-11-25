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
        return toResponse(saved);
    }

    public ChannelResponse getChannelById(Long id) {
        Channel channel = channelRepository.findById(id)
                .orElseThrow(() -> new ChannelNotFoundException("Channel not found with id: " + id));
        return toResponse(channel);
    }

    public List<ChannelResponse> getWorkspaceChannels(Long workspaceId) {
        List<Channel> channels = channelRepository.findByWorkspaceId(workspaceId);
        return channels.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
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

    private ChannelResponse toResponse(Channel channel) {
        return ChannelResponse.builder()
                .id(channel.getId())
                .workspaceId(channel.getWorkspace().getId())
                .name(channel.getName())
                .type(channel.getType())
                .createdBy(channel.getCreatedBy())
                .createdAt(channel.getCreatedAt())
                .updatedAt(channel.getUpdatedAt())
                .build();
    }
}

