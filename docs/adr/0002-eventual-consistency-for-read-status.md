# ADR-0002: Eventual Consistency for Read Status

- **Status**: Accepted ✅
- **Date**: 2025-12-13
- **Deciders**: @1031nice
- **Context**: v0.4 - Read Status & Notifications
- **Related**: [ADR-0001: Redis Pub/Sub vs Kafka](./0001-redis-vs-kafka-for-multi-server-broadcast.md)

---

## Problem Statement

Unread count and read receipts require high-frequency updates (every time a user reads a channel or receives a message). Need fast read/write performance without overloading the database, while maintaining durability for multi-device sync and crash recovery.

## Context

- **Update frequency**: 10,000+ operations/sec (users reading channels, messages arriving)
- **Write pattern**: Very frequent (every message → increment unread, every channel view → reset to 0)
- **Read pattern**: Every channel list load, every UI refresh
- **Consistency requirement**: Not critical (slight lag is acceptable, unlike message content)
- **Multi-device requirement**: Unread count must sync across devices (phone, desktop, web)
- **Durability requirement**: Must survive Redis crash (restore from PostgreSQL)

## Real-World Observation

**Actual Slack behavior** (based on usage):
- Reading a message on phone → Desktop shows updated count after ~5-10 seconds
- App restart → Unread count restored correctly
- New device login → Unread count synced from server

**Conclusion**: Slack uses Redis + DB with Eventual Consistency

---

## Proposed Solutions

### Option 1: Redis Only

**Architecture:**
```
Write: Redis INCR/SET
Read: Redis GET
Persistence: ❌ None
Recovery: ❌ None
```

**Pros:**
- ✅ Fastest (sub-millisecond)
- ✅ Simplest implementation
- ✅ No DB write load

**Cons:**
- ❌ **Data loss on Redis crash**: All unread counts lost
- ❌ **No multi-device sync**: New device login → no unread count
- ❌ **No recovery**: Server restart → all users see 0 unread
- ❌ **No analytics**: Cannot track unread message trends

**When acceptable:**
- Single-device apps (desktop-only)
- Non-critical counters (temporary state)
- Cache-only data (can be regenerated)

---

### Option 2: DB Only

**Architecture:**
```
Write: PostgreSQL UPDATE read_receipts SET unread_count = unread_count + 1
Read: PostgreSQL SELECT unread_count FROM read_receipts
Persistence: ✅ Always durable
```

**Pros:**
- ✅ Always durable
- ✅ Multi-device sync (single source of truth)
- ✅ Simple consistency model (no sync needed)

**Cons:**
- ❌ **Performance bottleneck**: 10,000+ DB writes/sec → overload
- ❌ **High latency**: 10-50ms per update (vs sub-1ms Redis)
- ❌ **Lock contention**: Popular channels → multiple concurrent updates → locking
- ❌ **Expensive at scale**: DB write costs 100x more than Redis write

**Calculation:**
- 10,000 users × 10 channels/user × 1 message/min = ~1,667 updates/sec
- DB write cost: 10ms × 1,667 = 16,670ms/sec (overload)
- Redis write cost: 0.1ms × 1,667 = 167ms/sec (acceptable)

---

### Option 3: Redis + DB (Eventual Consistency) ⭐ **Chosen**

**Architecture:**
```
Write path:
  1. Client reads channel
  2. Redis ZADD/INCR (immediate, <1ms)
  3. Return success to client
  4. Background job: Batch write to PostgreSQL every 5-10 seconds

Read path:
  1. Redis GET (cache hit, <1ms)
  2. If cache miss → PostgreSQL SELECT
  3. Populate Redis cache

Sync:
  - Scheduled task: Redis → PostgreSQL every 5-10 seconds
  - Batch update: 100-1000 records per transaction

Recovery (Redis crash):
  - PostgreSQL → Redis (restore from last sync)
  - Data loss: <10 seconds worth of updates
```

**Pros:**
- ✅ **Fast writes**: Sub-millisecond (Redis)
- ✅ **Fast reads**: Sub-millisecond (Redis cache)
- ✅ **Durable**: PostgreSQL backup
- ✅ **Multi-device sync**: All devices read from same Redis/DB
- ✅ **Reduced DB load**: Batched writes (100x reduction)
- ✅ **Crash recovery**: Restore from PostgreSQL

**Cons:**
- ⚠️ **Consistency lag**: <10 seconds (target P95)
  - Example: Read on phone → Desktop shows unread for 5 seconds
  - **Why acceptable**: Non-critical data, users tolerate slight lag
- ⚠️ **Complexity**: Two systems to manage (Redis + PostgreSQL)
- ⚠️ **Data loss risk**: Redis crash → <10 sec data loss

**Trade-offs accepted:**
1. **<10 sec consistency lag**: Acceptable for non-critical data (not message content)
2. **Increased complexity**: Performance gain (100x) justifies it
3. **Potential data loss**: Rare event (Redis crash), <10 sec loss acceptable

---

### Option 4: Redis + DB (Strong Consistency)

**Architecture:**
```
Write path (distributed transaction):
  1. Begin transaction
  2. Write to Redis
  3. Write to PostgreSQL
  4. Commit both (2PC)
  5. Return success
```

**Pros:**
- ✅ Immediate consistency (no lag)
- ✅ No data loss

**Cons:**
- ❌ **High latency**: Redis write + DB write + 2PC overhead (20-100ms)
- ❌ **Complexity**: Distributed transaction coordination
- ❌ **Availability risk**: Both systems must be up (Redis down → write fails)
- ❌ **Overkill**: Strong consistency not needed for unread count

**Why not chosen:**
- Unread count is **not critical data** (unlike message content)
- 10x latency increase for marginal benefit
- Distributed transactions add significant complexity

---

## Decision: Redis + DB (Eventual Consistency)

### Why Eventual Consistency is Acceptable

**1. Non-critical data:**
| Data Type | Consistency Required |
|-----------|---------------------|
| Message content | Strong (DB-first, ADR-0001) |
| Unread count | Eventual (acceptable lag) |
| Read receipts | Eventual (acceptable lag) |

- Message loss: **Unacceptable** → Strong consistency
- Unread count lag: **Annoying but OK** → Eventual consistency

**2. Real-world validation:**
- **Slack**: Slight lag on multi-device sync (~5-10 sec)
- **Discord**: Similar behavior
- **WhatsApp**: Unread count syncs with delay
- **User tolerance**: <10 sec lag is acceptable for non-critical data

**3. Performance gain:**
- Redis: 10,000+ ops/sec
- PostgreSQL: 100 ops/sec (with acceptable latency)
- **100x improvement** in throughput

---

## Consistency Guarantees

### What we guarantee:
- ✅ **Eventually consistent**: All replicas converge within <10 sec (target P95)
- ✅ **Read-your-own-writes**: Same device sees own updates immediately (Redis cache)
- ✅ **Monotonic reads**: Never see older value after newer value (Redis sequence)
- ✅ **Bounded staleness**: Maximum lag = sync interval (10 sec)

### What we DON'T guarantee:
- ❌ **Immediate consistency**: Multi-device sync has <10 sec lag
- ❌ **Zero data loss**: Redis crash → last <10 sec lost

---

## Implementation Design

### Redis Data Structures

**Unread Count** (per user per channel):
```
Key: unread:{userId}:{channelId}
Type: Sorted Set
Members: messageIds
Scores: timestamps
Commands:
  - ZADD unread:123:456 1702456789 "msg-789"  # Add unread message
  - ZCARD unread:123:456                       # Get count
  - ZREM unread:123:456 "msg-789"              # Mark as read
  - DEL unread:123:456                         # Clear all unread
```

**Read Receipts** (last read position):
```
Key: read_receipt:{userId}:{channelId}
Type: Hash
Fields:
  - lastReadSequence: 42
  - lastReadAt: "2025-12-13T10:30:00Z"
Commands:
  - HSET read_receipt:123:456 lastReadSequence 42
  - HGET read_receipt:123:456 lastReadSequence
```

### PostgreSQL Schema

```sql
CREATE TABLE read_receipts (
    user_id BIGINT NOT NULL,
    channel_id BIGINT NOT NULL,
    last_read_sequence BIGINT NOT NULL,
    unread_count INT NOT NULL DEFAULT 0,
    last_read_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, channel_id),
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (channel_id) REFERENCES channels(id)
);

CREATE INDEX idx_read_receipts_user_id ON read_receipts(user_id);
CREATE INDEX idx_read_receipts_updated_at ON read_receipts(updated_at);
```

### Sync Strategy

**Scheduled Job** (every 5-10 seconds):
```java
@Scheduled(fixedDelay = 10000) // 10 seconds
public void syncReadStatusToDatabase() {
    // 1. Scan Redis for updated read status (since last sync)
    // 2. Batch read from Redis (100-1000 records)
    // 3. Batch write to PostgreSQL (single transaction)
    // 4. Track consistency lag metric
    // 5. Handle failures (exponential backoff, alerting)
}
```

**Batch Update:**
- Batch size: 100-1000 records per transaction
- Trade-off: Larger batch → lower DB load, higher latency

**Failure Handling:**
- Retry with exponential backoff (1s, 2s, 4s, 8s, 16s)
- Alert after 3 consecutive failures
- Track failed syncs in separate Redis set for retry

---

## Failure Modes and Recovery

### Scenario 1: Redis Down

**Behavior:**
- Write fails (return error to client)
- Read falls back to PostgreSQL (slower, but works)

**Recovery:**
- Redis restarts → Populate from PostgreSQL (warm-up)
- Sync job resumes

### Scenario 2: PostgreSQL Down

**Behavior:**
- Write to Redis succeeds (user sees update)
- Sync job fails (queued for retry)

**Recovery:**
- PostgreSQL restarts → Sync job catches up (replay queued updates)

### Scenario 3: Sync Job Failure

**Behavior:**
- Redis continues to accept writes
- PostgreSQL becomes stale (>10 sec lag)

**Recovery:**
- Exponential backoff retry (1s, 2s, 4s, 8s, 16s)
- Alert on-call engineer after 3 failures
- Manual intervention if needed

### Scenario 4: Redis Crash (Data Loss)

**Behavior:**
- All in-memory data lost
- Last sync to PostgreSQL was 10 sec ago
- **Data loss**: Last 10 seconds of updates

**Recovery:**
1. Redis restarts (empty)
2. Restore from PostgreSQL:
   ```java
   public void restoreReadStatusFromDatabase() {
       List<ReadReceipt> allReceipts = readReceiptRepository.findAll();
       for (ReadReceipt receipt : allReceipts) {
           redis.set("unread:" + receipt.getUserId() + ":" + receipt.getChannelId(),
                     receipt.getUnreadCount());
       }
   }
   ```
3. Service resumes normal operation

**Mitigation:**
- Redis persistence (RDB snapshots, AOF logs)
- Reduce sync interval (5 sec instead of 10 sec) → less data loss

---

## Metrics to Track

### Performance Metrics
- **Write latency (Redis)**: Target <1ms P95
- **Read latency (Redis)**: Target <1ms P95
- **Sync latency**: Time to sync 1000 records (target <1sec)

### Consistency Metrics
- **Consistency lag**: Time between Redis write and DB sync
  - Target: <10s P95, <5s P50
- **Sync success rate**: Percentage of successful syncs (target >99.9%)
- **Data loss (on crash)**: Number of updates lost (target <10 sec worth)

### Business Metrics
- **Unread count accuracy**: Compare Redis vs DB (should converge)
- **Multi-device sync time**: Phone update → Desktop sees it (target <10s)

---

## Implementation Phases

### Phase 1: Redis-only (`feature/unread-count`, `feature/read-receipts`)
**Goal**: Prove the feature works, measure performance

**Scope:**
- Unread count: Redis Sorted Set
- Read receipts: Redis Hash
- WebSocket broadcast

**Deliverable**: Working feature with Redis, no DB sync yet

### Phase 2: DB sync (`feature/consistency-sync`)
**Goal**: Add durability and multi-device sync

**Scope:**
- PostgreSQL schema (read_receipts table)
- Scheduled sync job (every 10 sec)
- Crash recovery (DB → Redis)

**Deliverable**: Eventual consistency with <10 sec lag

### Phase 3: Monitoring and optimization
**Goal**: Measure and improve

**Scope:**
- Consistency lag metrics
- Sync failure alerts
- Optimize sync interval (5s vs 10s vs 30s)
- Optimize batch size

**Deliverable**: Production-ready with observability

---

## Alternative Considered: Outbox Pattern

**Why not Outbox + Kafka** (like we considered for messages in ADR-0001)?

**Outbox Pattern:**
```
1. Write to PostgreSQL (read_receipts table)
2. Write to Outbox table (transactional)
3. Background worker polls Outbox
4. Publish to Kafka
5. Consumer updates Redis
```

**Why not chosen:**
- ❌ **Latency**: 1+ sec (DB write + polling + Kafka + consume)
- ❌ **Complexity**: 5 steps vs 2 steps (Redis write + sync)
- ❌ **Overkill**: High-frequency updates (10,000+ ops/sec) don't need guaranteed delivery
- ❌ **Wrong direction**: We want fast writes (Redis) → slow persistence (DB), not the reverse

**When Outbox makes sense:**
- Critical events (payment processed, order placed)
- Cross-service communication (need event log)
- Audit trail required

**Unread count is different:**
- High frequency, low criticality
- Redis-first (fast) + DB backup (durable) is the right pattern

---

## Comparison Table

| Approach | Write Latency | Read Latency | Consistency | Multi-device | Durability | DB Load | Complexity |
|----------|---------------|--------------|-------------|--------------|------------|---------|------------|
| **Redis only** | <1ms ✅ | <1ms ✅ | N/A | ❌ | ❌ | None ✅ | Low ✅ |
| **DB only** | 10-50ms ❌ | 10-50ms ❌ | Strong ✅ | ✅ | ✅ | High ❌ | Low ✅ |
| **Redis+DB (Eventual)** ⭐ | <1ms ✅ | <1ms ✅ | <10s lag ⚠️ | ✅ | ✅ | Low ✅ | Medium ⚠️ |
| **Redis+DB (Strong)** | 20-100ms ❌ | <1ms ✅ | Strong ✅ | ✅ | ✅ | High ❌ | High ❌ |
| **Outbox + Kafka** | 1+ sec ❌ | <1ms ✅ | Eventual ⚠️ | ✅ | ✅ | Medium ⚠️ | Very High ❌ |

---

## Open Questions

1. **Sync interval**: 5 sec vs 10 sec vs 30 sec?
   - Shorter interval → Less data loss, higher DB load
   - Answer: Start with 10 sec, measure consistency lag, adjust if needed

2. **Batch size**: 100 vs 1000 records per sync?
   - Larger batch → Lower DB load, higher latency
   - Answer: Start with 1000, measure sync latency

3. **Redis persistence**: RDB vs AOF vs both?
   - RDB: Fast, less durable (snapshots every N seconds)
   - AOF: Slower, more durable (log every write)
   - Answer: AOF + fsync every second (balance speed and durability)

4. **Conflict resolution**: What if Redis and DB diverge?
   - Redis crash → DB restores Redis (DB wins)
   - Sync failure → Redis is newer (Redis wins)
   - Answer: **Last-write-wins** (timestamp-based)

---

## Success Criteria

**v0.4 is successful if:**
- ✅ Unread count updates in <1ms P95 (Redis write)
- ✅ Unread count reads in <1ms P95 (Redis cache)
- ✅ Consistency lag <10s P95 (Redis → DB sync)
- ✅ Multi-device sync works (phone → desktop)
- ✅ Crash recovery works (Redis restart → restore from DB)
- ✅ No user-visible errors (99.9%+ sync success)

**Measured in v0.4:**
- Consistency lag distribution (P50, P95, P99)
- Sync success rate
- Redis vs DB accuracy (should converge)

---

## References

1. [Slack Engineering: Real-time Messaging](https://slack.engineering/real-time-messaging/)
   - Slack's approach to real-time data and caching

2. [Martin Fowler: Patterns for Managing Data in Microservices](https://martinfowler.com/articles/patterns-of-distributed-systems/eventual-consistency.html)
   - Eventual consistency patterns

3. [Redis Persistence](https://redis.io/docs/management/persistence/)
   - RDB vs AOF trade-offs

4. [PostgreSQL Batch Updates](https://www.postgresql.org/docs/current/sql-insert.html)
   - ON CONFLICT DO UPDATE for upserts

5. [Designing Data-Intensive Applications](https://dataintensive.net/) - Chapter 5: Replication
   - Consistency models and trade-offs

---

## Revision History

- **2025-12-13**: Initial version (before v0.4 implementation)
- *TBD*: Update with actual measured consistency lag after v0.4 completion
