package com.slack.readreceipt.service;

import com.slack.channel.domain.Channel;
import com.slack.channel.domain.ChannelType;
import com.slack.readreceipt.domain.ReadReceipt;
import com.slack.user.domain.User;
import com.slack.workspace.domain.Workspace;
import com.slack.readreceipt.repository.ReadReceiptRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReadReceiptReconciliationService 단위 테스트")
class ReadReceiptReconciliationServiceTest {

    @Mock
    private ReadReceiptRepository readReceiptRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private Gauge gauge;

    @Mock
    private Counter counter;

    @Mock
    private Timer timer;

    @InjectMocks
    private ReadReceiptReconciliationService reconciliationService;

    private User testUser1;
    private User testUser2;
    private Workspace testWorkspace;
    private Channel testChannel;
    private ReadReceipt staleReceipt1;
    private ReadReceipt staleReceipt2;

    @BeforeEach
    void setUp() throws Exception {
        testUser1 = User.builder()
                .authUserId("auth-1")
                .email("user1@example.com")
                .name("John")
                .build();
        setField(testUser1, "id", 1L);

        testUser2 = User.builder()
                .authUserId("auth-2")
                .email("user2@example.com")
                .name("Jane")
                .build();
        setField(testUser2, "id", 2L);

        testWorkspace = Workspace.builder()
                .name("Test Workspace")
                .build();
        setField(testWorkspace, "id", 1L);

        testChannel = Channel.builder()
                .workspace(testWorkspace)
                .name("general")
                .type(ChannelType.PUBLIC)
                .createdBy(1L)
                .build();
        setField(testChannel, "id", 100L);

        staleReceipt1 = ReadReceipt.builder()
                .user(testUser1)
                .channel(testChannel)
                .lastReadTimestamp("1735046400000050")
                .build();
        setField(staleReceipt1, "updatedAt", LocalDateTime.now().minusMinutes(15));

        staleReceipt2 = ReadReceipt.builder()
                .user(testUser2)
                .channel(testChannel)
                .lastReadTimestamp("1735046400000040")
                .build();
        setField(staleReceipt2, "updatedAt", LocalDateTime.now().minusMinutes(20));

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(meterRegistry.gauge(anyString(), anyDouble())).thenReturn(null);
        lenient().when(meterRegistry.counter(anyString())).thenReturn(counter);
        lenient().when(meterRegistry.timer(anyString())).thenReturn(timer);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    @DisplayName("stale한 레코드가 없으면 아무 작업도 하지 않는다")
    void reconcileReadReceipts_NoStaleRecords() {
        // given
        when(readReceiptRepository.findByUpdatedAtBefore(any(LocalDateTime.class)))
                .thenReturn(List.of());

        // when
        reconciliationService.reconcileReadReceipts();

        // then
        verify(readReceiptRepository, never()).save(any(ReadReceipt.class));
        verify(meterRegistry).gauge(eq("read_receipts.reconciliation.stale_count"), eq(0));
    }

    @Test
    @DisplayName("Redis가 DB보다 최신이면 DB를 업데이트한다")
    void reconcileReadReceipts_RedisNewerThanDb() {
        // given
        when(readReceiptRepository.findByUpdatedAtBefore(any(LocalDateTime.class)))
                .thenReturn(List.of(staleReceipt1));

        String redisKey = "read_receipt:1:100";
        String newerTimestamp = "1735046400000100";  // DB보다 최신
        when(valueOperations.get(redisKey)).thenReturn(newerTimestamp);

        // when
        reconciliationService.reconcileReadReceipts();

        // then
        verify(readReceiptRepository, times(1)).save(staleReceipt1);
        assertThat(staleReceipt1.getLastReadTimestamp()).isEqualTo(newerTimestamp);
        verify(counter).increment(1.0);  // fixed_count 증가
    }

    @Test
    @DisplayName("Redis가 DB보다 오래되었으면 업데이트하지 않는다")
    void reconcileReadReceipts_RedisOlderThanDb() {
        // given
        when(readReceiptRepository.findByUpdatedAtBefore(any(LocalDateTime.class)))
                .thenReturn(List.of(staleReceipt1));

        String redisKey = "read_receipt:1:100";
        String olderTimestamp = "1735046400000010";  // DB보다 오래됨
        when(valueOperations.get(redisKey)).thenReturn(olderTimestamp);

        // when
        reconciliationService.reconcileReadReceipts();

        // then
        verify(readReceiptRepository, never()).save(any(ReadReceipt.class));
        assertThat(staleReceipt1.getLastReadTimestamp()).isEqualTo("1735046400000050");  // 변경 없음
    }

    @Test
    @DisplayName("Redis에 값이 없으면 업데이트하지 않는다 (eviction)")
    void reconcileReadReceipts_RedisEvicted() {
        // given
        when(readReceiptRepository.findByUpdatedAtBefore(any(LocalDateTime.class)))
                .thenReturn(List.of(staleReceipt1));

        String redisKey = "read_receipt:1:100";
        when(valueOperations.get(redisKey)).thenReturn(null);  // Redis에서 eviction됨

        // when
        reconciliationService.reconcileReadReceipts();

        // then
        verify(readReceiptRepository, never()).save(any(ReadReceipt.class));
    }

    @Test
    @DisplayName("여러 stale 레코드 중 divergence가 있는 것만 수정한다")
    void reconcileReadReceipts_MultipleRecords_PartialFix() {
        // given
        when(readReceiptRepository.findByUpdatedAtBefore(any(LocalDateTime.class)))
                .thenReturn(Arrays.asList(staleReceipt1, staleReceipt2));

        // staleReceipt1: Redis가 더 최신 → 수정 필요
        when(valueOperations.get("read_receipt:1:100")).thenReturn("1735046400000100");

        // staleReceipt2: Redis가 더 오래됨 → 수정 불필요
        when(valueOperations.get("read_receipt:2:100")).thenReturn("1735046400000030");

        // when
        reconciliationService.reconcileReadReceipts();

        // then
        verify(readReceiptRepository, times(1)).save(staleReceipt1);
        verify(readReceiptRepository, never()).save(staleReceipt2);
        verify(counter).increment(1.0);  // 1개만 수정됨
    }

    @Test
    @DisplayName("예외 발생 시 에러 메트릭을 기록한다")
    void reconcileReadReceipts_ExceptionOccurs() {
        // given
        when(readReceiptRepository.findByUpdatedAtBefore(any(LocalDateTime.class)))
                .thenThrow(new RuntimeException("DB error"));

        // when
        reconciliationService.reconcileReadReceipts();

        // then
        verify(meterRegistry.counter("read_receipts.reconciliation.errors")).increment();
    }

    @Test
    @DisplayName("timestamp 비교는 문자열 비교로 수행된다")
    void reconcileReadReceipts_StringComparison() {
        // given
        when(readReceiptRepository.findByUpdatedAtBefore(any(LocalDateTime.class)))
                .thenReturn(List.of(staleReceipt1));

        String redisKey = "read_receipt:1:100";
        // Lexicographic 비교: "1735046400000051" > "1735046400000050"
        when(valueOperations.get(redisKey)).thenReturn("1735046400000051");

        // when
        reconciliationService.reconcileReadReceipts();

        // then
        verify(readReceiptRepository, times(1)).save(staleReceipt1);
        assertThat(staleReceipt1.getLastReadTimestamp()).isEqualTo("1735046400000051");
    }

    @Test
    @DisplayName("Redis와 DB가 동일하면 업데이트하지 않는다")
    void reconcileReadReceipts_RedisEqualToDb() {
        // given
        when(readReceiptRepository.findByUpdatedAtBefore(any(LocalDateTime.class)))
                .thenReturn(List.of(staleReceipt1));

        String redisKey = "read_receipt:1:100";
        when(valueOperations.get(redisKey)).thenReturn("1735046400000050");  // DB와 동일

        // when
        reconciliationService.reconcileReadReceipts();

        // then
        verify(readReceiptRepository, never()).save(any(ReadReceipt.class));
    }
}
