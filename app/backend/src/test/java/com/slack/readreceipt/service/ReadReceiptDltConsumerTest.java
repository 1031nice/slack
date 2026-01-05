package com.slack.readreceipt.service;

import com.slack.readreceipt.dto.ReadReceiptEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReadReceiptDltConsumer Tests")
class ReadReceiptDltConsumerTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private Counter counter;

    @InjectMocks
    private ReadReceiptDltConsumer dltConsumer;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(meterRegistry.counter(anyString())).thenReturn(counter);
    }

    @Test
    @DisplayName("DLT consumer should reconcile using Redis value when available")
    void shouldReconcileUsingRedisValue() {
        // Given
        Long userId = 1L;
        Long channelId = 100L;
        String eventTimestamp = "1000";
        String redisTimestamp = "2000";  // Redis has newer value

        ReadReceiptEvent event = ReadReceiptEvent.builder()
                .userId(userId)
                .channelId(channelId)
                .lastReadTimestamp(eventTimestamp)
                .build();

        when(valueOperations.get("read_receipt:1:100")).thenReturn(redisTimestamp);
        when(jdbcTemplate.update(anyString(), any(), any(), any())).thenReturn(1);

        // When
        dltConsumer.handleFailedReadReceipt(event);

        // Then
        verify(valueOperations).get("read_receipt:1:100");
        verify(jdbcTemplate).update(
                contains("INSERT INTO read_receipts"),
                eq(userId),
                eq(channelId),
                eq(redisTimestamp)  // Should use Redis value
        );
        verify(counter).increment();
    }

    @Test
    @DisplayName("DLT consumer should use event value when Redis is evicted")
    void shouldUseEventValueWhenRedisEvicted() {
        // Given
        Long userId = 1L;
        Long channelId = 100L;
        String eventTimestamp = "1000";

        ReadReceiptEvent event = ReadReceiptEvent.builder()
                .userId(userId)
                .channelId(channelId)
                .lastReadTimestamp(eventTimestamp)
                .build();

        when(valueOperations.get("read_receipt:1:100")).thenReturn(null);  // Redis evicted
        when(jdbcTemplate.update(anyString(), any(), any(), any())).thenReturn(1);

        // When
        dltConsumer.handleFailedReadReceipt(event);

        // Then
        verify(jdbcTemplate).update(
                contains("INSERT INTO read_receipts"),
                eq(userId),
                eq(channelId),
                eq(eventTimestamp)  // Should use event value
        );
        verify(counter).increment();
    }

    @Test
    @DisplayName("DLT consumer should track failure metric when DB update fails")
    void shouldTrackFailureMetricWhenDbFails() {
        // Given
        ReadReceiptEvent event = ReadReceiptEvent.builder()
                .userId(1L)
                .channelId(100L)
                .lastReadTimestamp("1000")
                .build();

        when(valueOperations.get(anyString())).thenReturn("2000");
        when(jdbcTemplate.update(anyString(), any(), any(), any()))
                .thenThrow(new RuntimeException("DB error"));

        Counter failureCounter = mock(Counter.class);
        when(meterRegistry.counter("read_receipts.dlt.failed")).thenReturn(failureCounter);

        // When/Then
        try {
            dltConsumer.handleFailedReadReceipt(event);
        } catch (RuntimeException e) {
            // Expected
        }

        verify(failureCounter).increment();
    }

    @Test
    @DisplayName("DLT consumer should use GREATEST() for order resolution")
    void shouldUseGreatestForOrderResolution() {
        // Given
        ReadReceiptEvent event = ReadReceiptEvent.builder()
                .userId(1L)
                .channelId(100L)
                .lastReadTimestamp("1000")
                .build();

        when(valueOperations.get(anyString())).thenReturn("1000");
        when(jdbcTemplate.update(anyString(), any(), any(), any())).thenReturn(1);

        // When
        dltConsumer.handleFailedReadReceipt(event);

        // Then
        verify(jdbcTemplate).update(
                contains("GREATEST("),
                eq(1L),
                eq(100L),
                eq("1000")
        );
    }
}
