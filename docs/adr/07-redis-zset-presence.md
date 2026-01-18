# ADR-0007: Redis ZSET for Distributed Presence Tracking

## Metadata

- **Status**: Accepted ✅
- **Date**: 2026-01-16
- **Context**: v0.7 - Presence System Implementation
- **Deciders**: Engineering Team
- **Related Deep Dive**: [Deep Dive 07: Distributed Presence System](../deepdives/07-distributed-presence.md)

---

## TL;DR (Executive Summary)

**Decision**: Use **Redis Sorted Sets (ZSET)** to manage online/offline status for millions of users.

**Key Trade-off**: Slightly higher memory usage than Bitmaps in exchange for **efficient range queries (staleness detection)** and **batching capabilities**.

**Rationale**: Handling millions of heartbeats requires a write-heavy optimized store. RDBMS cannot handle the IOPS. Redis ZSET allows us to store `(UserId, Timestamp)` tuples, enabling both fast updates (`ZADD`) and efficient cleanup of offline users (`ZREMRANGEBYSCORE`) without full scans.

---

## Context

### The Problem

We need to track the "Online" status of users in real-time.
1.  **Heartbeat Storm**: 1M concurrent users sending keep-alives every 30s results in ~33k writes/sec.
2.  **Stale Data**: If a server crashes, connected users must eventually be marked offline. We need an efficient way to find and remove users who stopped sending heartbeats.
3.  **Fan-out**: Status changes need to be broadcast to friends/channels.

### Constraints

- **Write Throughput**: Must handle >30k ops/sec.
- **Latency**: Queries ("Is User A online?") must be sub-millisecond.
- **Cleanup**: Identifying "who timed out" must be efficient (no O(N) scans).

---

## Decision

### What We Chose

**Redis ZSET (Sorted Set)**.

*   **Key**: `presence:online_users` (or sharded keys)
*   **Member**: `userId`
*   **Score**: `timestamp` (Unix Epoch)

### Why This Choice

| Strategy | Write Speed | Cleanup Efficiency | Memory | Verdict |
| :--- | :--- | :--- | :--- | :--- |
| **RDBMS (Update)** | 🔴 Low | 🔴 Slow | ⚪ N/A | Rejected |
| **Redis String (`SETEX`)** | 🟢 High | 🟢 Auto (TTL) | 🔴 High | Rejected (CPU/Mem cost) |
| **Redis ZSET (`ZADD`)** | 🟢 **High** | 🟢 **Fast (`ZREM...`)** | 🟡 Medium | **Selected** |

**Experiment Results (`presence-bench`)**:
*   Individual `SETEX`: ~1,200 ops/sec (Network bound).
*   **Batch `ZADD`**: **~97,000 ops/sec** (Optimized).
*   A single Redis instance can easily handle our target load using ZSET batching.

---

## Consequences

### Positive Impacts

- **Efficiency**: Batching heartbeats into a single `ZADD` command reduces network overhead by 99%.
- **Active Pruning**: `ZREMRANGEBYSCORE key -inf (now - 60s)` allows us to bulk-remove offline users in O(log N) time.
- **Simplicity**: No complex distributed lock or leader election required for cleanup.

### Negative Impacts & Mitigations

- **Risk: Hot Key**
  - Storing all users in one ZSET (`presence:online_users`) creates a hot key.
  - **Mitigation**: Sharding. Use `presence:online_users:{shardId}` where `shardId = userId % 100`.

- **Risk: Persistence**
  - If Redis crashes, all presence data is lost (everyone appears offline).
  - **Mitigation**: Acceptable. Users will re-appear as "Online" within 30 seconds (next heartbeat). Presence is ephemeral.

### Implementation Details

1.  **Client**: Sends heartbeat via WebSocket every 30s.
2.  **Gateway**: Buffers heartbeats for 1s, then sends `ZADD` batch to Redis.
3.  **Sweeper**: Background job runs every 10s to remove stale users.

---

## References

- **[Deep Dive 07: Distributed Presence System](../deepdives/07-distributed-presence.md)**
  - Detailed analysis and benchmark results.
