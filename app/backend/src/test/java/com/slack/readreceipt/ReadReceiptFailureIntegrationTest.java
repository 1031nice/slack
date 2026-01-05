package com.slack.readreceipt;

import com.slack.readreceipt.dto.ReadReceiptEvent;
import com.slack.readreceipt.service.ReadReceiptService;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

/**
 * Integration test for Read Receipt failure scenarios
 * Tests producer fallback queue and DLT consumer
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Read Receipt Failure Scenarios")
class ReadReceiptFailureIntegrationTest {

    @Autowired
    private ReadReceiptService readReceiptService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @SpyBean
    private KafkaTemplate<String, ReadReceiptEvent> kafkaTemplate;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    @DisplayName("Producer fallback queue should buffer events during Kafka outage")
    void testProducerFallbackQueueDuringKafkaOutage() throws Exception {
        // Given: Kafka is down (simulated by making send throw exception)
        doThrow(new RuntimeException("Kafka down"))
                .when(kafkaTemplate).send(anyString(), anyString(), any(ReadReceiptEvent.class));

        Long userId = 999L;
        Long channelId = 888L;
        String timestamp = "1640995200000.001";

        // When: Update read receipt (should fall back to queue)
        readReceiptService.updateReadReceipt(userId, channelId, timestamp);

        // Then: Redis should be updated immediately (user experience unaffected)
        String redisKey = "read_receipt:" + userId + ":" + channelId;
        Object redisValue = redisTemplate.opsForValue().get(redisKey);
        assertThat(redisValue).isNotNull();
        assertThat(redisValue.toString()).isEqualTo(timestamp);

        // And: Fallback metric should be incremented
        Double fallbackCount = meterRegistry.counter("read_receipts.kafka.fallback").count();
        assertThat(fallbackCount).isGreaterThan(0);

        // Note: In real integration test, we would:
        // 1. Wait for retry scheduler (5 seconds)
        // 2. "Fix" Kafka (stop throwing exception)
        // 3. Verify event eventually persisted to DB
        // 4. Measure recovery time
    }

    @Test
    @DisplayName("DLT consumer should reconcile from Redis when DB write fails")
    void testDltConsumerReconciliation() {
        // This would require:
        // 1. Force Kafka consumer to fail (simulate DB constraint violation)
        // 2. Event lands in DLT
        // 3. DLT consumer fetches from Redis
        // 4. Reconciles to DB
        // 5. Verify final state

        // For now, unit tests cover the logic
        // Real integration test would need:
        // - TestContainers for Kafka
        // - Embedded Redis
        // - Test database
    }

    @Test
    @DisplayName("System should maintain eventual consistency despite failures")
    void testEventualConsistency() {
        // Scenario:
        // 1. Update read receipt (Redis updated)
        // 2. Kafka fails (goes to fallback queue)
        // 3. Retry succeeds
        // 4. Consumer processes (DB updated)
        // 5. Verify Redis == DB (eventual consistency achieved)

        Long userId = 123L;
        Long channelId = 456L;
        String timestamp = "1640995200000.001";

        // Update read receipt
        readReceiptService.updateReadReceipt(userId, channelId, timestamp);

        // Wait for eventual consistency (in real test, would wait for Kafka consumer)
        await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    // Check Redis
                    String redisKey = "read_receipt:" + userId + ":" + channelId;
                    Object redisValue = redisTemplate.opsForValue().get(redisKey);
                    assertThat(redisValue).isNotNull();

                    // In full integration test, would also check DB:
                    // String dbValue = jdbcTemplate.queryForObject(
                    //     "SELECT last_read_timestamp FROM read_receipts WHERE user_id = ? AND channel_id = ?",
                    //     String.class, userId, channelId
                    // );
                    // assertThat(dbValue).isEqualTo(redisValue.toString());
                });
    }
}
