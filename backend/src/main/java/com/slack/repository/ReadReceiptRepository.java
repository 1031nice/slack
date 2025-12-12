package com.slack.repository;

import com.slack.domain.readreceipt.ReadReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReadReceiptRepository extends JpaRepository<ReadReceipt, Long> {
    /**
     * 특정 사용자와 채널의 read receipt를 조회합니다.
     * 
     * @param userId User ID
     * @param channelId Channel ID
     * @return ReadReceipt (있을 경우)
     */
    Optional<ReadReceipt> findByUserIdAndChannelId(Long userId, Long channelId);

    /**
     * 특정 채널의 모든 read receipt를 조회합니다.
     * 
     * @param channelId Channel ID
     * @return ReadReceipt 목록
     */
    List<ReadReceipt> findByChannelId(Long channelId);

    /**
     * 특정 사용자의 모든 read receipt를 조회합니다.
     * 
     * @param userId User ID
     * @return ReadReceipt 목록
     */
    List<ReadReceipt> findByUserId(Long userId);

    /**
     * 특정 채널에서 특정 시퀀스 번호 이상을 읽은 사용자들을 조회합니다.
     * 
     * @param channelId Channel ID
     * @param sequenceNumber Sequence number
     * @return ReadReceipt 목록
     */
    @Query("SELECT rr FROM ReadReceipt rr WHERE rr.channel.id = :channelId AND rr.lastReadSequence >= :sequenceNumber")
    List<ReadReceipt> findUsersWhoReadSequence(@Param("channelId") Long channelId, @Param("sequenceNumber") Long sequenceNumber);
}
