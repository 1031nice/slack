package com.slack.service;

import com.slack.domain.readreceipt.ReadReceipt;
import com.slack.repository.ReadReceiptRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Reconciliation service for detecting and fixing Redis-DB divergence
 * Scheduled job runs every 5 minutes to ensure eventual consistency
 * ADR-0007: Kafka-based batching for read receipt persistence
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReadReceiptReconciliationService {

    private static final String READ_RECEIPT_KEY_PREFIX = "read_receipt:";
    private static final int STALE_THRESHOLD_MINUTES = 10;

    private final ReadReceiptRepository readReceiptRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final MeterRegistry meterRegistry;

    /**
     * Reconcile stale read receipts between Redis and DB
     * Runs every 5 minutes to detect and fix divergence
     *
     * Process:
     * 1. Find DB records not updated in last 10 minutes (potential lag)
     * 2. Compare with Redis values
     * 3. If Redis is newer, update DB (fix divergence)
     * 4. Track metrics for monitoring
     */
    @Scheduled(cron = "0 */5 * * * *")  // Every 5 minutes
    @Transactional
    public void reconcileReadReceipts() {
        long startTime = System.currentTimeMillis();
        log.info("Starting read receipt reconciliation job");

        try {
            // Find records where DB might be stale (updated_at > 10 min ago)
            LocalDateTime staleThreshold = LocalDateTime.now().minusMinutes(STALE_THRESHOLD_MINUTES);
            List<ReadReceipt> staleRecords = readReceiptRepository.findByUpdatedAtBefore(staleThreshold);

            if (staleRecords.isEmpty()) {
                log.info("Reconciliation complete: No stale records found");
                recordMetrics(0, 0, 0);
                return;
            }

            log.info("Found {} potentially stale records (updated_at < {})",
                    staleRecords.size(), staleThreshold);

            int fixedCount = 0;
            long maxDivergenceMs = 0;

            for (ReadReceipt dbRecord : staleRecords) {
                String redisKey = buildRedisKey(dbRecord.getUser().getId(), dbRecord.getChannel().getId());
                Object redisValue = redisTemplate.opsForValue().get(redisKey);

                if (redisValue == null) {
                    // Redis evicted - DB is source of truth, no action needed
                    continue;
                }

                String redisTimestamp = redisValue.toString();
                String dbTimestamp = dbRecord.getLastReadTimestamp();

                // Compare timestamps - Redis should be >= DB
                if (redisTimestamp.compareTo(dbTimestamp) > 0) {
                    // Redis is newer - DB is stale, fix it
                    log.warn("Detected Redis-DB divergence: userId={}, channelId={}, " +
                                    "redisTimestamp={}, dbTimestamp={}",
                            dbRecord.getUser().getId(),
                            dbRecord.getChannel().getId(),
                            redisTimestamp,
                            dbTimestamp);

                    dbRecord.updateLastReadTimestamp(redisTimestamp);
                    readReceiptRepository.save(dbRecord);
                    fixedCount++;

                    // Calculate divergence age
                    long divergenceMs = Duration.between(
                            dbRecord.getUpdatedAt(),
                            LocalDateTime.now()
                    ).toMillis();
                    maxDivergenceMs = Math.max(maxDivergenceMs, divergenceMs);
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("Reconciliation complete: checked={}, fixed={}, maxDivergence={}ms, duration={}ms",
                    staleRecords.size(), fixedCount, maxDivergenceMs, duration);

            recordMetrics(staleRecords.size(), fixedCount, maxDivergenceMs);

        } catch (Exception e) {
            log.error("Reconciliation job failed", e);
            meterRegistry.counter("read_receipts.reconciliation.errors").increment();
        }
    }

    /**
     * Record reconciliation metrics for monitoring
     *
     * @param staleCount Number of stale records checked
     * @param fixedCount Number of records fixed
     * @param maxDivergenceMs Maximum divergence age in milliseconds
     */
    private void recordMetrics(int staleCount, int fixedCount, long maxDivergenceMs) {
        meterRegistry.gauge("read_receipts.reconciliation.stale_count", staleCount);
        meterRegistry.counter("read_receipts.reconciliation.fixed_count").increment(fixedCount);

        if (maxDivergenceMs > 0) {
            meterRegistry.timer("read_receipts.reconciliation.max_divergence")
                    .record(maxDivergenceMs, TimeUnit.MILLISECONDS);
        }
    }

    private String buildRedisKey(Long userId, Long channelId) {
        return READ_RECEIPT_KEY_PREFIX + userId + ":" + channelId;
    }
}
