# ADR-0007: Kafka-Based Batching for Read Receipt Persistence

- **Status**: Proposed 🔄
- **Date**: 2025-12-26
- **Context**: v0.4 - Read Status & Notifications
- **Related**: [ADR-0002: Eventual Consistency for Read Status](./0002-eventual-consistency-for-read-status.md)

---

## Problem Statement

Current implementation of read receipt persistence has **critical scalability and consistency issues** that would fail at real Slack scale:

### Issue 1: Unbounded Async Processing

**Current code** (`ReadReceiptService.java:165-194`):
```java
@Async
@Transactional(propagation = Propagation.REQUIRES_NEW)
protected void persistToDatabase(Long userId, Long channelId, String lastReadTimestamp) {
    // Every read receipt update triggers async DB write
    // No batching, no rate limiting, no backpressure
}
```

**Failure scenario at scale:**
```
10,000 concurrent users × 10 channels × 1 read/minute
= 100,000 read receipt updates/minute
= 1,667 async DB writes/second

Result:
1. @Async thread pool exhaustion (default maxPoolSize: Integer.MAX_VALUE)
2. DB connection pool depletion (HikariCP default: 10 connections)
3. DB write throughput bottleneck → latency spike → queue buildup
4. OutOfMemoryError from unbounded task queue
5. System-wide outage
```

### Issue 2: Redis-DB Inconsistency Without Reconciliation

**Current problems:**

1. **Order inversion**:
   ```
   T1: User reads message at timestamp=1000 → async DB write starts
   T2: User reads message at timestamp=2000 → async DB write starts
   DB: timestamp=2000 commits first, timestamp=1000 commits later
   Result: DB has stale timestamp=1000
   ```

2. **No conflict resolution**:
   - Redis write succeeds, DB write fails → divergence
   - No reconciliation job to detect/fix inconsistencies
   - Redis eviction during DB write lag → data loss

3. **Silent failures**:
   ```java
   catch (Exception e) {
       log.error("Failed to persist..."); // Just log, no retry
   }
   ```
   - Failed writes are lost forever
   - No metrics, no alerting, no recovery

### Real Slack Scale Requirements

Based on Slack Engineering data:

| Metric | Value | Source |
|--------|-------|--------|
| Daily active users | 20M+ | Public data |
| Peak jobs/second | 33,000 | [Scaling Slack's Job Queue](https://slack.engineering/scaling-slacks-job-queue/) |
| Read receipts/user/day | ~100-500 (estimate) | Channel switches, message reads |
| **Total read receipts/day** | **~2-10 billion** | 20M users × 100-500 |
| **Peak writes/second** | **~50,000-100,000** | Assuming 4x peak vs average |

**Current implementation would fail catastrophically at this scale.**

---

## Context

### Read Receipt Characteristics

- **Update frequency**: Very high (every channel view, every message read)
- **Write pattern**: Bursty (morning work hours, after meetings)
- **Criticality**: Low (lag acceptable, unlike message delivery)
- **Consistency requirement**: Eventual (Redis is source of truth for real-time)
- **Durability requirement**: Must survive Redis crash (restore from DB)

### Real Slack's Evolution

Slack faced identical scalability issues with their **job queue system**, which processed similar high-frequency, low-criticality updates:

**Problem they faced:**
> "Redis had little operational headroom, particularly with respect to memory... slower dequeuing than enqueueing caused Redis to max out, preventing new job writes... dequeuing also requires having enough memory to move the job"

**Their solution:**
- **Kafka as durable buffer** between application and Redis
- **Decoupled enqueue/dequeue rates** via configurable rate limiting
- **Services**: Kafkagate (enqueue) → Kafka → JQRelay (dequeue with rate limits)
- **Scale**: 1.4 billion jobs/day, 33,000 jobs/second at peak

Source: [Scaling Slack's Job Queue](https://slack.engineering/scaling-slacks-job-queue/)

---

## Proposed Solutions

### Option 1: Current Implementation (@Async, Unbounded) ❌

**Architecture:**
```
Read receipt update → Redis write (sync) → @Async DB write (unbounded)
```

**Pros:**
- ✅ Simple implementation (already done)
- ✅ No additional infrastructure (no Kafka needed)

**Cons:**
- ❌ **CRITICAL: Thread pool exhaustion at scale** (unbounded async tasks)
- ❌ **CRITICAL: DB connection pool depletion** (too many concurrent writes)
- ❌ **Order inversion** (newer timestamp overwritten by older)
- ❌ **No backpressure** (can't slow down when DB is overloaded)
- ❌ **Silent failures** (no retry, no alerting)
- ❌ **No reconciliation** (Redis-DB divergence undetected)

**Verdict:** **Unacceptable for production**. Would fail at 1,000+ concurrent users.

---

### Option 2: Batching + Debouncing (In-Memory Queue)

**Architecture:**
```java
// In-memory pending updates map
private final ConcurrentHashMap<UserChannelKey, String> pendingUpdates = new ConcurrentHashMap<>();

public void updateReadReceipt(Long userId, Long channelId, String timestamp) {
    redis.set(key, timestamp);  // Immediate
    pendingUpdates.put(new UserChannelKey(userId, channelId), timestamp);  // Buffer
}

@Scheduled(fixedDelay = 5000)  // Every 5 seconds
public void flushBatch() {
    Map<UserChannelKey, String> batch = pendingUpdates;
    pendingUpdates = new ConcurrentHashMap<>();  // Swap

    // Batch upsert
    String sql = """
        INSERT INTO read_receipts (user_id, channel_id, last_read_timestamp, updated_at)
        VALUES (?, ?, ?, NOW())
        ON CONFLICT (user_id, channel_id)
        DO UPDATE SET
            last_read_timestamp = GREATEST(
                read_receipts.last_read_timestamp,
                EXCLUDED.last_read_timestamp
            ),
            updated_at = NOW()
        WHERE EXCLUDED.last_read_timestamp >= read_receipts.last_read_timestamp
    """;

    jdbcTemplate.batchUpdate(sql, batch);
}
```

**Pros:**
- ✅ **Batching reduces DB load** (1000 updates → 1 transaction)
- ✅ **Debouncing**: Same user+channel only persists latest value
- ✅ **Order resolution**: `GREATEST()` ensures monotonic timestamps
- ✅ **Simple**: No new infrastructure (Kafka/queue)

**Cons:**
- ⚠️ **Data loss on server crash**: In-memory queue lost (last 5 seconds)
- ⚠️ **No horizontal scaling**: Single server's memory limit
- ⚠️ **Memory growth**: Pending map grows unbounded if DB is slow
- ⚠️ **No retry on failure**: Batch fails → all updates lost

**When acceptable:**
- Small to medium scale (< 10,000 concurrent users)
- Acceptable data loss (< 5 seconds worth)
- Single-region deployment

**Verdict:** **Good starting point**, but doesn't scale to Slack levels.

---

### Option 3: Kafka + Consumer Batching ⭐ **Chosen**

**Architecture (mirrors Slack's job queue):**

```
┌─────────────┐      ┌──────────────┐      ┌───────────┐      ┌─────────────┐
│ Web Server  │─────▶│  Kafkagate   │─────▶│   Kafka   │─────▶│ DB Consumer │
│ (REST API)  │      │ (Go service) │      │  Cluster  │      │ (Batching)  │
└─────────────┘      └──────────────┘      └───────────┘      └─────────────┘
       │                                                               │
       │ 1. Redis write (sync)                                        │
       │ 2. Kafka write (sync ack)                              3. Batch DB write
       │                                                         (configurable rate)
       ▼                                                               ▼
  ┌─────────┐                                                   ┌──────────┐
  │  Redis  │◀──────────────── Reconciliation Job ─────────────│   DB     │
  │ (Cache) │                  (detect divergence)             │ (Durable)│
  └─────────┘                                                   └──────────┘
```

**Components:**

1. **Web Server (Spring Boot)**:
   ```java
   @Transactional
   public void updateReadReceipt(Long userId, Long channelId, String timestamp) {
       // 1. Update Redis (fast, <1ms)
       redisTemplate.opsForValue().set(buildKey(userId, channelId), timestamp);

       // 2. Publish to Kafka (sync ack, ~10ms)
       ReadReceiptEvent event = new ReadReceiptEvent(userId, channelId, timestamp);
       kafkaTemplate.send("read-receipts", event).get();  // Wait for ack

       // 3. Broadcast WebSocket
       broadcastReadReceipt(userId, channelId, timestamp);
   }
   ```

2. **Kafka Cluster**:
   - **Topic**: `read-receipts`
   - **Partitions**: 32 (horizontal scaling)
   - **Replication**: 3x (durability)
   - **Retention**: 2 days (same as Slack)
   - **Key**: `userId:channelId` (deduplication + ordering within key)

3. **DB Consumer (Spring Boot)**:
   ```java
   @KafkaListener(
       topics = "read-receipts",
       concurrency = "8",  // 8 consumer threads
       containerFactory = "batchFactory"
   )
   public void consumeReadReceipts(List<ReadReceiptEvent> events) {
       // Deduplicate: latest timestamp per user+channel
       Map<UserChannelKey, String> deduplicated = events.stream()
           .collect(Collectors.toMap(
               e -> new UserChannelKey(e.getUserId(), e.getChannelId()),
               ReadReceiptEvent::getTimestamp,
               (t1, t2) -> t1.compareTo(t2) >= 0 ? t1 : t2  // Latest wins
           ));

       // Batch upsert with GREATEST() for order resolution
       batchUpsert(deduplicated);

       log.info("Persisted {} read receipts (deduped from {})",
                deduplicated.size(), events.size());
   }

   @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000))
   private void batchUpsert(Map<UserChannelKey, String> batch) {
       String sql = """
           INSERT INTO read_receipts (user_id, channel_id, last_read_timestamp, updated_at)
           VALUES (?, ?, ?, NOW())
           ON CONFLICT (user_id, channel_id)
           DO UPDATE SET
               last_read_timestamp = GREATEST(
                   read_receipts.last_read_timestamp,
                   EXCLUDED.last_read_timestamp
               ),
               updated_at = NOW()
       """;
       jdbcTemplate.batchUpdate(sql, batch.entrySet(), batch.size(),
           (ps, entry) -> {
               ps.setLong(1, entry.getKey().getUserId());
               ps.setLong(2, entry.getKey().getChannelId());
               ps.setString(3, entry.getValue());
           });
   }
   ```

4. **DLT Consumer** (for failed events):
   ```java
   @KafkaListener(topics = "read-receipts-dlt")
   public void handleFailedReadReceipt(ReadReceiptEvent event) {
       log.warn("Reconciling failed event from DLT: userId={}, channelId={}",
           event.getUserId(), event.getChannelId());

       try {
           // Get latest value from Redis (source of truth)
           String redisValue = redisTemplate.opsForValue()
               .get(buildKey(event.getUserId(), event.getChannelId()));

           if (redisValue == null) {
               // Redis evicted, use event value
               redisValue = event.getLastReadTimestamp();
           }

           // Upsert to DB with GREATEST() for order resolution
           String sql = """
               INSERT INTO read_receipts (user_id, channel_id, last_read_timestamp, updated_at)
               VALUES (?, ?, ?, NOW())
               ON CONFLICT (user_id, channel_id)
               DO UPDATE SET
                   last_read_timestamp = GREATEST(
                       read_receipts.last_read_timestamp,
                       EXCLUDED.last_read_timestamp
                   ),
                   updated_at = NOW()
           """;

           jdbcTemplate.update(sql, event.getUserId(), event.getChannelId(), redisValue);
           meterRegistry.counter("read_receipts.dlt.reconciled").increment();

       } catch (Exception e) {
           log.error("DLT reconciliation failed, needs manual intervention", e);
           meterRegistry.counter("read_receipts.dlt.failed").increment();
           alertService.critical("DLT reconciliation failed", e);
       }
   }
   ```

5. **Kafka Producer Fallback** (for producer failures):
   ```java
   private final Queue<ReadReceiptEvent> fallbackQueue = new ConcurrentLinkedQueue<>();

   private void publishToKafka(Long userId, Long channelId, String timestamp) {
       try {
           ReadReceiptEvent event = new ReadReceiptEvent(userId, channelId, timestamp);
           kafkaTemplate.send("read-receipts", event).get(100, TimeUnit.MILLISECONDS);
       } catch (Exception e) {
           log.error("Kafka publish failed, using fallback queue", e);
           fallbackQueue.offer(event);
           meterRegistry.counter("read_receipts.kafka.fallback").increment();
       }
   }

   @Scheduled(fixedDelay = 5000)  // Retry every 5 seconds
   public void retryFallbackQueue() {
       ReadReceiptEvent event;
       while ((event = fallbackQueue.poll()) != null) {
           try {
               kafkaTemplate.send("read-receipts", event).get();
               meterRegistry.counter("read_receipts.fallback.success").increment();
           } catch (Exception e) {
               fallbackQueue.offer(event);  // Re-queue for retry
               break;
           }
       }
   }
   ```

**Kafka Configuration:**
```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      acks: 1  # Leader ack (balance between latency and durability)
      retries: 3
      batch-size: 16384
      linger-ms: 10  # Wait up to 10ms to batch records
    consumer:
      group-id: read-receipt-persister
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      max-poll-records: 500  # Batch size
      properties:
        spring.json.trusted.packages: com.slack.dto.events
```

**Pros:**

- ✅ **Durability**: Kafka persists events (2-day retention, 3x replication)
- ✅ **Backpressure**: Consumer rate configurable (can't overwhelm DB)
- ✅ **Deduplication**: Latest timestamp per key wins
- ✅ **Order resolution**: `GREATEST()` prevents stale writes
- ✅ **Horizontal scaling**: Add more consumer threads/partitions
- ✅ **Retry + DLQ**: Failed batches retry, poison messages to DLQ
- ✅ **Observability**: Kafka lag metrics show backlog
- ✅ **Reconciliation**: Detects and fixes Redis-DB divergence
- ✅ **Proven at scale**: Slack uses identical architecture (33,000 jobs/sec)

**Cons:**

- ❌ **Infrastructure complexity**: Kafka cluster to manage
- ❌ **Operational overhead**: Monitoring, alerting, capacity planning
- ❌ **Latency increase**: ~10-50ms (Kafka write + consumer lag)
- ❌ **Learning curve**: Team must learn Kafka operations
- ⚠️ **Cost**: Kafka cluster resources (CPU, memory, storage)

**Trade-offs accepted:**

1. **10-50ms latency for durability**: Acceptable (read receipts are eventual, not real-time)
2. **Infrastructure complexity for scalability**: Necessary at Slack scale
3. **Operational overhead for reliability**: Worth it to prevent outages

---

### Option 4: Rate Limiting Only (No Batching)

**Architecture:**
```java
@Async
@RateLimiter(name = "readReceipt", fallbackMethod = "rateLimitFallback")
protected void persistToDatabase(...) {
    // Same as current, but rate limited
}

private void rateLimitFallback(...) {
    log.warn("Rate limited, skipping DB persist: user={}", userId);
    // Redis already updated, DB write skipped
}
```

**Configuration:**
```yaml
resilience4j.ratelimiter:
  instances:
    readReceipt:
      limitForPeriod: 1000  # Max 1000 DB writes per second
      timeoutDuration: 0s   # Don't wait, fail immediately
```

**Pros:**
- ✅ Simple (minimal code change)
- ✅ Prevents DB overload (max 1000 writes/sec)

**Cons:**
- ❌ **Data loss under load**: Rejected writes never persisted
- ❌ **No retry**: Failed writes lost forever
- ❌ **Doesn't solve order inversion**: Still @Async without coordination
- ❌ **No batching benefit**: Still 1000 individual transactions/sec

**Verdict:** **Insufficient**. Prevents catastrophic failure but loses data.

---

## Decision: Kafka + Consumer Batching (Option 3)

### Why Kafka-Based Approach?

**1. Proven at Slack Scale**

Slack's job queue handles **1.4 billion jobs/day** (33,000/sec peak) using this exact pattern:
- Kafka as durable buffer
- Consumer batching with configurable rate limits
- Decoupled enqueue/dequeue rates

Read receipts have similar characteristics (high-frequency, low-criticality, bursty).

**2. Durability Without Blocking**

- Redis write: <1ms (user sees immediate update)
- Kafka write: ~10ms (async to user experience)
- DB write: batched, no impact on user latency

**3. Horizontal Scaling**

Add more:
- Kafka partitions → higher throughput
- Consumer threads → faster DB writes
- Web servers → no coordination bottleneck

**4. Observability**

Kafka provides built-in metrics:
- Consumer lag (events waiting to be processed)
- Partition throughput
- Failure rate

**5. Failure Isolation**

- DB outage → Events accumulate in Kafka (2-day retention)
- Kafka outage → Redis still works (user experience unaffected, just no durability)
- Consumer crash → Kafka redelivers from last committed offset

### When to Use This Pattern

✅ **Use Kafka-based batching when:**
- Write throughput > 1,000 writes/second
- Order matters (need to prevent stale writes)
- Durability required (can't lose data on crash)
- Horizontal scaling needed (multi-server, multi-region)
- Decoupled rates needed (enqueue ≠ dequeue)

❌ **DON'T use Kafka when:**
- Low throughput (< 100 writes/second) → Option 2 (in-memory batching) is simpler
- Strong consistency required (must be in DB immediately) → Synchronous write
- Can't tolerate latency (10-50ms) → Probably not a real requirement for read receipts

### Implementation Approach

**Single-phase migration** (no dual-write needed for learning project):
1. Remove @Async persistence
2. Kafka becomes sole durability mechanism
3. DLT consumer handles failed events automatically
4. Fallback queue handles Kafka producer failures
5. Monitor metrics for system health

---

## Implementation Design

### Kafka Topic Schema

**Topic**: `read-receipts`

**Event Schema** (JSON):
```json
{
  "userId": 123,
  "channelId": 456,
  "lastReadTimestamp": "1735046400000001",
  "createdAt": "2025-12-26T10:30:00.123Z"
}
```

**Partitioning**: By `userId:channelId` (ensures order per user-channel pair)

**Retention**: 2 days (same as Slack's job queue)

### Consumer Batching Strategy

**Poll interval**: 1 second
**Batch size**: 500 records (configurable via `max-poll-records`)

**Deduplication logic:**
```
Batch of 500 events → 350 unique user-channel pairs (30% duplicates)
→ 350 DB writes instead of 500 (30% reduction)
```

**DB upsert with order resolution:**
```sql
INSERT INTO read_receipts (user_id, channel_id, last_read_timestamp, updated_at)
VALUES (?, ?, ?, NOW())
ON CONFLICT (user_id, channel_id)
DO UPDATE SET
    last_read_timestamp = GREATEST(
        read_receipts.last_read_timestamp,
        EXCLUDED.last_read_timestamp
    ),
    updated_at = NOW()
WHERE EXCLUDED.last_read_timestamp >= read_receipts.last_read_timestamp
```

**Why `GREATEST()`?**
- Prevents stale writes (older timestamp doesn't overwrite newer)
- Idempotent (same event replayed → same result)
- Monotonic (timestamps only increase)

### Failure Handling

**1. Kafka Write Failure**
```java
try {
    kafkaTemplate.send("read-receipts", event).get();  // Sync, throws on failure
} catch (Exception e) {
    log.error("Failed to publish to Kafka", e);
    // Redis already updated → user sees change
    // Fallback: Store in local queue for retry
    fallbackQueue.offer(event);
}
```

**2. Consumer Processing Failure**
```java
@RetryableTopic(
    attempts = "3",
    backoff = @Backoff(delay = 1000, multiplier = 2),
    dltTopicSuffix = "-dlt",
    include = {DataAccessException.class}
)
@KafkaListener(topics = "read-receipts")
public void consumeReadReceipts(List<ReadReceiptEvent> events) {
    batchUpsert(deduplicate(events));
}

@DltHandler
public void handleDlt(ReadReceiptEvent event) {
    log.error("Event failed after 3 retries, sending to DLT: {}", event);
    // Store in dead-letter topic for manual review
}
```

**3. DB Outage**
- Consumer stops committing offsets
- Kafka retains events (2-day retention)
- When DB recovers, consumer resumes from last committed offset
- No data loss

### Metrics to Track

**Performance Metrics:**
- `kafka.producer.record-send-rate`: Events/sec published
- `kafka.consumer.lag`: Events waiting to be processed
- `db.batch.size`: Average batch size
- `db.batch.duration`: Time to process batch

**Reliability Metrics:**
- `read_receipts.dlt.reconciled`: DLT events successfully recovered
- `read_receipts.dlt.failed`: DLT events that need manual intervention
- `read_receipts.kafka.fallback`: Producer failures using fallback queue
- `read_receipts.fallback.success`: Fallback queue retry successes
- `read_receipts.fallback.queue_size`: Current fallback queue depth

**Business Metrics:**
- `read_receipts.updates_per_second`: Read receipt update rate
- `read_receipts.dedup_ratio`: Percentage of duplicates filtered

---

## Consequences

### Positive

✅ **Eliminates unbounded async risk**: Kafka provides backpressure
✅ **Horizontal scalability**: Add partitions/consumers without coordination
✅ **Durability**: 2-day retention, 3x replication
✅ **Order resolution**: `GREATEST()` prevents stale writes
✅ **Batching efficiency**: 30-50% reduction in DB writes
✅ **Observability**: Kafka metrics show system health
✅ **Proven pattern**: Identical to Slack's production architecture
✅ **DLT reconciliation**: Event-driven recovery, no full table scans
✅ **Producer fallback**: Kafka failures don't lose data

### Negative

❌ **Infrastructure complexity**: Kafka cluster to manage
❌ **Operational overhead**: Monitoring, capacity planning, upgrades
❌ **Latency increase**: +10-50ms vs synchronous DB write
❌ **Learning curve**: Team must learn Kafka
❌ **Cost**: Kafka resources (3 brokers minimum for HA)

### Neutral (Trade-offs)

⚖️ **Consistency lag**: 1-5 seconds (acceptable for read receipts)
⚖️ **Infrastructure dependency**: Kafka outage → no durability (but Redis still works)
⚖️ **Complexity vs scalability**: Worth it at >10,000 concurrent users

---

## Comparison Table

| Approach | DB Writes/Sec | Data Loss Risk | Order Guarantee | Scalability | Complexity | Durability | Observability |
|----------|---------------|----------------|-----------------|-------------|------------|------------|---------------|
| **Option 1: Current** | Unbounded ❌ | Low ✅ | ❌ No | ❌ Poor | ✅ Low | Redis crash ⚠️ | ❌ None |
| **Option 2: Batching** | ~200 (batched) ✅ | 5 sec ⚠️ | ✅ Yes | ⚠️ Medium | ✅ Low | Server crash ⚠️ | ⚠️ Basic |
| **Option 3: Kafka** ⭐ | Configurable ✅ | ✅ None | ✅ Yes | ✅ Excellent | ❌ High | ✅ 2 days | ✅ Excellent |
| **Option 4: Rate Limit** | 1000 (limited) ⚠️ | High ❌ | ❌ No | ❌ Poor | ✅ Low | Rejected writes ❌ | ⚠️ Basic |

---

## Alternatives Considered: Why Not X?

### Why Not Redis Streams Instead of Kafka?

**Redis Streams** could replace Kafka:
```
XADD read_receipts * userId 123 channelId 456 timestamp 1000
```

**Rejected because:**
- ❌ **Memory limits**: Redis is in-memory (Slack hit this exact problem)
- ❌ **Durability**: Redis crash → stream lost (need AOF + persistence overhead)
- ❌ **Scalability**: Single Redis instance bottleneck
- ❌ **Not what Slack uses**: They explicitly moved from Redis to Kafka

### Why Not AWS SQS/SNS Instead of Kafka?

**Pros of SQS:**
- ✅ Fully managed (no Kafka ops)
- ✅ Auto-scaling

**Rejected because:**
- ❌ **No ordering guarantees**: FIFO queues have 300 TPS limit
- ❌ **No retention control**: Max 14 days, can't query old events
- ❌ **No compaction**: Can't deduplicate by key
- ❌ **Not what Slack uses**: Kafka is industry standard for event streaming

### Why Not Database Queue Table?

**Approach:**
```sql
CREATE TABLE read_receipt_queue (
    id SERIAL PRIMARY KEY,
    user_id BIGINT,
    channel_id BIGINT,
    timestamp TEXT,
    processed BOOLEAN DEFAULT FALSE
);
```

**Rejected because:**
- ❌ **Polling overhead**: `SELECT * WHERE processed = FALSE` is expensive
- ❌ **Lock contention**: Multiple consumers competing for rows
- ❌ **No horizontal scaling**: Single DB bottleneck
- ❌ **Slow**: DB is slowest component, using it as queue makes it slower

---

## Success Criteria

**v0.4.2 is successful if:**

- ✅ Kafka producer throughput: >10,000 events/sec
- ✅ Consumer lag: <5 seconds at P95
- ✅ DB batch efficiency: >30% deduplication rate
- ✅ Order correctness: Zero stale writes (older timestamp overwrites newer)
- ✅ Durability: Zero data loss on Redis/server crash
- ✅ DLT reconciliation: All failed events automatically recovered
- ✅ Fallback queue: Kafka producer failures handled gracefully

**Measured metrics:**

- Kafka consumer lag distribution (P50, P95, P99)
- DB batch size (avg, max)
- Deduplication ratio (duplicates / total events)
- DLT events processed per hour (should be near zero in steady state)
- Fallback queue size (should be zero when Kafka is healthy)
- Producer failure rate (<0.01% acceptable)

---

## Related Decisions

- **[ADR-0002: Eventual Consistency for Read Status](./0002-eventual-consistency-for-read-status.md)**: Established Redis-first, DB-backup pattern
- **[ADR-0001: Redis Pub/Sub vs Kafka](./0001-redis-vs-kafka-for-multi-server-broadcast.md)**: Chose Redis Pub/Sub for ephemeral WebSocket broadcast

**Relationship:**
- ADR-0001: Redis Pub/Sub for real-time broadcast (ephemeral, no durability)
- ADR-0002: Redis-first for read status (eventual consistency acceptable)
- **This ADR**: Kafka for durable persistence (batching, scalability)

---

## References

### Slack Engineering Blog

1. **[Scaling Slack's Job Queue](https://slack.engineering/scaling-slacks-job-queue/)**
   - Identical problem: High-frequency, low-criticality updates
   - Solution: Kafka as durable buffer, consumer batching
   - Scale: 1.4 billion jobs/day, 33,000/sec peak
   - **Key insight**: "Redis had little operational headroom... Kafka provides durable storage to buffer against memory exhaustion"

2. **[Making Slack Faster By Being Lazy](https://slack.engineering/making-slack-faster-by-being-lazy/)**
   - Unread count optimization: `users.counts` API
   - Lazy loading: Only load active channel messages
   - Result: 10% load time improvement (65% in extreme cases)

3. **[Flannel: Application-Level Edge Cache](https://slack.engineering/flannel-an-application-level-edge-cache-to-make-slack-scale/)**
   - Edge caching for user/channel data
   - Real-time event synchronization
   - 7x-44x payload reduction

### Industry Best Practices

4. **[Martin Kleppmann - Designing Data-Intensive Applications](https://dataintensive.net/)**
   - Chapter 11: Stream Processing
   - Kafka as event log, consumer batching patterns

5. **[Kafka: The Definitive Guide](https://www.confluent.io/resources/kafka-the-definitive-guide/)**
   - Consumer batching configuration
   - Exactly-once semantics (idempotency)

6. **[PostgreSQL UPSERT Documentation](https://www.postgresql.org/docs/current/sql-insert.html)**
   - `ON CONFLICT DO UPDATE` for idempotent writes

---

## Revision History

- **2025-12-26**: Initial proposal (before implementation)
- *TBD*: Update with actual Kafka throughput metrics after deployment
