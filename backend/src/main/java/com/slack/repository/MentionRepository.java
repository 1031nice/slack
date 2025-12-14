package com.slack.repository;

import com.slack.domain.mention.Mention;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MentionRepository extends JpaRepository<Mention, Long> {
    /**
     * Find all mentions for a specific user
     *
     * @param mentionedUserId User ID who was mentioned
     * @return List of mentions ordered by creation time (desc)
     */
    List<Mention> findByMentionedUserIdOrderByCreatedAtDesc(Long mentionedUserId);

    /**
     * Find unread mentions for a specific user
     *
     * @param mentionedUserId User ID who was mentioned
     * @return List of unread mentions ordered by creation time (desc)
     */
    @Query("SELECT m FROM Mention m WHERE m.mentionedUser.id = :mentionedUserId AND m.isRead = false ORDER BY m.createdAt DESC")
    List<Mention> findUnreadMentionsByUserId(@Param("mentionedUserId") Long mentionedUserId);

    /**
     * Find mention by message and mentioned user
     *
     * @param messageId Message ID
     * @param mentionedUserId User ID who was mentioned
     * @return Mention if exists
     */
    Optional<Mention> findByMessageIdAndMentionedUserId(Long messageId, Long mentionedUserId);

    /**
     * Find mentions by message and multiple mentioned users (batch query)
     * Prevents N+1 query problem when checking duplicate mentions
     *
     * @param messageId Message ID
     * @param mentionedUserIds Collection of user IDs who were mentioned
     * @return List of existing mentions
     */
    List<Mention> findByMessageIdAndMentionedUserIdIn(Long messageId, java.util.Collection<Long> mentionedUserIds);

    /**
     * Find all mentions for a specific message
     *
     * @param messageId Message ID
     * @return List of mentions
     */
    List<Mention> findByMessageId(Long messageId);
}
