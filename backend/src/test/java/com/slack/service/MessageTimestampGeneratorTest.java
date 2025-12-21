package com.slack.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MessageTimestampGenerator Unit Tests")
class MessageTimestampGeneratorTest {

    private MessageTimestampGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new MessageTimestampGenerator();
    }

    @Test
    @DisplayName("Should generate timestamp ID in correct format")
    void generateTimestampId_CorrectFormat() {
        // when
        String timestampId = generator.generateTimestampId();

        // then
        // Format: {timestamp_microseconds}.{sequence:03d}
        // Example: "1640995200123456.001"
        Pattern pattern = Pattern.compile("^\\d+\\.\\d{3}$");
        assertThat(timestampId).matches(pattern);
    }

    @Test
    @DisplayName("Should generate unique IDs on consecutive calls")
    void generateTimestampId_ConsecutiveCalls_UniqueIds() {
        // when
        String id1 = generator.generateTimestampId();
        String id2 = generator.generateTimestampId();
        String id3 = generator.generateTimestampId();

        // then
        assertThat(id1).isNotEqualTo(id2);
        assertThat(id2).isNotEqualTo(id3);
        assertThat(id1).isNotEqualTo(id3);
    }

    @Test
    @DisplayName("Should generate chronologically ordered IDs")
    void generateTimestampId_ChronologicalOrder() {
        // when
        String id1 = generator.generateTimestampId();
        String id2 = generator.generateTimestampId();
        String id3 = generator.generateTimestampId();

        // then
        // String comparison works because format is zero-padded
        assertThat(id1).isLessThan(id2);
        assertThat(id2).isLessThan(id3);
    }

    @Test
    @DisplayName("Should increment sequence for same microsecond")
    void generateTimestampId_SameMicrosecond_IncrementSequence() {
        // given
        Set<String> ids = new HashSet<>();

        // when - generate many IDs quickly (likely same microsecond)
        for (int i = 0; i < 100; i++) {
            ids.add(generator.generateTimestampId());
        }

        // then - all should be unique
        assertThat(ids).hasSize(100);
    }

    @Test
    @DisplayName("Should handle concurrent ID generation without collisions")
    void generateTimestampId_ConcurrentCalls_NoCollisions() throws InterruptedException {
        // given
        int threadCount = 10;
        int idsPerThread = 1000;
        Set<String> ids = ConcurrentHashMap.newKeySet();
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // when - generate IDs concurrently from multiple threads
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < idsPerThread; j++) {
                        ids.add(generator.generateTimestampId());
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // then - all IDs should be unique (no collisions)
        assertThat(ids).hasSize(threadCount * idsPerThread);
    }

    @RepeatedTest(5)
    @DisplayName("Should generate IDs with very low latency")
    void generateTimestampId_Performance() {
        // given
        int iterations = 10000;
        long startTime = System.nanoTime();

        // when
        for (int i = 0; i < iterations; i++) {
            generator.generateTimestampId();
        }

        long endTime = System.nanoTime();
        long averageNanos = (endTime - startTime) / iterations;
        double averageMicros = averageNanos / 1000.0;

        // then - should be very fast (< 10 microseconds average)
        assertThat(averageMicros).isLessThan(10.0);
    }

    @Test
    @DisplayName("Should parse timestamp and sequence from generated ID")
    void generateTimestampId_ParseComponents() {
        // when
        String timestampId = generator.generateTimestampId();
        String[] parts = timestampId.split("\\.");

        // then
        assertThat(parts).hasSize(2);

        // Timestamp part should be a valid number
        long timestamp = Long.parseLong(parts[0]);
        assertThat(timestamp).isGreaterThan(0);

        // Sequence part should be 3 digits
        String sequence = parts[1];
        assertThat(sequence).hasSize(3);
        assertThat(sequence).matches("\\d{3}");

        int sequenceNum = Integer.parseInt(sequence);
        assertThat(sequenceNum).isBetween(0, 999);
    }

    @Test
    @DisplayName("Should reset sequence when microsecond changes")
    void generateTimestampId_NewMicrosecond_ResetSequence() throws InterruptedException {
        // given - generate first ID
        String id1 = generator.generateTimestampId();

        // when - wait for microsecond to change
        Thread.sleep(1); // 1ms = 1000 microseconds
        String id2 = generator.generateTimestampId();

        // then - second ID should have sequence reset to .000 or .001
        String[] parts2 = id2.split("\\.");
        int sequence2 = Integer.parseInt(parts2[1]);

        // After waiting, sequence should be reset to low number
        assertThat(sequence2).isLessThan(10);
    }

    @Test
    @DisplayName("Should maintain uniqueness under high load")
    void generateTimestampId_HighLoad_MaintainsUniqueness() throws InterruptedException {
        // given
        int iterations = 50000;
        Set<String> ids = ConcurrentHashMap.newKeySet();
        CountDownLatch latch = new CountDownLatch(1);

        // when - single thread, high speed generation
        Thread thread = new Thread(() -> {
            try {
                for (int i = 0; i < iterations; i++) {
                    ids.add(generator.generateTimestampId());
                }
            } finally {
                latch.countDown();
            }
        });

        thread.start();
        latch.await();

        // then - all IDs should be unique
        assertThat(ids).hasSize(iterations);
    }

    @Test
    @DisplayName("Should generate IDs that sort chronologically by string comparison")
    void generateTimestampId_StringSort_ChronologicalOrder() throws InterruptedException {
        // given
        String id1 = generator.generateTimestampId();
        Thread.sleep(1); // Ensure different microsecond
        String id2 = generator.generateTimestampId();
        Thread.sleep(1);
        String id3 = generator.generateTimestampId();

        // when - sort as strings
        java.util.List<String> ids = java.util.Arrays.asList(id3, id1, id2);
        java.util.Collections.sort(ids);

        // then - should be in chronological order
        assertThat(ids).containsExactly(id1, id2, id3);
    }
}
