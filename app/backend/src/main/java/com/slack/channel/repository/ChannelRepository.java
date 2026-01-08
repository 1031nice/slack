package com.slack.channel.repository;

import com.slack.channel.domain.Channel;
import com.slack.channel.domain.ChannelType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChannelRepository extends JpaRepository<Channel, Long> {
    List<Channel> findByWorkspaceId(Long workspaceId);

    Optional<Channel> findByWorkspaceIdAndName(Long workspaceId, String name);

    /**
     * Batch fetch to prevent N+1 queries
     */
    List<Channel> findByWorkspaceIdInAndType(List<Long> workspaceIds, ChannelType type);
}

