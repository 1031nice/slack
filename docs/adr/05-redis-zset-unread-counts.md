# ADR-05: Redis ZSET for Unread Counts Strategy

## Metadata

- **Status**: Accepted ✅
- **Date**: 2026-01-10
- **Context**: v0.4 - Distributed Messaging Refinement
- **Deciders**: Engineering Team
- **Related Deep Dive**: [Deep Dive 06: Read Status Updates](../deepdives/06-read-status-updates.md)
- **Related ADR**: [ADR-52: Eventual Consistency for Unread Counts](./52-eventual-consistency-unread.md)

---

## TL;DR (Executive Summary)

**Decision**: Use **Redis Sorted Set (ZSET)** to store unread message IDs per user/channel.

**Key Trade-off**: Higher memory usage (storing IDs) compared to simple counters, in exchange for **idempotency**, **correctness**, and **feature flexibility**.

**Rationale**: Traditional counters are prone to race conditions and duplicate increments (e.g., receiving the same message event twice). ZSET allows us to store unique Message IDs as members and Timestamp as score, enabling robust "Mark as Read" operations (e.g., "Clear all messages before timestamp T") and automatic deduplication.

---

## Context

### The Problem

We need a data structure to track unread messages for millions of users across thousands of channels.
1.  **Write Amplification**: 1 message -> N unread increments.
2.  **Duplicate Events**: Distributed systems often deliver events "At Least Once". A simple `INCR` would double-count.
3.  **Partial Reads**: Support for "Mark as read up to this point" or "Read only these specific messages."

---

## Decision

### What We Chose

**Redis ZSET (Sorted Set)** per User-Channel pair.

*   **Key**: `unread:{userId}:{channelId}`
*   **Score**: `Timestamp` (for ordering)
*   **Member**: `messageId` (String)

### Why This Choice

| Feature | Simple Counter (`INCR`) | List (`LPUSH`) | **Sorted Set (`ZADD`)** |
| :--- | :--- | :--- | :--- |
| **Deduplication** | ❌ No (Double count) | ❌ No (Duplicates) | ✅ **Yes** (Set property) |
| **Ordering** | ❌ None | ✅ Insert Order | ✅ **Time Order** (Score) |
| **Range Read** | ❌ No | ✅ Range | ✅ **Range by Time** |
| **Memory** | 🟢 Very Low | 🟡 Medium | 🔴 High |

**Primary Reason**:
ZSET is the only structure that provides **idempotency** (deduplication) and **ordered metadata** in a single high-performance primitive. While a simple counter is faster, it cannot survive the complexities of distributed event delivery where duplicate messages are common.

---

## Consequences

### Positive Impacts

- **Idempotency**: `ZADD` the same message ID 100 times results in only 1 entry.
- **Reliability**: Safe against duplicate delivery of "Message Sent" events.
- **Flexibility**: Can easily implement "Unread Count" (`ZCARD`) and "Get Oldest Unread" (`ZRANGE`).

### Negative Impacts & Mitigations

- **Risk: Memory Usage**
  - Storing millions of IDs in RAM is expensive.
  - **Mitigation**: Implement a **"Max Unread Limit"** (e.g., 100). If a user has >100 unread messages, we trim the ZSET (`ZREMRANGEBYRANK`) and just show "100+". This caps memory usage per user-channel.

### Implementation Details

```java
// On New Message
redisTemplate.opsForZSet().add(key, messageId, timestamp);
// Trim if too large
redisTemplate.opsForZSet().removeRange(key, 0, -101);

// On Read
redisTemplate.opsForZSet().removeRangeByScore(key, 0, lastReadTimestamp);
```

---

## References

- **[Deep Dive 06: Read Status Updates](../deepdives/06-read-status-updates.md)**
  - Detailed analysis of the "Write Amplification" problem.
- **[Discord Engineering](https://discord.com/blog/how-discord-stores-billions-of-messages)**
  - Mentions similar state tracking challenges.
