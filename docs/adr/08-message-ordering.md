# ADR-0008: Message Ordering in Distributed Chat Systems

**Status**: PROPOSED (Not Implemented)
**Date**: 2025-12-31
**Updated**: 2026-01-05
**Related**: [ADR-0001](./0001-redis-vs-kafka-for-multi-server-broadcast.md), [ADR-0006](./0006-event-based-architecture-for-distributed-messaging.md)

---

## Current State

**This ADR documents a POTENTIAL problem and solution approaches. It has NOT been implemented yet.**

We need to **first measure** if message ordering is actually broken before implementing any solution.

---

## Problem Statement (To Be Validated)

**Hypothesis**: Current timestamp-based ordering may break under concurrent load due to clock skew + network latency.

### The Concrete Example (Theoretical)

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

### Current Architecture

```
Current (May Be Broken?):
Channel #general messages → Nginx (ip_hash) → Random Server (1, 2, or 3)
  → Each server generates timestamp independently
  → Clock skew + network latency MAY cause ordering conflicts

Need to measure this first!
```

---

## Measurement Plan (Do This First)

Before implementing ANY solution, we must **prove the problem exists**:

### Test 1: Ordering Accuracy Under Load
```bash
# Send 1000 messages from 10 concurrent clients
# Measure: How many are out of order?

Expected result:
- If <1% inversions: Current approach is fine, do nothing
- If 1-5% inversions: Minor issue, acceptable for now
- If >5% inversions: Real problem, must fix
```

### Test 2: Clock Skew Impact
```bash
# Artificially skew clocks on different servers (+/- 100ms)
# Measure: Does message ordering break?

If ordering breaks:
  → Clock skew is the root cause
  → Need synchronized time OR single-writer architecture
```

### Test 3: Network Latency Variance
```bash
# Simulate high latency variance (50-500ms)
# Measure: Message arrival order vs generation order

If large divergence:
  → Network latency is masking true send order
  → Client-side timestamps won't help (network is unpredictable)
```

---

## Decision: WAIT UNTIL DATA SHOWS PROBLEM

**Current status**: No implementation, no premature optimization.

**Next steps**:
1. Build load testing suite (measure ordering accuracy)
2. Collect data under realistic conditions
3. If problem exists (>5% inversions), THEN evaluate solutions below

---

## Solution Options (When Needed)

### Option 1: Channel Partitioning (Slack's Approach)

**How it works**: Each channel always routes to same server (consistent hashing)

```java
// Simple implementation (no Consul, no complexity)
@Service
public class ChannelRouter {
    public int getServerForChannel(Long channelId) {
        return Math.abs(channelId.hashCode()) % totalServers;
    }
}
```

**Pros**:
- ✅ **Perfect ordering**: Single server = single timestamp authority
- ✅ **Simple**: No distributed coordination
- ✅ **Production-proven**: Real Slack uses this

**Cons**:
- ❌ **Hot channel problem**: Popular channel overwhelms one server
- ❌ **Server failure**: Channel unavailable if server dies
- ❌ **Rebalancing**: Adding servers requires channel migration

**When to use**:
- Perfect ordering is critical
- Can handle hot channels (vertical scaling)
- Willing to accept single-server availability per channel

### Option 2: Snowflake IDs (Discord's Approach)

**How it works**: Each server generates unique IDs with worker ID + timestamp

```java
// 64-bit ID: [41-bit timestamp][10-bit worker ID][12-bit sequence]
long id = ((timestamp - epoch) << 22) | (workerId << 12) | sequence;
```

**Pros**:
- ✅ **Horizontal scaling**: Add servers freely
- ✅ **High availability**: No single point of failure
- ✅ **Simple failover**: Any server handles any channel

**Cons**:
- ❌ **~99.9% ordering**: Clock skew causes occasional inversions
- ❌ **Client complexity**: Deduplication + time-based sorting
- ❌ **Worker ID management**: Must assign unique IDs per server

**When to use**:
- Horizontal scaling > perfect ordering
- Can tolerate rare inversions (1 in 1000 messages)
- High availability per channel is critical

### Option 3: Kafka Partition-Based Ordering

**How it works**: Messages flow through Kafka partitions (keyed by channel)

```
Client → Server → DB → Kafka (partition by channelId) → Consumer → WebSocket
```

**Pros**:
- ✅ **100% ordering**: Kafka guarantees order within partition
- ✅ **Durability**: Message replay capability
- ✅ **Event sourcing**: Append-only log

**Cons**:
- ❌ **Latency**: +10-50ms (Kafka write + consumer poll)
- ❌ **Complexity**: Kafka cluster management
- ❌ **Over-engineering**: Too heavy for real-time chat

**When to use**:
- Building event-sourced system
- Message replay is requirement
- Latency not critical (<100ms acceptable)

### Option 4: Logical Clocks (Advanced)

**How it works**: Hybrid Logical Clocks (HLC) combine wall clock + logical counter

**Pros**:
- ✅ **Causality tracking**: Preserves happened-before relationships
- ✅ **No coordination**: Decentralized timestamp generation

**Cons**:
- ❌ **Extreme complexity**: Requires deep distributed systems knowledge
- ❌ **Overkill**: Chat doesn't need Byzantine fault tolerance

**When to use**:
- Building distributed database
- Need causal consistency
- NOT for simple chat systems

---

## Comparison Table

| Approach | Ordering | Complexity | Scalability | Latency | Use Case |
|----------|----------|------------|-------------|---------|----------|
| **Channel Partitioning** (Slack) | 100% | Low | Medium (hot channels) | <1ms | Perfect ordering critical |
| **Snowflake IDs** (Discord) | ~99.9% | Medium | High | <1ms | Horizontal scaling > ordering |
| **Kafka Partitions** | 100% | High | High | 10-50ms | Event sourcing, replay |
| **Logical Clocks** | 100% (causal) | Very High | High | <1ms | Distributed databases |

---

## What Real Systems Do

| System | Approach | Why? |
|--------|----------|------|
| **Slack** | Channel Server + Consistent Hashing | Perfect ordering, accepts hot channel risk |
| **Discord** | Snowflake ID (~99.9% ordering) | Horizontal scaling > perfect ordering |
| **WhatsApp** | End-to-end encryption + vector clocks | Causality for E2EE, offline sync |
| **Telegram** | Server-assigned sequence per chat | Similar to Slack (single writer) |

---

## Current Decision: NO DECISION YET

**Status**: Waiting for data

**Action items**:
1. [ ] Implement ordering accuracy measurement tool
2. [ ] Run load tests (1000 concurrent clients, 10k messages)
3. [ ] Analyze results: What % of messages are inverted?
4. [ ] If problem exists, evaluate solutions above
5. [ ] Choose solution based on actual measured impact

**Philosophy**: Don't solve problems you don't have. Measure first, optimize later.

---

## References

**Real-world systems** (for inspiration, not copying):
- [Slack Engineering: Real-time Messaging](https://slack.engineering/real-time-messaging/)
- [Discord: How Discord Stores Billions of Messages](https://discord.com/blog/how-discord-stores-billions-of-messages)
- [Designing Data-Intensive Applications](https://dataintensive.net/) - Chapter 6 (Partitioning)

**Not trying to copy, but learning from their trade-offs.**

---

## Notes

**Why this ADR exists**: To document the problem space and solution options **before** jumping to implementation.

**What we avoid**: Building consistent hashing ring with Consul before proving the problem exists.

**What we learned**: System design is about understanding trade-offs, not copying solutions.
