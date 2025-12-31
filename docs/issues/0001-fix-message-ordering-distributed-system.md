# Issue #001: Fix Message Ordering in Distributed Systems

**Status**: Open
**Priority**: High
**Created**: 2025-12-31
**Related ADR**: [ADR-0008](../adr/0008-message-ordering-in-distributed-systems.md)

---

## Problem Summary

Current `MessageTimestampGenerator` implementation has critical flaws that cause:
1. **ID collisions** when multiple servers handle the same channel
2. **Incorrect message ordering** due to clock skew and network latency
3. **System.nanoTime() misuse** (machine-specific, not wall clock time)

### Example Issue

```
User A sends: "5시에 봅시다" at 10:00:00.100
User B sends: "네" at 10:00:00.200 (100ms later)

Current behavior:
  → Messages can appear reversed due to network latency + server clock differences

Expected behavior:
  → Messages should appear in chronological order (or very close to it)
```

---

## Root Cause Analysis

### 1. Wrong Time Source

```java
// Current (BROKEN)
private long getMicroseconds() {
    return System.nanoTime() / 1000;  // ❌ Machine-specific!
}
```

**Problems:**
- `System.nanoTime()` is relative time (elapsed since JVM start)
- Each server has different zero point
- Cannot compare timestamps across servers
- Not suitable for distributed ID generation

### 2. Missing Worker ID

```
Snowflake structure: [Timestamp 41bit][Worker ID 10bit][Sequence 12bit]
Current structure:    [Timestamp 64bit][Sequence 12bit]  ← No Worker ID!
```

**Impact:**
- Two servers can generate identical IDs
- Database unique constraint violations possible
- No way to trace which server generated which message

### 3. Distributed System Architecture

```
Channel #general can receive messages from:
  ├─ Server 1 (Tokyo)
  ├─ Server 2 (Seoul)
  └─ Server 3 (Singapore)

Each server independently generates IDs
  → No coordination = collision risk
```

---

## Proposed Solutions

### Option A: Snowflake ID with Worker ID ⭐ (Recommended)

**Implementation:**
```java
@Service
public class SnowflakeIdGenerator {
    private final long workerId;
    private final long epoch = 1640995200000L;

    public SnowflakeIdGenerator(@Value("${server.worker-id}") long workerId) {
        this.workerId = workerId;
    }

    public synchronized long generateId() {
        long timestamp = System.currentTimeMillis(); // ← Use wall clock!

        return ((timestamp - epoch) << 22)  // 41 bits
             | (workerId << 12)             // 10 bits (0-1023)
             | sequence;                    // 12 bits (0-4095)
    }
}
```

**Pros:**
- ✅ Globally unique (worker ID prevents collisions)
- ✅ Chronologically sortable
- ✅ Proven at scale (Twitter, Discord, Slack)
- ✅ No coordination needed at runtime

**Cons:**
- ❌ Requires worker ID configuration per server
- ❌ Clock skew can still affect ordering (~0.01% cases)
- ❌ Max 1024 workers (acceptable for most systems)

**Trade-offs:**
- Perfect ordering → Eventual ordering (~99.99% accurate)
- Coordination → Configuration (worker IDs)

---

### Option B: Kafka Partition-Based Ordering

**Architecture:**
```
Client → Server → DB → Kafka (partition by channelId) → Consumer → WebSocket
```

**How it works:**
```java
// All messages for same channel go to same partition
producer.send(new ProducerRecord<>(
    "messages",
    channelId.toString(),  // partition key
    message
));

// Kafka guarantees ordering within partition
```

**Pros:**
- ✅ **Perfect ordering guarantee** (100%)
- ✅ At-least-once delivery
- ✅ Message replay capability
- ✅ No clock skew issues

**Cons:**
- ❌ Higher latency (10-50ms Kafka + outbox polling)
- ❌ Infrastructure complexity (Kafka cluster)
- ❌ Partition count limits scalability
- ❌ Over-engineered for current scale

**When to use:**
- Event-driven architecture
- Ordering is absolutely critical (financial, audit logs)
- Already using Kafka for other purposes

---

### Option C: Hybrid Approach

**Combine Snowflake + Channel Partitioning:**

```java
@Service
public class MessageRoutingService {
    // Hot channels → dedicated server (perfect ordering)
    // Normal channels → any server with Snowflake ID

    public int getServerForChannel(Long channelId) {
        if (isHotChannel(channelId)) {
            return channelId.hashCode() % totalServers; // Partition
        }
        return -1; // Any server can handle
    }
}
```

**Benefits:**
- ✅ Best of both worlds
- ✅ Perfect ordering for important channels
- ✅ Scalability for normal channels
- ❌ More complex logic

---

## Recommended Approach

**Start with Option A (Snowflake ID)**, because:

1. **Simpler migration path**
   - Replace MessageTimestampGenerator
   - Add worker-id configuration
   - Update database schema

2. **Matches production systems**
   - Slack, Discord use similar approach
   - Proven at billions of messages/day

3. **Good enough ordering**
   - ~99.99% chronological accuracy
   - Rare reorderings are minor (bounded by network latency)

4. **Learning value**
   - Understand distributed ID generation
   - Experience CAP theorem trade-offs
   - Learn about clock synchronization (NTP)

**Future upgrade to Option B (Kafka)** when:
- Need perfect ordering guarantees
- Implementing event sourcing
- Message replay becomes requirement

---

## Implementation Plan

### Phase 1: Fix ID Generator (Week 1)

**Tasks:**
- [ ] Implement SnowflakeIdGenerator with worker ID
- [ ] Add configuration: `server.worker-id` in application.yml
- [ ] Update database schema: add `snowflake_id` column
- [ ] Add unique constraint on snowflake_id
- [ ] Write unit tests (collision detection, monotonicity)

**Files to modify:**
```
backend/src/main/java/com/slack/message/service/
  ├─ SnowflakeIdGenerator.java (NEW)
  └─ MessageTimestampGenerator.java (DEPRECATE)

backend/src/main/resources/
  └─ application.yml (add server.worker-id)

backend/src/main/resources/db/migration/
  └─ V8__add_snowflake_id_to_messages.sql (NEW)
```

### Phase 2: Dual ID System (Week 2)

**Run both systems in parallel:**
```java
Message message = Message.builder()
    .timestampId(oldGenerator.generateTimestampId())  // Keep for safety
    .snowflakeId(snowflakeGenerator.generateId())     // New system
    .build();
```

**Tasks:**
- [ ] Update Message entity
- [ ] Update MessageService to generate both IDs
- [ ] Add monitoring: track ID generation metrics
- [ ] Add alerts: detect collisions or clock skew

### Phase 3: Client Migration (Week 3-4)

**Update clients to use snowflake_id:**

```typescript
// Sort by snowflake ID instead of timestamp_id
messages.sort((a, b) => a.snowflakeId - b.snowflakeId);

// Add 2-second buffer for late messages
setTimeout(() => {
  const sorted = buffer.sort(...);
  displayMessages(sorted);
}, 2000);
```

**Tasks:**
- [ ] Update WebSocket message format
- [ ] Implement client-side time buffer
- [ ] Add deduplication logic
- [ ] Update reconnection logic

### Phase 4: Cleanup (Week 5)

**Remove old system:**
- [ ] Drop timestamp_id column
- [ ] Remove MessageTimestampGenerator
- [ ] Update documentation
- [ ] Verify metrics (out-of-order rate < 1%)

---

## Success Criteria

### Performance Metrics

| Metric | Target | Measurement |
|--------|--------|-------------|
| ID generation latency | < 0.1ms (p99) | Prometheus histogram |
| Out-of-order messages | < 1% | Client-side detection |
| ID collisions | 0 | Database constraint violations |
| Clock skew | < 100ms | Server time comparison |

### Testing Requirements

**Unit Tests:**
```java
@Test
void generateId_noCollisions_across1000Workers() {
    Set<Long> ids = new HashSet<>();
    for (int workerId = 0; workerId < 1000; workerId++) {
        SnowflakeIdGenerator gen = new SnowflakeIdGenerator(workerId);
        ids.add(gen.generateId());
    }
    assertEquals(1000, ids.size()); // All unique
}

@Test
void generateId_monotonicallyIncreasing() {
    SnowflakeIdGenerator gen = new SnowflakeIdGenerator(0);
    long prev = gen.generateId();
    for (int i = 0; i < 10000; i++) {
        long current = gen.generateId();
        assertTrue(current > prev);
        prev = current;
    }
}
```

**Integration Tests:**
```java
@Test
void multipleServers_sameChannel_noCollisions() {
    // Simulate 3 servers handling same channel
    ExecutorService executor = Executors.newFixedThreadPool(3);

    List<Future<Long>> futures = new ArrayList<>();
    for (int workerId = 0; workerId < 3; workerId++) {
        futures.add(executor.submit(() -> {
            SnowflakeIdGenerator gen = new SnowflakeIdGenerator(workerId);
            return messageService.createMessage(gen.generateId(), ...);
        }));
    }

    Set<Long> ids = futures.stream()
        .map(f -> f.get())
        .collect(Collectors.toSet());

    assertEquals(3, ids.size()); // No collisions
}
```

**Load Tests:**
```bash
# Generate 1M messages across 10 workers
# Verify: 0 collisions, < 1% out-of-order
./gradlew loadTest --workers=10 --messages=1000000
```

---

## Monitoring & Alerts

### Metrics to Track

```java
@Component
public class SnowflakeMetrics {
    private final MeterRegistry registry;

    public void recordIdGeneration(long latencyNanos) {
        registry.timer("snowflake.generation.latency").record(latencyNanos, NANOSECONDS);
    }

    public void recordClockSkew(long skewMs) {
        registry.gauge("snowflake.clock_skew_ms", skewMs);
    }

    public void recordOutOfOrderMessage(long delta) {
        registry.counter("messages.out_of_order",
            "delta_ms", String.valueOf(delta)
        ).increment();
    }
}
```

### Alert Rules

```yaml
alerts:
  - name: HighClockSkew
    condition: snowflake.clock_skew_ms > 1000
    action: Alert ops team (NTP issue)

  - name: HighOutOfOrderRate
    condition: rate(messages.out_of_order[5m]) > 0.01
    action: Investigate network issues

  - name: IdCollision
    condition: increase(postgres.unique_violation[5m]) > 0
    action: Critical - check worker ID configuration
```

---

## Risks & Mitigations

### Risk 1: Worker ID Conflicts

**Scenario:** Two servers accidentally use same worker ID

**Impact:** ID collisions, database errors

**Mitigation:**
```yaml
# Use environment variable, fail fast on startup
server:
  worker-id: ${WORKER_ID:?WORKER_ID must be set}

# Health check verifies unique worker ID
@Component
public class WorkerIdHealthCheck {
    @PostConstruct
    public void verifyUniqueWorkerId() {
        Long existingWorker = redis.get("worker:" + workerId);
        if (existingWorker != null) {
            throw new IllegalStateException("Worker ID " + workerId + " already in use!");
        }
        redis.set("worker:" + workerId, hostName);
    }
}
```

### Risk 2: Clock Drift

**Scenario:** Server clock drifts significantly from NTP

**Impact:** Messages timestamped in the past/future

**Mitigation:**
- Configure NTP on all servers
- Monitor clock skew (alert if > 1s)
- Use Hybrid Logical Clock for critical channels

### Risk 3: Worker ID Exhaustion

**Scenario:** Need more than 1024 servers

**Impact:** Cannot assign unique worker IDs

**Mitigation:**
- 10 bits = 1024 workers is sufficient for learning project
- Production: Use datacenter ID + worker ID scheme
  - 5 bits datacenter (32 DCs) + 5 bits worker (32 per DC) = 1024 total

---

## Questions for Discussion

1. **Worker ID assignment**: Manual (config file) vs Automatic (Zookeeper)?
2. **Clock skew tolerance**: Alert at 100ms or 1s?
3. **Migration strategy**: Big bang vs gradual rollout?
4. **Kafka evaluation**: Should we prototype both solutions?

---

## References

- [ADR-0008: Message Ordering in Distributed Systems](../adr/0008-message-ordering-in-distributed-systems.md)
- [Twitter Snowflake](https://github.com/twitter-archive/snowflake)
- [Discord: How Discord Stores Billions of Messages](https://discord.com/blog/how-discord-stores-billions-of-messages)
- [Slack Engineering: Real-time Messaging](https://slack.engineering/real-time-messaging/)

---

## Related Files

```
Current implementation:
  backend/src/main/java/com/slack/message/service/MessageTimestampGenerator.java

Files to create:
  backend/src/main/java/com/slack/message/service/SnowflakeIdGenerator.java
  backend/src/main/resources/db/migration/V8__add_snowflake_id.sql
  backend/src/test/java/com/slack/message/service/SnowflakeIdGeneratorTest.java

Configuration:
  backend/src/main/resources/application.yml (add server.worker-id)
```

---

## Next Steps

1. **Review ADR-0008** - Understand distributed systems trade-offs
2. **Prototype Snowflake generator** - Implement and test locally
3. **Benchmark performance** - Measure ID generation latency
4. **Design migration** - Plan zero-downtime migration path
5. **Consider Kafka** - Evaluate if perfect ordering is required

---

**Issue Owner**: TBD
**Target Completion**: Q1 2025
**Estimated Effort**: 3-4 weeks
