package com.slack.repository;

import com.slack.domain.channel.ChannelMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChannelMemberRepository extends JpaRepository<ChannelMember, Long> {
    /**
     * 특정 channel에 특정 user가 멤버로 존재하는지 확인합니다.
     */
    boolean existsByChannelIdAndUserId(Long channelId, Long userId);

    /**
     * 특정 channel과 user로 ChannelMember를 조회합니다.
     */
    Optional<ChannelMember> findByChannelIdAndUserId(Long channelId, Long userId);

    /**
     * 특정 channel의 모든 멤버 ID를 조회합니다.
     * 
     * @param channelId Channel ID
     * @return List of user IDs who are members of the channel
     */
    @Query("SELECT cm.user.id FROM ChannelMember cm WHERE cm.channel.id = :channelId")
    List<Long> findUserIdsByChannelId(@Param("channelId") Long channelId);
}

