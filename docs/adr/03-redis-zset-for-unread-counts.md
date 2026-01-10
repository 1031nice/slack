# ADR-03: Redis Sorted Set for Unread Tracking

## Metadata

- **Status**: Accepted ✅
- **Date**: 2026-01-10
- **Context**: v0.4 - Unread Count Data Modeling
- **Deciders**: Engineering Team
- **Related Deep Dive**: [Deep Dive 05: High-Throughput Read Status Management](../deepdives/05-read-status-updates.md)

---

## TL;DR (Executive Summary)

**Decision**: Use **Redis Sorted Set (ZSET)** to store unread message IDs per user/channel.

**Key Trade-off**: Accept slightly higher memory usage (~24 bytes per entry) in exchange for automatic deduplication, time-based sorting, and $O(1)$ count performance.

**Rationale**: Traditional counters are prone to race conditions and duplicate increments. ZSET provides a robust way to track *which* specific messages are unread, enabling features like "mark as read until timestamp."

---

## Context

### The Problem
We need a data structure to track unread messages for millions of users across thousands of channels. 

### Key Requirements
1.  **Deduplication**: Retries or network issues must not cause double-counting.
2.  **Performance**: Reading the unread count must be $O(1)$ to keep the channel list responsive.
3.  **Partial Reads**: Support for "Mark as read up to this point" or "Read only these specific messages."
4.  **Auto-Cleanup**: Ability to remove old unread data based on time.

---

## Decision

### What We Chose
**Redis Sorted Set (ZSET)** for each `userId:channelId` pair.

*   **Key**: `unread:{userId}:{channelId}`
*   **Member**: `messageId` (String)
*   **Score**: `timestamp` (Milliseconds)

### Why This Choice (Trade-off Analysis)

| Criteria | **Redis ZSET** (Selected) | Simple Counter (INCR) | List / Set |
| :--- | :--- | :--- | :--- |
| **Deduplication** | ✅ Automatic (Member-based) | ❌ None (Risk of double-count) | 🟡 Set only |
| **Ordering** | ✅ Time-sorted (Score) | ❌ None | ❌ None |
| **Count Perf** | ✅ $O(1)$ (`ZCARD`) | ✅ $O(1)$ | ❌ $O(N)$ for List |
| **Partial Read** | ✅ Supported (`ZREM`) | ❌ Impossible | ❌ Hard |

**Primary Reason**:
ZSET is the only structure that provides **idempotency** (deduplication) and **ordered metadata** in a single high-performance primitive. While a simple counter is faster, it cannot survive the complexities of distributed event delivery where duplicate messages are common.

---

## Consequences

### Positive Impacts
- **Reliability**: Safe against duplicate delivery of "Message Sent" events.
- **Feature Rich**: Enables "Jump to oldest unread" and "Clear unread before [date]" with ease.
- **Consistency**: Naturally aligns with the `last_read_timestamp` in the database.

### Negative Impacts & Mitigations
- **Memory Consumption**: Stores every unread ID. At Slack-scale, this can reach Terabytes.
    - **Mitigation (TTL)**: Unread keys expire after 30 days of inactivity. Older counts are fetched from DB on demand.
    - **Mitigation (Max Limit)**: We cap the ZSET size at **10,000 items**.
        - *Rationale*: If a user has >10,000 unread messages, the "Unread Divider" (red line) will be inaccurate (clamped to the 10,000th message). We accept this trade-off because users rarely scroll back 10,000 messages, and it prevents unbounded memory growth. The UI will simply show "999+".

- **Write Amplification**: 1 message sent to a 1,000-user channel = 1,000 `ZADD` ops.
    - **Mitigation**: Use **Redis Pipelining** to batch writes.

---

## Implementation Details

```java
// Increment (Add unread)
redisTemplate.opsForZSet().add(key, messageId, timestamp);

// Get Count
Long count = redisTemplate.opsForZSet().zCard(key);

// Partial Clear (Mark as read up to timestamp)
redisTemplate.opsForZSet().removeRangeByScore(key, 0, targetTimestamp);
```

---

## References

- **[Deep Dive 05: High-Throughput Read Status Management](../deepdives/05-read-status-updates.md)**
- **[Redis Documentation: Sorted Sets](https://redis.io/docs/data-types/sorted-sets/)**
- **Discord Engineering**: "How Discord Stores Billions of Messages" (Mentions similar state tracking).