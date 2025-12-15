package com.slack.service;

import com.slack.exception.SequenceGenerationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MessageSequenceService Unit Tests")
class MessageSequenceServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private MessageSequenceService messageSequenceService;

    private static final Long VALID_CHANNEL_ID = 100L;
    private static final String EXPECTED_KEY = "slack:sequence:channel:" + VALID_CHANNEL_ID;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("Should generate first sequence number as 1")
    void getNextSequenceNumber_FirstSequence() {
        // given
        when(valueOperations.increment(EXPECTED_KEY)).thenReturn(1L);

        // when
        Long result = messageSequenceService.getNextSequenceNumber(VALID_CHANNEL_ID);

        // then
        assertThat(result).isEqualTo(1L);
        verify(valueOperations, times(1)).increment(EXPECTED_KEY);
    }

    @Test
    @DisplayName("Should increment sequence number on consecutive calls")
    void getNextSequenceNumber_ConsecutiveCalls() {
        // given
        when(valueOperations.increment(EXPECTED_KEY))
            .thenReturn(1L)
            .thenReturn(2L)
            .thenReturn(3L);

        // when
        Long first = messageSequenceService.getNextSequenceNumber(VALID_CHANNEL_ID);
        Long second = messageSequenceService.getNextSequenceNumber(VALID_CHANNEL_ID);
        Long third = messageSequenceService.getNextSequenceNumber(VALID_CHANNEL_ID);

        // then
        assertThat(first).isEqualTo(1L);
        assertThat(second).isEqualTo(2L);
        assertThat(third).isEqualTo(3L);
        verify(valueOperations, times(3)).increment(EXPECTED_KEY);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when channelId is null")
    void getNextSequenceNumber_NullChannelId() {
        // when & then
        assertThatThrownBy(() -> messageSequenceService.getNextSequenceNumber(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid channel ID");

        verify(valueOperations, never()).increment(anyString());
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when channelId is zero")
    void getNextSequenceNumber_ZeroChannelId() {
        // when & then
        assertThatThrownBy(() -> messageSequenceService.getNextSequenceNumber(0L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid channel ID");

        verify(valueOperations, never()).increment(anyString());
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when channelId is negative")
    void getNextSequenceNumber_NegativeChannelId() {
        // when & then
        assertThatThrownBy(() -> messageSequenceService.getNextSequenceNumber(-1L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid channel ID");

        verify(valueOperations, never()).increment(anyString());
    }

    @Test
    @DisplayName("Should throw SequenceGenerationException when Redis returns null")
    void getNextSequenceNumber_RedisReturnsNull() {
        // given
        when(valueOperations.increment(EXPECTED_KEY)).thenReturn(null);

        // when & then
        assertThatThrownBy(() -> messageSequenceService.getNextSequenceNumber(VALID_CHANNEL_ID))
            .isInstanceOf(SequenceGenerationException.class)
            .hasMessageContaining("Failed to generate sequence number for channel: " + VALID_CHANNEL_ID);

        verify(valueOperations, times(1)).increment(EXPECTED_KEY);
    }

    @Test
    @DisplayName("Should generate different sequences for different channels")
    void getNextSequenceNumber_DifferentChannels() {
        // given
        Long channelId1 = 100L;
        Long channelId2 = 200L;
        String key1 = "slack:sequence:channel:" + channelId1;
        String key2 = "slack:sequence:channel:" + channelId2;

        when(valueOperations.increment(key1)).thenReturn(1L);
        when(valueOperations.increment(key2)).thenReturn(1L);

        // when
        Long sequence1 = messageSequenceService.getNextSequenceNumber(channelId1);
        Long sequence2 = messageSequenceService.getNextSequenceNumber(channelId2);

        // then
        assertThat(sequence1).isEqualTo(1L);
        assertThat(sequence2).isEqualTo(1L);
        verify(valueOperations, times(1)).increment(key1);
        verify(valueOperations, times(1)).increment(key2);
    }

    @Test
    @DisplayName("Should handle large sequence numbers")
    void getNextSequenceNumber_LargeSequenceNumber() {
        // given
        Long largeSequence = 999999L;
        when(valueOperations.increment(EXPECTED_KEY)).thenReturn(largeSequence);

        // when
        Long result = messageSequenceService.getNextSequenceNumber(VALID_CHANNEL_ID);

        // then
        assertThat(result).isEqualTo(largeSequence);
        verify(valueOperations, times(1)).increment(EXPECTED_KEY);
    }

    @Test
    @DisplayName("Should use correct Redis key format")
    void getNextSequenceNumber_CorrectKeyFormat() {
        // given
        Long channelId = 12345L;
        String expectedKey = "slack:sequence:channel:12345";
        when(valueOperations.increment(expectedKey)).thenReturn(1L);

        // when
        messageSequenceService.getNextSequenceNumber(channelId);

        // then
        verify(valueOperations, times(1)).increment(expectedKey);
    }
}
