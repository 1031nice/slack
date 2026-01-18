package com.slack.presence.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Service for tracking user online presence using Redis ZSET.
 * Implements the strategy defined in ADR-07.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PresenceService {

    private static final String PRESENCE_KEY = "presence:online_users";
    private static final long HEARTBEAT_TTL_SECONDS = 60; // Users considered offline after 60s

    private final RedisTemplate<String, String> redisTemplate;

    // Bounded buffer for batched ZADD updates (Deep Dive 07)
    // ARCHITECTURAL NOTE:
    // Ideally, this aggregation should happen at the Gateway/Edge layer (e.g., Netty/Go) to avoid GC pressure.
    // Since we are currently in a Monolithic setup (Spring Boot acts as Gateway), we implement this
    // "Application-Level Buffering" using a Bounded Queue.
    //
    // Strategy: Drop-on-Full (Lossy). If the queue is full (10k), we drop new heartbeats to protect the heap.
    private final BlockingQueue<Long> heartbeatBuffer = new ArrayBlockingQueue<>(10000);

    /**
     * Updates the heartbeat timestamp for a user.
     * Uses a "Write-Behind" pattern with a bounded buffer to prevent Redis overload.
     *
     * @param userId The ID of the user sending the heartbeat.
     */
    public void updateHeartbeat(Long userId) {
        // Non-blocking offer: If queue is full, immediately return false (Drop packet)
        // This acts as a circuit breaker during load spikes.
        boolean added = heartbeatBuffer.offer(userId);
        if (!added) {
            log.warn("Heartbeat buffer full! Dropping update for user {}", userId);
        }
    }

    /**
     * Flushes buffered heartbeats to Redis in batches.
     * Runs every 1 second or can be triggered by buffer size in a real event loop.
     */
    @Scheduled(fixedDelay = 1000)
    public void flushHeartbeats() {
        if (heartbeatBuffer.isEmpty()) {
            return;
        }

        List<Long> batch = new ArrayList<>();
        heartbeatBuffer.drainTo(batch, 1000); // Drain up to 1000 at a time

        if (batch.isEmpty()) return;

        double now = Instant.now().getEpochSecond();

        // Execute Batch ZADD via Pipeline
        redisTemplate.executePipelined((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
            for (Long userId : batch) {
                // ZADD presence:online_users <now> <userId>
                connection.zSetCommands().zAdd(PRESENCE_KEY.getBytes(), now, userId.toString().getBytes());
            }
            return null;
        });

        log.debug("Flushed {} heartbeats to Redis.", batch.size());
    }

    /**
     * Bulk checks the online status of a list of users.
     * Uses Redis Pipeline to fetch scores in a single round-trip (O(1) RTT).
     *
     * @param userIds List of user IDs to check.
     * @return Set of user IDs that are currently considered online.
     */
    public Set<Long> getOnlineUsers(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Set.of();
        }

        // Current threshold timestamp
        double minScore = Instant.now().getEpochSecond() - HEARTBEAT_TTL_SECONDS;

        List<Object> scores = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (Long userId : userIds) {
                connection.zSetCommands().zScore(PRESENCE_KEY.getBytes(), userId.toString().getBytes());
            }
            return null;
        });

        Set<Long> onlineUsers = new HashSet<>();
        for (int i = 0; i < scores.size(); i++) {
            Object scoreObj = scores.get(i);
            if (scoreObj != null) {
                // Redis returns Double for scores
                double score = (Double) scoreObj;
                if (score >= minScore) {
                    onlineUsers.add(userIds.get(i));
                }
            }
        }

        return onlineUsers;
    }

    /**
     * Removes users who haven't sent a heartbeat within the TTL.
     * Scheduled to run periodically.
     */
    @Scheduled(fixedDelay = 10000) // Run every 10 seconds
    public void removeStaleUsers() {
        double maxScore = Instant.now().getEpochSecond() - HEARTBEAT_TTL_SECONDS;

        // ZREMRANGEBYSCORE: Remove all members with score < (now - 60s)
        Long removedCount = redisTemplate.opsForZSet().removeRangeByScore(PRESENCE_KEY, 0, maxScore);

        if (removedCount != null && removedCount > 0) {
            log.info("Pruned {} stale users from presence set.", removedCount);
        }
    }
}
