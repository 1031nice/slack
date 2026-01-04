# ADR-0008: Message Ordering in Distributed Chat Systems

**Status**: Accepted
**Date**: 2025-12-31
**Updated**: 2026-01-04
**Related**: [ADR-0001](./0001-redis-vs-kafka-for-multi-server-broadcast.md), [ADR-0006](./0006-event-based-architecture-for-distributed-messaging.md)
**Supersedes**: Current `MessageTimestampGenerator` implementation

---

## Problem Statement

**The root cause of message ordering issues**: Our current architecture allows **multiple servers to handle messages for the same channel**, causing ordering problems.

### The Concrete Example

```
Real-world timeline:
10:00:00.100 - User A sends: "Let's meet at 5pm"
10:00:00.200 - User B sends: "Sounds good" (100ms later)

User A's message:
  Client → (200ms network delay) → Server 1 (Tokyo)
  Arrives at 10:00:00.300
  Server 1 generates timestamp: 10:00:00.300

User B's message:
  Client → (50ms network delay) → Server 2 (Seoul)
  Arrives at 10:00:00.250
  Server 2 generates timestamp: 10:00:00.250

Result when sorted by timestamp:
  1. [10:00:00.250] "Sounds good"      ← sent SECOND
  2. [10:00:00.300] "Let's meet at 5pm" ← sent FIRST

Messages appear in WRONG order!
```

**This is NOT a fundamental distributed systems problem—it's an architectural choice problem.**

Real Slack solves this by ensuring **one channel = one dedicated server** (Channel Server with consistent hashing). This eliminates ordering issues entirely.

---

## Context

### Current Architecture Problem

Our current load balancer distributes requests randomly:

```
Current (BROKEN):
Channel #general messages → Nginx (ip_hash) → Random Server (1, 2, or 3)
  → Each server generates timestamp independently
  → Clock skew + network latency causes ordering conflicts

User A → Server 1 → timestamp=10:00:00.300
User B → Server 2 → timestamp=10:00:00.250 (100ms earlier!)
  → Messages appear in wrong order
```

### Why Current `MessageTimestampGenerator` Fails

```java
@Service
public class MessageTimestampGenerator {
    private long lastMicroseconds = -1L;
    private int sequence = 0;

    public synchronized String generateTimestampId() {
        long currentMicroseconds = getMicroseconds();
        // ...
    }

    private long getMicroseconds() {
        return System.nanoTime() / 1000;  // ❌ PROBLEM!
    }
}
```

**Three critical flaws:**

1. **`System.nanoTime()` is NOT a wall clock**
   - Measures elapsed time since JVM start
   - Each server has different zero point
   - Cannot compare across servers

2. **Multiple servers can handle same channel**
   - Load balancer routes to any server
   - Each generates timestamp independently
   - Ordering conflicts guaranteed

3. **Clock skew amplifies the problem**
   - Even with `System.currentTimeMillis()`
   - Servers with fast/slow clocks create incorrect order

### The Real Problem

**Our architecture allows multiple servers to handle the same channel:**

```
Current (BROKEN):
Channel #general messages → Load Balancer (random) → Any Server (1, 2, or 3)
  → Each server generates timestamp independently
  → Ordering conflicts

Slack Way (CORRECT):
Channel #general messages → Consistent Hash → Always Server 2
  → Single server handles all messages for that channel
  → Perfect ordering guaranteed
```

**This is an architecture problem, not a distributed systems limitation.**

---

## How Real Slack Solves This

### Slack's Approach: Channel Servers with Consistent Hashing

According to [Slack's engineering blog](https://slack.engineering/real-time-messaging/), Slack uses **Channel Servers** mapped to channels via **consistent hashing**:

> "Channel Servers are stateful and in-memory, holding channel history, with every CS **mapped to a subset of channels based on consistent hashing**"
> — [Real-Time Messaging Architecture at Slack (InfoQ)](https://www.infoq.com/news/2023/04/real-time-messaging-slack/)

> "A 'channel' ID is **hashed and mapped to a unique server**"
> — [Slack Architecture - System Design](https://systemdesign.one/slack-architecture/)

> "The channel server **arbitrated message order** - when two users hit send simultaneously, the channel server decided which message came first"
> — [How Slack Supports Billions of Daily Messages (ByteByteGo)](https://blog.bytebytego.com/p/how-slack-supports-billions-of-daily)

> "All messages within a single channel are **guaranteed to have a unique timestamp** which is **ASCII sortable**"
> — [Retrieving messages | Slack API](https://api.slack.com/messaging/retrieving)

### Key Insights

1. **One channel = One server** (via consistent hashing)
2. **Server assigns canonical timestamp** (not client-generated)
3. **Timestamp is the message ID** (looks like UNIX timestamp, but it's actually the message identifier)
4. **Perfect ordering within channel** (single server = single authority)

**Slack does NOT use:**
- ❌ Snowflake IDs with worker IDs (not needed when routing guarantees single writer)
- ❌ Kafka for message ordering (Kafka only used for job queue, not real-time messages)
- ❌ Logical clocks (HLC, Vector clocks) for message ordering

**Slack DOES use:**
- ✅ Consistent hashing to map channels → servers
- ✅ Simple timestamp assignment by designated server
- ✅ ASCII-sortable timestamp strings (e.g., "1234567890.123456")

---

## Decision: Channel Partitioning with Consistent Hashing (The Slack Way)

**We adopt Slack's architecture: Channel-based server partitioning with consistent hashing.**

### Why This Choice?

1. **Real Slack architecture**: Production-proven at billions of messages/day
2. **Perfect ordering guarantee**: 100% correct (not 99.9%), because single server assigns all timestamps
3. **Simple implementation**: No Snowflake IDs, no Kafka, just consistent hashing + timestamps
4. **Learning value**: Understanding real production architecture and its trade-offs

### The Guarantee We Provide

**"All messages in a channel appear in perfect chronological order, guaranteed."**

This is possible because:
- One channel always routes to the same Channel Server
- That server assigns timestamps sequentially
- No clock skew issues (single authority per channel)
- No coordination overhead (no distributed consensus)

---

## Implementation

### Step 1: Channel Routing Service

```java
@Service
public class ChannelRoutingService {
    private final int serverId;
    private final int totalServers;

    public ChannelRoutingService(
        @Value("${server.id}") int serverId,
        @Value("${cluster.total-servers}") int totalServers
    ) {
        this.serverId = serverId;
        this.totalServers = totalServers;
    }

    /**
     * Determine which server should handle this channel
     * Uses consistent hashing for stable partitioning
     */
    public int getServerForChannel(Long channelId) {
        // Simple modulo hash (production Slack uses consistent hashing ring)
        return Math.abs(channelId.hashCode()) % totalServers;
    }

    /**
     * Check if THIS server should handle this channel
     */
    public boolean isResponsibleFor(Long channelId) {
        return getServerForChannel(channelId) == serverId;
    }
}
```

### Step 2: Message Controller with Routing Check

```java
@Controller
public class MessageController {
    @Autowired
    private ChannelRoutingService routingService;
    @Autowired
    private MessageTimestampGenerator timestampGenerator;

    @MessageMapping("/message")
    public void handleMessage(MessageRequest request, Principal principal) {
        Long channelId = request.getChannelId();

        // Check if this server should handle this channel
        if (!routingService.isResponsibleFor(channelId)) {
            int correctServer = routingService.getServerForChannel(channelId);
            throw new WrongServerException(
                "Channel " + channelId + " should be handled by server " + correctServer
            );
        }

        // This server is responsible - assign timestamp (canonical source of truth)
        String timestamp = timestampGenerator.generateTimestampId();

        Message message = messageService.save(request, timestamp);
        broadcastMessage(message);
    }
}
```

### Step 3: Fix MessageTimestampGenerator

```java
@Service
public class MessageTimestampGenerator {
    private long lastMilliseconds = -1L;
    private int sequence = 0;

    public synchronized String generateTimestampId() {
        long currentMillis = System.currentTimeMillis(); // ✅ Use wall clock

        if (currentMillis > lastMilliseconds) {
            // Time advanced - reset sequence
            lastMilliseconds = currentMillis;
            sequence = 0;
        } else {
            // Same millisecond - increment sequence
            sequence++;
        }

        // Format: "1234567890.001" (ASCII sortable, just like Slack)
        return String.format("%d.%03d", lastMilliseconds, sequence);
    }
}
```

### Step 4: Nginx Load Balancer Configuration

```nginx
# Hash based on channel_id to ensure consistent routing
upstream backend {
    hash $arg_channel_id consistent;  # Consistent hashing on channel_id

    server backend1:9000;
    server backend2:9001;
    server backend3:9002;
}

server {
    listen 8888;

    location /ws {
        proxy_pass http://backend;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
```

### Step 5: Client Connection

```typescript
// Client includes channelId in connection URL
const channelId = getCurrentChannelId();
const socket = new SockJS(
  `http://localhost:8888/ws?channel_id=${channelId}`
);

// All messages for this channel route to same server
// No client-side reordering needed - server guarantees order
messages.sort((a, b) => a.timestamp.localeCompare(b.timestamp)); // Simple!
```

---

## Migration Plan

### Phase 1: Implement Channel Routing (Week 1)

**Tasks:**
- [ ] Create `ChannelRoutingService`
- [ ] Add configuration: `server.id` and `cluster.total-servers`
- [ ] Update Nginx config with `hash $arg_channel_id consistent`
- [ ] Add routing check in `MessageController`

**Files to create/modify:**
```
backend/src/main/java/com/slack/service/
  └─ ChannelRoutingService.java (NEW)

backend/src/main/resources/application.yml
  server:
    id: ${SERVER_ID:0}
  cluster:
    total-servers: ${TOTAL_SERVERS:3}

nginx/nginx.conf
  upstream backend {
    hash $arg_channel_id consistent;
    ...
  }
```

### Phase 2: Fix MessageTimestampGenerator (Week 2)

**Tasks:**
- [ ] Replace `System.nanoTime()` with `System.currentTimeMillis()`
- [ ] Remove unnecessary complexity (no worker ID needed)
- [ ] Format timestamp as ASCII-sortable string (Slack style)
- [ ] Add unit tests

**Migration:**
```java
// OLD (BROKEN)
private long getMicroseconds() {
    return System.nanoTime() / 1000;
}

// NEW (FIXED)
private long getMilliseconds() {
    return System.currentTimeMillis();
}
```

### Phase 3: Client Updates (Week 3)

**Tasks:**
- [ ] Update WebSocket connection to include `channel_id` parameter
- [ ] Remove client-side 2-second buffer (not needed!)
- [ ] Simple timestamp sorting (already ASCII sortable)

**Client changes:**
```typescript
// Before: Complex buffering + deduplication
// After: Simple sorting (server guarantees order)
messages.sort((a, b) => a.timestamp.localeCompare(b.timestamp));
```

### Phase 4: Testing & Validation (Week 4)

**Load tests:**
- [ ] Verify same channel always routes to same server
- [ ] Confirm 100% message ordering (no inversions)
- [ ] Test server failover and rebalancing

---

## Consequences

### Positive

✅ **Perfect ordering**: 100% guaranteed (vs 99.9% with Snowflake)
✅ **Simple implementation**: No Snowflake, no Kafka, no Worker IDs
✅ **Real Slack architecture**: Production-proven at scale
✅ **Fast**: No coordination overhead, no distributed consensus
✅ **ASCII-sortable timestamps**: Simple client-side handling

### Negative

❌ **Hot channel problem**: Popular channels concentrate on one server
❌ **Server failure impact**: Channels on failed server become unavailable
❌ **Rebalancing complexity**: Adding/removing servers requires channel migration

### Neutral (Trade-offs)

⚖️ **Horizontal scaling**: Can add servers, but hot channels still bottleneck
⚖️ **Availability vs Ordering**: Perfect ordering, but lower availability per channel
⚖️ **Slack's trade-off**: They accept these downsides for perfect ordering

---

## Addressing Trade-offs

### Hot Channel Problem

**Slack's solution** (from [Real-time Messaging blog](https://slack.engineering/real-time-messaging/)):
- Channel Servers handle ~16 million channels per host
- Hot channels get dedicated resources (vertical scaling)
- Monitoring identifies hot channels proactively

**Our approach:**
- Start simple: modulo hash (good enough for learning)
- Monitor: track messages/sec per channel
- Upgrade later: consistent hashing ring (handles rebalancing better)

### Server Failure Handling

**Slack's approach:**
- Multiple Gateway Servers for redundancy
- Channel Server failure → clients reconnect to backup
- Channel history in database (recover on failover)

**Our approach:**
- Accept temporary channel unavailability (learning project)
- Document failover procedure
- Future: implement replica Channel Servers

---

## Alternatives Considered

Each approach has valid use cases. We chose Channel Partitioning for this project to match production Slack's architecture, but other approaches may be better depending on requirements.

### Alternative 1: Snowflake ID with Worker IDs

**Description:**
Each server generates globally unique IDs using timestamp + worker ID + sequence number.

**Example:**
```java
// 64-bit ID: [41-bit timestamp][10-bit worker ID][12-bit sequence]
long id = ((timestamp - epoch) << 22) | (workerId << 12) | sequence;
```

**Pros:**
- ✅ **Horizontal scaling**: Add servers freely without coordination
- ✅ **No routing needed**: Any server can handle any channel
- ✅ **Simple failover**: Server down? Others take over immediately
- ✅ **High availability**: No single point of failure per channel

**Cons:**
- ❌ **~99.9% ordering**: Clock skew + network latency cause occasional inversions
- ❌ **Configuration overhead**: Must assign unique worker IDs
- ❌ **Client complexity**: Need deduplication and time-based sorting

**Why Slack doesn't use this:**
- Consistent hashing already guarantees single writer per channel
- Worker IDs would be redundant

**When to use this instead:**
- Horizontal scaling > perfect ordering
- Can't partition by channel (e.g., global event stream)
- Need maximum availability per channel
- Discord, Twitter use this approach

### Alternative 2: Kafka Partition-Based Ordering

**Description:**
Messages flow through Kafka partitions (keyed by channel ID) before delivery.

**Example:**
```
Client → Server → DB → Kafka (partition by channelId) → Consumer → WebSocket
```

**Pros:**
- ✅ **100% ordering**: Kafka guarantees order within partition
- ✅ **Durability**: Message replay capability
- ✅ **Event sourcing**: Natural fit for event-driven architecture
- ✅ **Audit trail**: All messages in append-only log

**Cons:**
- ❌ **Latency**: +10-50ms (Kafka write + consumer poll)
- ❌ **Complexity**: Kafka cluster management, monitoring
- ❌ **Over-engineering**: Too heavy for simple real-time chat

**Why Slack doesn't use this for messages:**
- Slack uses Kafka for **job queue**, NOT real-time messages ([source](https://slack.engineering/scaling-slacks-job-queue/))
- Real-time chat requires <100ms latency
- Channel Server already provides ordering

**When to use this instead:**
- Building event-sourced system
- Message replay is requirement
- Already have Kafka infrastructure
- Latency not critical (<100ms)

### Alternative 3: Centralized Sequencer (Redis INCR)

**Description:**
Use Redis INCR to generate sequential IDs per channel.

**Example:**
```java
long sequence = redis.incr("channel:" + channelId + ":seq");
```

**Pros:**
- ✅ **Perfect ordering**: Sequential IDs guarantee order
- ✅ **Simple**: No complex hashing or routing
- ✅ **Gap detection**: Can detect missing messages

**Cons:**
- ❌ **Bottleneck**: Every message waits for Redis
- ❌ **Single point of failure**: Redis down = no new messages
- ❌ **Doesn't scale**: All servers coordinate on single counter

**Why Slack doesn't use this:**
- Becomes bottleneck at scale (millions of messages/sec)
- Consistent hashing eliminates need for coordination

**When to use this instead:**
- Small scale (<1000 msg/sec)
- Redis already in use
- Simple architecture preferred

### Alternative 4: Consensus Algorithms (Raft/Paxos)

**Description:**
Servers reach consensus on message order via voting protocol.

**Pros:**
- ✅ **Byzantine fault tolerance**: Works even with malicious servers
- ✅ **No coordinator**: Distributed consensus

**Cons:**
- ❌ **50-200ms latency**: Too slow for real-time chat
- ❌ **Extreme complexity**: Multi-Paxos, leader election, log replication
- ❌ **Overkill**: Chat doesn't need Byzantine fault tolerance

**Why Slack doesn't use this:**
- Way too slow for real-time messaging
- Complexity not justified

**When to use this instead:**
- Financial transactions (need Byzantine fault tolerance)
- Distributed databases (CockroachDB, etcd)
- NOT real-time chat

---

## Success Metrics

### Performance Targets

| Metric | Current (v0.4) | Target (v0.5) | Measurement |
|--------|----------------|---------------|-------------|
| Message ordering accuracy | ~95% (broken) | 100% (guaranteed) | Load test with concurrent sends |
| Timestamp assignment latency | 1-5ms (System.nanoTime) | <0.1ms (local) | Prometheus histogram |
| Ordering dependency | None (broken) | Nginx hash routing | Config validation |

### Validation Tests

**Test 1: Concurrent Message Ordering**
```bash
# Send 100 messages concurrently to same channel from 10 clients
# Verify: timestamp order = send order (100% accuracy)
./gradlew test --tests MessageOrderingIntegrationTest
```

**Test 2: Channel Routing Consistency**
```bash
# Send 1000 messages to Channel #general
# Verify: All handled by same server
grep "Channel #general" server-*.log | cut -d: -f1 | sort -u | wc -l
# Expected: 1 (only one server)
```

**Test 3: Timestamp Uniqueness**
```sql
-- No duplicate timestamps in same channel
SELECT channel_id, timestamp_id, COUNT(*)
FROM messages
GROUP BY channel_id, timestamp_id
HAVING COUNT(*) > 1;
-- Expected: 0 rows
```

---

## Monitoring & Alerts

### Metrics to Track

```java
@Component
public class ChannelRoutingMetrics {
    private final MeterRegistry registry;

    public void recordChannelTraffic(Long channelId, int serverId) {
        registry.counter("channel.messages",
            "channel_id", channelId.toString(),
            "server_id", String.valueOf(serverId)
        ).increment();
    }

    public void recordWrongServerError(Long channelId, int expectedServer, int actualServer) {
        registry.counter("channel.routing.errors",
            "channel_id", channelId.toString(),
            "expected_server", String.valueOf(expectedServer),
            "actual_server", String.valueOf(actualServer)
        ).increment();
    }

    public void recordChannelLoadImbalance(Map<Integer, Integer> serverLoads) {
        int maxLoad = Collections.max(serverLoads.values());
        int minLoad = Collections.min(serverLoads.values());
        double imbalance = (double) maxLoad / (minLoad + 1);

        registry.gauge("channel.load_imbalance", imbalance);
    }
}
```

### Alert Rules

```yaml
alerts:
  - name: HotChannelDetected
    condition: rate(channel.messages{channel_id="X"}[5m]) > 1000
    action: Alert ops team (consider dedicated server)

  - name: LoadImbalance
    condition: channel.load_imbalance > 3.0
    action: Rebalance channels across servers

  - name: WrongServerRouting
    condition: rate(channel.routing.errors[5m]) > 0
    action: Critical - Nginx config issue
```

---

## Educational Value

### What We Learn

1. **Real production architecture**: Exactly how Slack handles billions of messages
2. **Consistent hashing**: Load distribution while maintaining routing stability
3. **Trade-off analysis**: Perfect ordering vs availability, simplicity vs scalability
4. **Why NOT to over-engineer**: Snowflake/Kafka unnecessary when architecture solves the problem

### Comparison with Other Chat Systems

| System | Message Ordering Approach | Rationale |
|--------|---------------------------|-----------|
| **Slack** | Channel Server + Consistent Hashing | Perfect ordering, accepts hot channel risk |
| **Discord** | Snowflake ID (~99.9% ordering) | Horizontal scaling > perfect ordering |
| **WhatsApp** | End-to-end encryption + vector clocks | Causality preservation for E2EE |
| **Telegram** | Server-assigned sequence per chat | Similar to Slack (single writer) |

**Our choice (Slack)** prioritizes **learning real production architecture** and **perfect ordering guarantees**.

---

## References

### Slack Engineering Resources

- [Real-time Messaging | Engineering at Slack](https://slack.engineering/real-time-messaging/) - Primary architecture reference
- [Real-Time Messaging Architecture at Slack (InfoQ)](https://www.infoq.com/news/2023/04/real-time-messaging-slack/) - Consistent hashing details
- [Slack Architecture - System Design](https://systemdesign.one/slack-architecture/) - Channel mapping explanation
- [How Slack Supports Billions of Daily Messages (ByteByteGo)](https://blog.bytebytego.com/p/how-slack-supports-billions-of-daily) - Message arbitration details
- [Retrieving messages | Slack API](https://api.slack.com/messaging/retrieving) - Timestamp guarantees
- [Scaling Slack's Job Queue](https://slack.engineering/scaling-slacks-job-queue/) - Kafka usage (job queue, not messages)

### Distributed Systems Concepts

- [Consistent Hashing Explained](https://www.toptal.com/big-data/consistent-hashing) - Algorithm details
- Martin Kleppmann - *Designing Data-Intensive Applications*, Chapter 6 (Partitioning)

---

## Acceptance Criteria

Before merging this implementation:

- [ ] `ChannelRoutingService` implemented and tested
- [ ] Nginx configured with `hash $arg_channel_id consistent`
- [ ] MessageTimestampGenerator uses `System.currentTimeMillis()`
- [ ] Client includes `channel_id` in WebSocket connection
- [ ] Load test confirms 100% message ordering (0 inversions)
- [ ] Monitoring dashboard shows channel → server mapping
- [ ] Documentation updated with architecture diagrams
- [ ] Runbook for handling hot channels

---

**Questions? See [Slack Engineering Blog](https://slack.engineering/real-time-messaging/) for production implementation details.**
