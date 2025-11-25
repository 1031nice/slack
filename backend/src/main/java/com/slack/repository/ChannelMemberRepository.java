package com.slack.repository;

import com.slack.domain.channel.ChannelMember;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelMemberRepository extends JpaRepository<ChannelMember, Long> {
    /**
     * 특정 channel에 특정 user가 멤버로 존재하는지 확인합니다.
     */
    boolean existsByChannelIdAndUserId(Long channelId, Long userId);
}

