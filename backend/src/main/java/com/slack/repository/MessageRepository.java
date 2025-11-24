package com.slack.repository;

import com.slack.domain.message.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    @Query("SELECT m FROM Message m WHERE m.channel.id = :channelId " +
           "AND (:beforeId IS NULL OR m.id < :beforeId) " +
           "ORDER BY m.createdAt DESC")
    List<Message> findMessagesByChannelId(
        @Param("channelId") Long channelId,
        @Param("beforeId") Long beforeId,
        org.springframework.data.domain.Pageable pageable
    );
}

