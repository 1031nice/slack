package com.slack.repository;

import com.slack.domain.channel.Channel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChannelRepository extends JpaRepository<Channel, Long> {
    List<Channel> findByWorkspaceId(Long workspaceId);
    
    /**
     * 특정 workspace에서 이름으로 channel을 찾습니다.
     * 기본 channel을 찾을 때 사용합니다.
     */
    Optional<Channel> findByWorkspaceIdAndName(Long workspaceId, String name);
}

