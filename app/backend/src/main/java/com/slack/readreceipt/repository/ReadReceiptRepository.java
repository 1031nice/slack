package com.slack.readreceipt.repository;

import com.slack.readreceipt.domain.ReadReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReadReceiptRepository extends JpaRepository<ReadReceipt, Long> {
    Optional<ReadReceipt> findByUserIdAndChannelId(Long userId, Long channelId);

    List<ReadReceipt> findByChannelId(Long channelId);

    List<ReadReceipt> findByUserId(Long userId);

    /**
     * Find read receipts not updated since given timestamp
     * Used by reconciliation job to detect stale records
     *
     * @param threshold Timestamp threshold
     * @return List of potentially stale read receipts
     */
    List<ReadReceipt> findByUpdatedAtBefore(LocalDateTime threshold);

    /**
     * Batch query to find read receipts for multiple users in a channel
     * Prevents N+1 query issue when fetching all channel read receipts
     *
     * @param channelId Channel ID
     * @param userIds List of User IDs
     * @return List of ReadReceipts matching the criteria
     */
    List<ReadReceipt> findByChannelIdAndUserIdIn(Long channelId, List<Long> userIds);
}
