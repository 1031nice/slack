package com.slack.repository;

import com.slack.domain.channel.Channel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChannelRepository extends JpaRepository<Channel, Long> {
    List<Channel> findByWorkspaceId(Long workspaceId);
}

