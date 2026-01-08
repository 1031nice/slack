package com.slack.readreceipt.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReadReceiptEvent 단위 테스트")
class ReadReceiptEventTest {

    @Test
    @DisplayName("ReadReceiptEvent를 생성할 수 있다")
    void createReadReceiptEvent() {
        // given
        Long userId = 1L;
        Long channelId = 100L;
        String timestamp = "1735046400000050";
        Instant createdAt = Instant.parse("2025-12-28T00:00:00Z");

        // when
        ReadReceiptEvent event = ReadReceiptEvent.builder()
                .userId(userId)
                .channelId(channelId)
                .lastReadTimestamp(timestamp)
                .createdAt(createdAt)
                .build();

        // then
        assertThat(event.getUserId()).isEqualTo(userId);
        assertThat(event.getChannelId()).isEqualTo(channelId);
        assertThat(event.getLastReadTimestamp()).isEqualTo(timestamp);
        assertThat(event.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    @DisplayName("partition key는 userId:channelId 형식이다")
    void getPartitionKey() {
        // given
        ReadReceiptEvent event = ReadReceiptEvent.builder()
                .userId(1L)
                .channelId(100L)
                .lastReadTimestamp("1735046400000050")
                .build();

        // when
        String partitionKey = event.getPartitionKey();

        // then
        assertThat(partitionKey).isEqualTo("1:100");
    }

    @Test
    @DisplayName("partition key는 동일한 user-channel 조합에 대해 동일하다")
    void getPartitionKey_SameUserChannel() {
        // given
        ReadReceiptEvent event1 = ReadReceiptEvent.builder()
                .userId(1L)
                .channelId(100L)
                .lastReadTimestamp("1735046400000050")
                .build();

        ReadReceiptEvent event2 = ReadReceiptEvent.builder()
                .userId(1L)
                .channelId(100L)
                .lastReadTimestamp("1735046400000060")
                .build();

        // when & then
        assertThat(event1.getPartitionKey()).isEqualTo(event2.getPartitionKey());
    }

    @Test
    @DisplayName("partition key는 다른 user-channel 조합에 대해 다르다")
    void getPartitionKey_DifferentUserChannel() {
        // given
        ReadReceiptEvent event1 = ReadReceiptEvent.builder()
                .userId(1L)
                .channelId(100L)
                .lastReadTimestamp("1735046400000050")
                .build();

        ReadReceiptEvent event2 = ReadReceiptEvent.builder()
                .userId(2L)
                .channelId(100L)
                .lastReadTimestamp("1735046400000050")
                .build();

        ReadReceiptEvent event3 = ReadReceiptEvent.builder()
                .userId(1L)
                .channelId(200L)
                .lastReadTimestamp("1735046400000050")
                .build();

        // when & then
        assertThat(event1.getPartitionKey()).isNotEqualTo(event2.getPartitionKey());
        assertThat(event1.getPartitionKey()).isNotEqualTo(event3.getPartitionKey());
        assertThat(event2.getPartitionKey()).isNotEqualTo(event3.getPartitionKey());
    }

    @Test
    @DisplayName("createdAt이 없으면 기본값으로 현재 시각이 설정된다")
    void defaultCreatedAt() {
        // given
        Instant before = Instant.now();

        // when
        ReadReceiptEvent event = ReadReceiptEvent.builder()
                .userId(1L)
                .channelId(100L)
                .lastReadTimestamp("1735046400000050")
                .build();

        Instant after = Instant.now();

        // then
        assertThat(event.getCreatedAt()).isNotNull();
        assertThat(event.getCreatedAt()).isBetween(before, after);
    }

    @Test
    @DisplayName("빌더를 사용하여 모든 필드를 설정할 수 있다")
    void builderWithAllFields() {
        // given & when
        ReadReceiptEvent event = ReadReceiptEvent.builder()
                .userId(123L)
                .channelId(456L)
                .lastReadTimestamp("1735046400000999")
                .createdAt(Instant.parse("2025-12-28T12:00:00Z"))
                .build();

        // then
        assertThat(event.getUserId()).isEqualTo(123L);
        assertThat(event.getChannelId()).isEqualTo(456L);
        assertThat(event.getLastReadTimestamp()).isEqualTo("1735046400000999");
        assertThat(event.getCreatedAt()).isEqualTo(Instant.parse("2025-12-28T12:00:00Z"));
    }
}
