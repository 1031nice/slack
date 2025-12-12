package com.slack.domain.readreceipt;

import com.slack.domain.channel.Channel;
import com.slack.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "read_receipts", uniqueConstraints = {
    @UniqueConstraint(name = "uk_read_receipt_user_channel", columnNames = {"user_id", "channel_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReadReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id", nullable = false)
    private Channel channel;

    /**
     * 마지막으로 읽은 메시지의 시퀀스 번호
     * 이 시퀀스 번호 이하의 모든 메시지를 읽은 것으로 간주
     */
    @Column(name = "last_read_sequence", nullable = false)
    private Long lastReadSequence;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    @Builder
    public ReadReceipt(User user, Channel channel, Long lastReadSequence) {
        this.user = user;
        this.channel = channel;
        this.lastReadSequence = lastReadSequence;
    }

    /**
     * Update last read sequence number
     * 
     * @param sequenceNumber New sequence number (must be >= current)
     */
    public void updateLastReadSequence(Long sequenceNumber) {
        if (sequenceNumber != null && (this.lastReadSequence == null || sequenceNumber >= this.lastReadSequence)) {
            this.lastReadSequence = sequenceNumber;
        }
    }
}
