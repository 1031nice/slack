package com.slack.domain.mention;

import com.slack.domain.message.Message;
import com.slack.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "mentions", uniqueConstraints = {
    @UniqueConstraint(name = "uk_mention_message_user", columnNames = {"message_id", "mentioned_user_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Mention {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    private Message message;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mentioned_user_id", nullable = false)
    private User mentionedUser;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "is_read", nullable = false)
    private Boolean isRead;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (isRead == null) {
            isRead = false;
        }
    }

    @Builder
    public Mention(Message message, User mentionedUser, Boolean isRead) {
        this.message = message;
        this.mentionedUser = mentionedUser;
        this.isRead = isRead != null ? isRead : false;
    }

    /**
     * Mark mention as read
     */
    public void markAsRead() {
        this.isRead = true;
    }
}
