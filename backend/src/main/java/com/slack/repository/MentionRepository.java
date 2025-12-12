package com.slack.repository;

import com.slack.domain.mention.Mention;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MentionRepository extends JpaRepository<Mention, Long> {
    /**
     * 특정 사용자에게 언급된 모든 mention을 조회합니다.
     * 
     * @param mentionedUserId 언급된 사용자 ID
     * @return Mention 목록
     */
    List<Mention> findByMentionedUserIdOrderByCreatedAtDesc(Long mentionedUserId);

    /**
     * 특정 사용자의 읽지 않은 mention을 조회합니다.
     * 
     * @param mentionedUserId 언급된 사용자 ID
     * @return 읽지 않은 Mention 목록
     */
    @Query("SELECT m FROM Mention m WHERE m.mentionedUser.id = :mentionedUserId AND m.isRead = false ORDER BY m.createdAt DESC")
    List<Mention> findUnreadMentionsByUserId(@Param("mentionedUserId") Long mentionedUserId);

    /**
     * 특정 메시지와 사용자로 mention을 조회합니다.
     * 
     * @param messageId 메시지 ID
     * @param mentionedUserId 언급된 사용자 ID
     * @return Mention (있을 경우)
     */
    Optional<Mention> findByMessageIdAndMentionedUserId(Long messageId, Long mentionedUserId);

    /**
     * 특정 메시지에 대한 모든 mention을 조회합니다.
     * 
     * @param messageId 메시지 ID
     * @return Mention 목록
     */
    List<Mention> findByMessageId(Long messageId);
}
