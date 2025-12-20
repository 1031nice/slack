# ADR-0002: Event-Based Architecture for Distributed Messaging

**Status**: Proposed
**Date**: 2025-12-20
**Supersedes**: Current sequence-based ordering (v0.3)

---

## Context

### Current System (v0.3): Sequence-Based Ordering

Our current implementation uses Redis INCR to generate sequential message IDs per channel:

```
Channel 1: 1, 2, 3, 4, 5...
Channel 2: 1, 2, 3, 4, 5...
```

**How it works**:

- Server receives message → `Redis INCR channel:{channelId}:sequence` → persist to PostgreSQL
- Client tracks `lastSequenceNumber` per channel
- On reconnect: Client sends last sequence → Server queries PostgreSQL for messages after that sequence
- Guarantees: Total ordering within channel, gap detection (missing 5? Request 4-6)

**Current limitations**:

- Works well for current scale (low message volume, single Redis instance)
- Simple client logic (track single number per channel)
- Easy gap detection (sequential numbers)

### The Problem Real Slack Faces

As Slack scaled to billions of messages across global infrastructure, sequence-based ordering created **fundamental
bottlenecks**:

#### 1. **Single Point of Coordination**

- **Bottleneck**: Every message write requires Redis INCR call
- **Failure Mode**: Redis down = no new messages (even if PostgreSQL is healthy)
- **Global Scale**: Multiple data centers need to coordinate on sequence generation
- **Throughput Limit**: Centralized counter becomes the bottleneck

#### 2. **Network Ordering Unreliability**

At global scale, network ordering is unreliable:

```
Server A sends: Message seq=100 at T1
Server B sends: Message seq=101 at T2
Client receives: 101 first (faster route), then 100 (slower route)
```

Sequential IDs create false expectations of order that the network cannot guarantee.

#### 3. **Horizontal Scaling Challenges**

- Adding more servers doesn't help if they all coordinate on single Redis instance
- Database sharding becomes complex (which shard owns sequence generation?)
- Cross-region replication requires sequence conflict resolution

### Real Slack's Solution: Event-Based Architecture

Slack shifted to an event-based model:

**Core Principles**:

1. **Distributed ID Generation**: Snowflake-like IDs (no coordination needed)
2. **No Ordering Guarantee**: Events arrive in any order
3. **Client-Side Ordering**: Sort by timestamp after receiving
4. **At-Least-Once Delivery**: Idempotency instead of exactly-once semantics
5. **Client-Side Deduplication**: Track `Set<event_id>` instead of single sequence number

**Example Flow**:

```
Message arrives with:
{
  event_id: "Ev1234ABC" (UUID or Snowflake ID)
  timestamp: "2024-01-15T10:30:45.123Z"
  channel_id: "C123"
  content: "Hello"
}

Client:
1. Check: Have I seen event_id "Ev1234ABC"? No → Process it
2. Add to seen set: seenEvents.add("Ev1234ABC")
3. Sort all messages by timestamp for display
4. Handle gaps: No concept of "missing event 5" - events arrive when they arrive
```

---

## Decision

**We will migrate to event-based architecture in v0.5**, after completing v0.4 (Read Status & Notifications) with the
current sequence-based system.

### Why v0.5 (Not v0.4)?

**Rationale**:

1. **v0.4 focus**: Implement read status, unread counts, and mentions with current stable system
2. **Learn first**: Understand read receipt patterns and state management before architectural shift
3. **Incremental risk**: Don't change two major systems (read status + messaging architecture) simultaneously
4. **v0.5 clean slate**: Thread support (v0.5) is a natural point to rearchitect, as threads benefit from event-based
   design

---

## Implementation Plan

### Phase 1: Snowflake ID Generation (Week 1-2)

**Introduce event IDs without removing sequence numbers** (dual system):

```java

@Service
public class SnowflakeIdGenerator {
    // Twitter Snowflake format: 64 bits
    // 1 bit: unused
    // 41 bits: timestamp (milliseconds since epoch)
    // 10 bits: server ID (supports 1024 servers)
    // 12 bits: sequence per millisecond (4096 IDs/ms)

    public String generateEventId() {
        long timestamp = System.currentTimeMillis() - CUSTOM_EPOCH;
        long serverId = SERVER_ID;
        long sequence = getSequenceForCurrentMs();

        long id = (timestamp << 22) | (serverId << 12) | sequence;
        return "Ev" + Long.toHexString(id).toUpperCase();
    }
}
```

**Migration strategy**:

- All new messages get both `sequence_number` (old) and `event_id` (new)
- PostgreSQL: Add `event_id VARCHAR(20) UNIQUE NOT NULL` column
- WebSocket messages include both fields
- Client logic unchanged (still uses sequence numbers)

**Goal**: Ensure event_id generation is stable before relying on it.

---

### Phase 2: Client-Side Sorting with Time Buffer (Week 3-4)

**Add timestamp-based sorting with 2-second buffer**:

```typescript
// Frontend: lib/websocket.ts
class MessageBuffer {
  private buffer: Map<string, WebSocketMessage[]> = new Map();
  private seenEvents: Set<string> = new Set();

  onMessageReceived(msg: WebSocketMessage) {
    // Deduplication
    if (this.seenEvents.has(msg.event_id)) {
      console.log('Duplicate event ignored:', msg.event_id);
      return;
    }
    this.seenEvents.add(msg.event_id);

    // Add to buffer
    const channelBuffer = this.buffer.get(msg.channelId) || [];
    channelBuffer.push(msg);
    this.buffer.set(msg.channelId, channelBuffer);

    // Flush buffer after 2 seconds
    setTimeout(() => this.flushBuffer(msg.channelId), 2000);
  }

  flushBuffer(channelId: string) {
    const messages = this.buffer.get(channelId) || [];

    // Sort by timestamp, then event_id (for deterministic tie-breaking)
    messages.sort((a, b) => {
      if (a.timestamp !== b.timestamp) {
        return a.timestamp - b.timestamp;
      }
      return a.event_id.localeCompare(b.event_id);
    });

    // Display in UI
    messages.forEach(msg => this.displayMessage(msg));
    this.buffer.delete(channelId);
  }
}
```

**Why 2-second buffer?**

- Allows late-arriving messages to be sorted correctly
- Trade-off: 2s display latency vs correct ordering
- Real Slack uses ~1-3s buffer (observed behavior)

**Dual mode**: Client supports both sequence-based (reconnect) and event-based (real-time).

---

### Phase 3: Remove Sequence Numbers (Week 5-6)

**Replace sequence-based reconnection with time-based query**:

```java
// Backend: MessageService.java
public List<MessageResponse> getMessagesSince(Long channelId,Instant timestamp){
        return messageRepository.findByChannelIdAndCreatedAtAfter(channelId,timestamp)
        .stream()
        .map(this::toResponse)
        .toList();
        }
```

**Frontend reconnection**:

```typescript
onReconnect() {
  const lastSeenTimestamp = this.getLastMessageTimestamp(channelId);

  // Request messages since last seen (with 5s overlap for safety)
  const requestTimestamp = lastSeenTimestamp - 5000;

  this.client.publish({
    destination: '/app/message.resync',
    body: JSON.stringify({
      channelId,
      since: requestTimestamp
    })
  });
}
```

**Challenge: Gap Detection**

- Old: "I have messages 1-100, next message is 102 → request 101"
- New: No sequential IDs → how to detect gaps?

**Solution: Heartbeat-based detection**:

```typescript
class GapDetector {
  private lastHeartbeat: Map<string, number> = new Map();

  startHeartbeat(channelId: string) {
    setInterval(() => {
      const now = Date.now();
      const last = this.lastHeartbeat.get(channelId) || now;

      // If no message in 10 seconds, request resync
      if (now - last > 10000) {
        this.requestResync(channelId);
      }
    }, 5000);
  }

  onMessageReceived(msg: WebSocketMessage) {
    this.lastHeartbeat.set(msg.channelId, Date.now());
  }
}
```

**Remove**:

- `MessageSequenceService` (Redis INCR)
- `sequence_number` column from Message entity
- All sequence-based client logic

---

## Consequences

### Positive

✅ **Eliminates single point of failure**: No dependency on Redis INCR for message writes
✅ **Horizontal scalability**: Adding servers doesn't create coordination bottleneck
✅ **Global distribution ready**: Each data center generates IDs independently
✅ **Real Slack architecture**: Learn production patterns used at scale
✅ **Idempotency by design**: Duplicate deliveries are expected and handled

### Negative

❌ **Client complexity**: Tracking `Set<event_id>` vs single sequence number (memory overhead)
❌ **Gap detection harder**: No longer "message 5 missing", need heartbeat-based detection
❌ **Display latency**: 2-second buffer adds perceived lag
❌ **Migration risk**: Dual system during transition adds code complexity
❌ **Learning curve**: Team must understand eventual consistency, idempotency patterns

### Neutral (Trade-offs)

⚖️ **Ordering**: Lose total order guarantee, gain network resilience
⚖️ **Consistency model**: Shift from "guaranteed sequential" to "eventually ordered by time"
⚖️ **Client state**: More memory (Set of UUIDs) but better offline support

---

## Alternatives Considered

### Alternative 1: Keep Sequence-Based with HA Redis

**Approach**: Use Redis Cluster or Sentinel for high availability

**Why Rejected**:

- Doesn't solve coordination bottleneck (still single logical counter)
- Multi-region still requires coordination
- Adds infrastructure complexity without solving root problem
- Not how real Slack evolved (they moved away from sequences entirely)

### Alternative 2: Hybrid (Sequences + Snowflake IDs)

**Approach**: Keep sequences for ordering, add event IDs for deduplication

**Why Rejected**:

- Maintains the bottleneck we're trying to eliminate
- Adds complexity without gaining scalability benefits
- No clear migration path to fully distributed system

### Alternative 3: Logical Clocks (Vector Clocks, Lamport Timestamps)

**Approach**: Use logical time instead of physical time

**Why Rejected**:

- Overkill for message ordering (not doing CRDTs or distributed consensus)
- Real Slack uses physical timestamps, not logical clocks
- Harder to reason about and debug

---

## Success Metrics

**Before (v0.4 sequence-based)**:

- Sequence generation latency: ~1ms (Redis INCR)
- Redis dependency: 100% (no sequences = no messages)
- Gap detection: O(1) (check consecutive numbers)

**After (v0.5 event-based)**:

- ID generation latency: ~0.01ms (local, no network call)
- Redis dependency: 0% for ID generation (only for Pub/Sub)
- Gap detection: Heartbeat-based (~5s intervals)
- Deduplication overhead: O(1) Set lookup per event

**Measure**:

- Average message display latency (target: <3s at P95)
- Memory usage per channel (expected: +10KB for event ID set per active channel)
- Duplicate message rate (target: <0.1% after deduplication)
- Redis failure resilience (message writes succeed even if Pub/Sub fails)

---

## Related Decisions

- **[ADR-0001: Redis Pub/Sub vs Kafka](./0001-redis-vs-kafka-for-multi-server-broadcast.md)**: Established Redis for
  ephemeral messaging
- **v0.4 Read Status**: Will use sequence numbers (stable foundation before migration)
- **v0.5 Thread Support**: Will use event IDs from day one (clean implementation)

---

## References

- [Real-time Messaging | Slack Engineering](https://slack.engineering/real-time-messaging/)
- [Twitter Snowflake ID Generation](https://github.com/twitter-archive/snowflake)
- Martin Kleppmann - *Designing Data-Intensive Applications*, Chapter 8: The Trouble with Distributed Systems
- [How Slack Works - RTM API (deprecated)](https://api.slack.com/rtm) - Shows Slack's evolution away from sequence-based
- [Slack Events API](https://api.slack.com/apis/connections/events-api) - Current event-based approach with `event_id`

---

## Notes

**Why this matters for a learning project**:

This decision reflects real problems at scale. While our project won't reach billions of messages, implementing
event-based architecture teaches:

1. **Distributed systems thinking**: Coordination is expensive, eliminate it when possible
2. **Trade-off analysis**: Simplicity (sequences) vs scalability (events)
3. **Production patterns**: How real companies evolve architectures as they scale
4. **Failure modes**: What breaks when Redis goes down? How to make systems resilient?

The goal is not to prematurely optimize, but to **experience the problems that real Slack faced** and learn their
solutions firsthand.
