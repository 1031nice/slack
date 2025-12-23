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
     * 마지막으로 읽은 메시지의 timestamp
     * 이 timestamp 이하의 모든 메시지를 읽은 것으로 간주
     * Format: timestampId (e.g., "1735046400000001") or ISO datetime
     */
    @Column(name = "last_read_timestamp", nullable = false, length = 30)
    private String lastReadTimestamp;

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
    public ReadReceipt(User user, Channel channel, String lastReadTimestamp) {
        this.user = user;
        this.channel = channel;
        this.lastReadTimestamp = lastReadTimestamp;
    }

    /**
     * Update last read timestamp
     *
     * @param timestamp New timestamp (must be >= current lexicographically)
     */
    public void updateLastReadTimestamp(String timestamp) {
        if (timestamp != null && (this.lastReadTimestamp == null || timestamp.compareTo(this.lastReadTimestamp) >= 0)) {
            this.lastReadTimestamp = timestamp;
        }
    }
}
