package com.slack.service;

import com.slack.domain.channel.Channel;
import com.slack.domain.workspace.Workspace;
import com.slack.dto.channel.ChannelCreateRequest;
import com.slack.dto.channel.ChannelResponse;
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
                .orElseThrow(() -> new RuntimeException("Workspace not found with id: " + workspaceId));
        
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
                .orElseThrow(() -> new RuntimeException("Channel not found with id: " + id));
        return toResponse(channel);
    }

    public List<ChannelResponse> getWorkspaceChannels(Long workspaceId) {
        List<Channel> channels = channelRepository.findByWorkspaceId(workspaceId);
        return channels.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
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

