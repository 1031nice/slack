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

    /**
     * 특정 채널에서 시퀀스 번호 이후의 메시지를 조회합니다.
     * 재연결 시 누락된 메시지를 복구하기 위해 사용됩니다.
     * 
     * @param channelId 채널 ID
     * @param afterSequenceNumber 이 시퀀스 번호 이후의 메시지 조회
     * @return 시퀀스 번호 순서대로 정렬된 메시지 목록
     */
    @Query("SELECT m FROM Message m WHERE m.channel.id = :channelId " +
           "AND m.sequenceNumber IS NOT NULL " +
           "AND m.sequenceNumber > :afterSequenceNumber " +
           "ORDER BY m.sequenceNumber ASC")
    List<Message> findMessagesAfterSequence(
        @Param("channelId") Long channelId,
        @Param("afterSequenceNumber") Long afterSequenceNumber
    );
}

