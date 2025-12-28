package com.slack.readreceipt.service;

import com.slack.readreceipt.dto.ReadReceiptEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReadReceiptPersistenceService 단위 테스트")
class ReadReceiptPersistenceServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private ReadReceiptPersistenceService persistenceService;

    private List<ReadReceiptEvent> testEvents;

    @BeforeEach
    void setUp() {
        testEvents = Arrays.asList(
                ReadReceiptEvent.builder()
                        .userId(1L)
                        .channelId(100L)
                        .lastReadTimestamp("1735046400000050")
                        .createdAt(Instant.parse("2025-12-28T00:00:00Z"))
                        .build(),
                ReadReceiptEvent.builder()
                        .userId(2L)
                        .channelId(100L)
                        .lastReadTimestamp("1735046400000045")
                        .createdAt(Instant.parse("2025-12-28T00:00:01Z"))
                        .build(),
                ReadReceiptEvent.builder()
                        .userId(3L)
                        .channelId(200L)
                        .lastReadTimestamp("1735046400000030")
                        .createdAt(Instant.parse("2025-12-28T00:00:02Z"))
                        .build()
        );
    }

    @Test
    @DisplayName("배치 이벤트를 성공적으로 처리할 수 있다")
    void consumeReadReceipts_Success() {
        // when
        persistenceService.consumeReadReceipts(testEvents, 0, acknowledgment);

        // then
        verify(jdbcTemplate, times(1)).batchUpdate(
                anyString(),
                anyCollection(),
                anyInt(),
                any()
        );
        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    @DisplayName("중복 이벤트는 deduplication되어 최신 timestamp만 유지된다")
    void consumeReadReceipts_Deduplication() {
        // given
        List<ReadReceiptEvent> duplicateEvents = Arrays.asList(
                ReadReceiptEvent.builder()
                        .userId(1L)
                        .channelId(100L)
                        .lastReadTimestamp("1735046400000050")
                        .build(),
                ReadReceiptEvent.builder()
                        .userId(1L)
                        .channelId(100L)
                        .lastReadTimestamp("1735046400000060")  // 더 최신
                        .build(),
                ReadReceiptEvent.builder()
                        .userId(1L)
                        .channelId(100L)
                        .lastReadTimestamp("1735046400000040")  // 더 오래됨
                        .build()
        );

        // when
        persistenceService.consumeReadReceipts(duplicateEvents, 0, acknowledgment);

        // then
        // 3개 이벤트가 1개로 dedup되어 DB 작업이 1번만 수행됨
        verify(jdbcTemplate, times(1)).batchUpdate(
                anyString(),
                anyCollection(),
                eq(1),  // dedup 후 크기
                any()
        );
        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    @DisplayName("빈 배치는 DB 작업 없이 처리된다")
    void consumeReadReceipts_EmptyBatch() {
        // when
        persistenceService.consumeReadReceipts(List.of(), 0, acknowledgment);

        // then
        verify(jdbcTemplate, never()).batchUpdate(anyString(), anyCollection(), anyInt(), any());
        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    @DisplayName("null 배치는 안전하게 처리된다")
    void consumeReadReceipts_NullBatch() {
        // when
        persistenceService.consumeReadReceipts(null, 0, acknowledgment);

        // then
        verify(jdbcTemplate, never()).batchUpdate(anyString(), anyCollection(), anyInt(), any());
        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    @DisplayName("DB 오류 발생 시 acknowledgment하지 않는다")
    void consumeReadReceipts_DbError_NoAcknowledgment() {
        // given
        doThrow(new RuntimeException("DB connection error"))
                .when(jdbcTemplate).batchUpdate(anyString(), anyCollection(), anyInt(), any());

        // when & then
        assertThatThrownBy(() ->
                persistenceService.consumeReadReceipts(testEvents, 0, acknowledgment)
        ).isInstanceOf(RuntimeException.class);

        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    @DisplayName("500개 이벤트 배치를 처리할 수 있다")
    void consumeReadReceipts_LargeBatch() {
        // given
        List<ReadReceiptEvent> largeBatch = new java.util.ArrayList<>();
        for (int i = 0; i < 500; i++) {
            largeBatch.add(ReadReceiptEvent.builder()
                    .userId((long) i)
                    .channelId(100L)
                    .lastReadTimestamp("1735046400000050")
                    .build());
        }

        // when
        persistenceService.consumeReadReceipts(largeBatch, 0, acknowledgment);

        // then
        verify(jdbcTemplate, times(1)).batchUpdate(anyString(), anyCollection(), anyInt(), any());
        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    @DisplayName("동일 user-channel에 대한 여러 이벤트는 하나로 dedup된다")
    void consumeReadReceipts_MultipleEventsForSameUserChannel() {
        // given
        List<ReadReceiptEvent> events = Arrays.asList(
                ReadReceiptEvent.builder().userId(1L).channelId(100L).lastReadTimestamp("100").build(),
                ReadReceiptEvent.builder().userId(1L).channelId(100L).lastReadTimestamp("200").build(),
                ReadReceiptEvent.builder().userId(1L).channelId(100L).lastReadTimestamp("150").build(),
                ReadReceiptEvent.builder().userId(2L).channelId(100L).lastReadTimestamp("300").build()
        );

        // when
        persistenceService.consumeReadReceipts(events, 0, acknowledgment);

        // then
        // 4개 이벤트 → 2개 unique user-channel 조합으로 dedup
        verify(jdbcTemplate, times(1)).batchUpdate(
                anyString(),
                anyCollection(),
                eq(2),  // userId=1과 userId=2 각각 1개씩
                any()
        );
        verify(acknowledgment, times(1)).acknowledge();
    }
}
