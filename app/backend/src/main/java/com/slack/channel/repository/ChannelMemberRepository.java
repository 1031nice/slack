package com.slack.channel.repository;

import com.slack.channel.domain.ChannelMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChannelMemberRepository extends JpaRepository<ChannelMember, Long> {
    boolean existsByChannelIdAndUserId(Long channelId, Long userId);

    Optional<ChannelMember> findByChannelIdAndUserId(Long channelId, Long userId);

    @Query("SELECT cm.user.id FROM ChannelMember cm WHERE cm.channel.id = :channelId")
    List<Long> findUserIdsByChannelId(@Param("channelId") Long channelId);

    @Query("SELECT cm.channel.id FROM ChannelMember cm WHERE cm.user.id = :userId")
    List<Long> findChannelIdsByUserId(@Param("userId") Long userId);
}

