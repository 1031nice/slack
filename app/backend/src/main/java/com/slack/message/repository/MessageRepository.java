package com.slack.message.repository;

import com.slack.message.domain.Message;
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

    /**
     * Find messages after a given timestamp for reconnection recovery
     */
    @Query("SELECT m FROM Message m WHERE m.channel.id = :channelId " +
           "AND (m.timestampId > :afterTimestamp OR " +
           "     (m.timestampId IS NULL AND m.createdAt > CAST(:afterTimestamp AS timestamp))) " +
           "ORDER BY COALESCE(m.timestampId, CAST(m.createdAt AS string)) ASC")
    List<Message> findMessagesAfterTimestamp(
        @Param("channelId") Long channelId,
        @Param("afterTimestamp") String afterTimestamp
    );
}

